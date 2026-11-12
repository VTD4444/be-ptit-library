"""
Đổi cỡ đoạn (chunk) cho toàn bộ kho mà tìm kiếm không bị gián đoạn:
  1. tạo cấu hình search_vector_configs mới (chưa bật);
  2. bóc tách lại mọi bản số hiện hành theo cấu hình đó (chạy indexer của worker ngay trên máy này);
  3. bật cấu hình mới;
  4. xóa các đoạn thuộc cấu hình cũ và nhờ worker dựng lại chỉ mục TF-IDF.

Tìm kiếm chỉ dùng đoạn của cấu hình đang bật, còn tìm theo vector không lọc cấu hình, nên phải làm đủ cả 4 bước.
Chạy trên máy có CSDL và có thư mục PDF của Spring (UPLOAD_ROOT):

    .venv\\Scripts\\python -m batch.switch_chunk_config --chunk-size 400 --overlap 80 --dry-run
    .venv\\Scripts\\python -m batch.switch_chunk_config --chunk-size 400 --overlap 80
"""
import argparse
import json
import os
import sys
import urllib.request
from pathlib import Path
from types import SimpleNamespace

WORKER = Path(__file__).resolve().parent.parent


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--chunk-size", type=int, required=True)
    parser.add_argument("--overlap", type=int, required=True)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--worker-url", default="http://127.0.0.1:8000")
    args = parser.parse_args()
    if not 0 <= args.overlap < args.chunk_size:
        raise SystemExit("overlap phải nhỏ hơn chunk-size")

    sys.path.insert(0, str(WORKER))
    from dotenv import load_dotenv

    load_dotenv(WORKER / ".env")
    from app import config, indexer
    from app.db import pool
    from app.tfidf_index import holder
    from batch.common import JOB_TYPE

    # Dựng TF-IDF ở tiến trình này là vô ích (worker thật giữ mô hình riêng): làm một lần qua API ở cuối
    holder.rebuild = lambda *_a, **_k: SimpleNamespace(size=0, vocabulary=())
    upload_root = config.UPLOAD_ROOT or (WORKER.parent / "uploads" / "digital-copies").resolve()
    pool.open(wait=True)

    with pool.connection() as conn:
        copies = conn.execute(
            """SELECT dc.id, dc.document_id, dc.file_path, d.title FROM public.digital_copies dc
               JOIN public.documents d ON d.id = dc.document_id AND d.deleted_at IS NULL
               WHERE dc.is_current = true ORDER BY d.title""").fetchall()
        existing = conn.execute(
            """SELECT id FROM public.search_vector_configs WHERE chunk_size = %s AND overlap = %s
               AND embedding_model = %s ORDER BY id LIMIT 1""",
            (args.chunk_size, args.overlap, config.EMBEDDING_MODEL)).fetchone()
    missing = [title for _, _, path, title in copies if not (upload_root / path).is_file()]
    print(f"{len(copies)} bản số hiện hành; thư mục PDF: {upload_root}")
    if missing:
        raise SystemExit("Thiếu file PDF trên máy này, không bóc tách lại được:\n  " + "\n  ".join(missing))
    if args.dry_run:
        print(f"Sẽ {'dùng lại cấu hình #' + str(existing[0]) if existing else 'tạo cấu hình mới'} "
              f"{args.chunk_size}/{args.overlap} và bóc tách lại: " + "; ".join(t for *_, t in copies))
        return

    with pool.connection() as conn, conn.transaction():
        config_id = existing[0] if existing else conn.execute(
            """INSERT INTO public.search_vector_configs
               (chunk_size, overlap, strategy, embedding_model, vector_dimensions, is_active, created_at, updated_at)
               VALUES (%s, %s, 'fixed', %s, %s, false, now(), now()) RETURNING id""",
            (args.chunk_size, args.overlap, config.EMBEDDING_MODEL, config.EMBEDDING_DIMENSIONS)).fetchone()[0]
    print(f"Cấu hình #{config_id}: {args.chunk_size} từ, chồng lấn {args.overlap}")

    failed = []
    for copy_id, document_id, path, title in copies:
        with pool.connection() as conn, conn.transaction():
            job_id = conn.execute(
                """INSERT INTO public.background_jobs
                   (job_type, target_type, target_id, payload, status, attempts, created_at, updated_at)
                   VALUES (%s, 'digital_copies', %s, %s::jsonb, 'QUEUED', 0, now(), now()) RETURNING id""",
                (JOB_TYPE, copy_id, json.dumps({"digitalCopyId": str(copy_id), "documentId": str(document_id),
                                                "filePath": path, "searchConfigId": config_id,
                                                "source": "switch_chunk_config"}))).fetchone()[0]
        indexer.run_indexing(indexer.IndexRequest(
            background_job_id=job_id, digital_copy_id=copy_id, document_id=document_id,
            file_path=str(upload_root / path), search_config_id=config_id))
        with pool.connection() as conn:
            status = conn.execute("SELECT status FROM public.background_jobs WHERE id = %s", (job_id,)).fetchone()[0]
            chunks = conn.execute("SELECT count(*) FROM public.search_document_chunks "
                                  "WHERE digital_copy_id = %s AND search_config_id = %s", (copy_id, config_id)).fetchone()[0]
        print(f"  {status:<9} {chunks:>5} đoạn  {title}")
        if status != "COMPLETED":
            failed.append(title)

    if failed:
        raise SystemExit("Có bản số bóc tách lỗi, CHƯA bật cấu hình mới (cấu hình cũ vẫn chạy):\n  " + "\n  ".join(failed))

    with pool.connection() as conn, conn.transaction():
        conn.execute("UPDATE public.search_vector_configs SET is_active = (id = %s), updated_at = now()", (config_id,))
        removed = conn.execute("DELETE FROM public.search_document_chunks WHERE search_config_id IS DISTINCT FROM %s",
                               (config_id,)).rowcount
    print(f"Đã bật cấu hình #{config_id}, xóa {removed} đoạn của cấu hình cũ")
    pool.close()
    try:
        urllib.request.urlopen(urllib.request.Request(f"{args.worker_url}/api/index/rebuild", method="POST"), timeout=10)
        print("Đã yêu cầu worker dựng lại chỉ mục TF-IDF.")
    except OSError as ex:
        print(f"Không gọi được worker ({ex}). Bật worker rồi POST {args.worker_url}/api/index/rebuild")


if __name__ == "__main__":
    main()
