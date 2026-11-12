"""
Nạp kết quả bóc tách từ Kaggle vào CSDL, chạy trên máy có CSDL và có sẵn các file PDF gốc.
Ghi giống hệt luồng upload qua Spring: digital_copies + background_jobs (COMPLETED) + search_document_chunks
(kèm embedding), chép PDF vào thư mục lưu trữ của Spring và dữ liệu vị trí vào thư mục data của worker.

    cd python-worker
    .venv\\Scripts\\python -m batch.import_results output "D:\\Books" "D:\\Hieu" --dry-run
    .venv\\Scripts\\python -m batch.import_results output "D:\\Books" "D:\\Hieu"

output là thư mục giải nén từ kết quả Kaggle (có results/ và copies/). Các thư mục PDF phải giống lúc chạy
match_pdfs (cùng tên thư mục). Nạp lại an toàn: bản số đã nạp được bỏ qua.
"""
import argparse
import base64
import gzip
import json
import os
import shutil
import sys
import urllib.request
from datetime import date
from pathlib import Path

import numpy as np

WORKER = Path(__file__).resolve().parent.parent


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("output", type=Path, help="Thư mục kết quả tải từ Kaggle")
    parser.add_argument("pdf_dirs", type=Path, nargs="+", help="Các thư mục PDF gốc (như lúc chạy match_pdfs)")
    parser.add_argument("--upload-dir", type=Path,
                        help="Thư mục lưu PDF của Spring (mặc định UPLOAD_ROOT trong .env, "
                             "hoặc be-ptit-library/uploads/digital-copies)")
    parser.add_argument("--replace", action="store_true",
                        help="Thay bản số hiện hành nếu biểu ghi đã có PDF khác (mặc định bỏ qua)")
    parser.add_argument("--dry-run", action="store_true", help="Chỉ kiểm tra, không ghi CSDL và không chép file")
    parser.add_argument("--max-db-mb", type=int, default=450,
                        help="Dừng trước khi CSDL vượt dung lượng này (gói free Supabase 500 MB); 0 = không giới hạn")
    parser.add_argument("--worker-url", default="http://127.0.0.1:8000",
                        help="Gọi /api/index/rebuild sau khi nạp ('' để bỏ qua)")
    return parser.parse_args()


def clean(value):
    """PostgreSQL không lưu được ký tự NUL trong text; một số PDF có ký tự này trong lớp chữ."""
    return value.replace(chr(0), "") if isinstance(value, str) else value


def to_vector(encoded: str | None) -> str | None:
    if not encoded:
        return None
    values = np.frombuffer(base64.b64decode(encoded), dtype=np.float32)
    return "[" + ",".join(f"{float(x):.6f}" for x in values) + "]"


def main() -> None:
    args = parse_args()
    sys.path.insert(0, str(WORKER))
    from dotenv import load_dotenv

    load_dotenv(WORKER / ".env")
    import psycopg

    from app import config
    from batch.common import JOB_TYPE, locate, sha256

    upload_dir = (args.upload_dir or config.UPLOAD_ROOT or WORKER.parent / "uploads" / "digital-copies").resolve()
    copies_dir = config.COPIES_DIR
    results = sorted((args.output / "results").glob("*.json.gz"))
    if not results:
        raise SystemExit(f"Không có kết quả trong {args.output / 'results'}")
    print(f"PDF sẽ chép vào: {upload_dir}\nDữ liệu vị trí vào: {copies_dir}\n{len(results)} kết quả\n")

    totals: dict[str, int] = {}

    def report(result: str, label: str, note: str = "") -> None:
        totals[result] = totals.get(result, 0) + 1
        print(f"{result:<7} {label} {note}", flush=True)

    # autocommit: mỗi file là một giao dịch riêng (conn.transaction), file lỗi không kéo theo các file đã nạp
    with psycopg.connect(os.environ["DATABASE_URL"], prepare_threshold=None, autocommit=True) as conn:
        active = conn.execute("SELECT id FROM public.search_vector_configs WHERE is_active = true LIMIT 1").fetchone()
        active_config = active[0] if active else None

        for path in results:
            if args.max_db_mb and not args.dry_run:
                db_mb = conn.execute("SELECT pg_database_size(current_database())").fetchone()[0] / 1024 / 1024
                if db_mb > args.max_db_mb:
                    print(f"\nDỪNG: CSDL đã {db_mb:.0f} MB > {args.max_db_mb} MB. Các file còn lại chưa nạp; "
                          "chạy lại lệnh này sẽ tiếp tục từ chỗ dừng.")
                    break
            with gzip.open(path, "rt", encoding="utf-8") as f:
                data = json.load(f)
            label, document_id, copy_id = data["file"], data["document_id"], data["digital_copy_id"]
            try:
                pdf = locate(label, args.pdf_dirs)
                if not pdf.is_file() or sha256(pdf) != data["file_hash"]:
                    report("ERROR", label, "File PDF gốc không có hoặc đã thay đổi so với lúc bóc tách")
                    continue
                relative_path = f"docs/{document_id}/{data['file_hash'][:32]}.pdf"
                copy_file = args.output / "copies" / f"{copy_id}.json.gz"

                if not conn.execute("SELECT 1 FROM public.documents WHERE id = %s AND deleted_at IS NULL",
                                    (document_id,)).fetchone():
                    report("ERROR", label, "Không tìm thấy biểu ghi (sai id hoặc đã xóa)")
                    continue
                if conn.execute("SELECT 1 FROM public.digital_copies WHERE id = %s", (copy_id,)).fetchone():
                    if not args.dry_run:  # vẫn bảo đảm file có mặt, vd khi chuyển sang máy chủ khác
                        (upload_dir / relative_path).parent.mkdir(parents=True, exist_ok=True)
                        if not (upload_dir / relative_path).exists():
                            shutil.copyfile(pdf, upload_dir / relative_path)
                        if copy_file.exists() and not (copies_dir / copy_file.name).exists():
                            shutil.copyfile(copy_file, copies_dir / copy_file.name)
                    report("SKIP", label, "Đã nạp trước đó")
                    continue
                if conn.execute("""SELECT 1 FROM public.digital_copies WHERE document_id = %s AND file_hash = %s
                                   AND processing_status <> 'FAILED'""", (document_id, data["file_hash"])).fetchone():
                    report("SKIP", label, "PDF này đã được tải lên biểu ghi qua giao diện")
                    continue
                current = conn.execute("SELECT 1 FROM public.digital_copies WHERE document_id = %s AND is_current",
                                       (document_id,)).fetchone()
                if current and not args.replace:
                    report("SKIP", label, "Biểu ghi đã có bản số khác (dùng --replace để thay)")
                    continue
                note = data["note"] or ""
                if data["search_config_id"] != active_config:
                    note += " [cấu hình chia đoạn đã đổi từ lúc đóng gói]"
                if args.dry_run:
                    report("DRYRUN", label, f"{data['processing_status']}, {len(data['chunks'])} đoạn {note}")
                    continue

                (upload_dir / relative_path).parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(pdf, upload_dir / relative_path)
                if copy_file.exists():
                    copies_dir.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(copy_file, copies_dir / copy_file.name)

                with conn.transaction(), conn.cursor() as cur:
                    cur.execute("UPDATE public.digital_copies SET is_current = false, updated_at = now() "
                                "WHERE document_id = %s AND is_current = true", (document_id,))
                    cur.execute(
                        """INSERT INTO public.digital_copies
                           (id, document_id, version, is_current, processing_status, file_name, file_path, file_hash,
                            file_size, page_count, extraction_quality, created_at, updated_at)
                           VALUES (%s, %s, %s, true, %s, %s, %s, %s, %s, %s, %s, now(), now())""",
                        (copy_id, document_id, "1.0" if not current else f"batch-{date.today():%Y%m%d}",
                         data["processing_status"], clean(data["file_name"]), relative_path, data["file_hash"],
                         data["file_size"], data["page_count"], data["extraction_quality"]))
                    cur.execute(
                        """INSERT INTO public.background_jobs
                           (job_type, target_type, target_id, payload, status, attempts, error_message,
                            started_at, finished_at, created_at, updated_at)
                           VALUES (%s, 'digital_copies', %s, %s::jsonb, 'COMPLETED', 1, %s, now(), now(), now(), now())""",
                        (JOB_TYPE, copy_id, json.dumps({
                            "digitalCopyId": copy_id, "documentId": document_id, "filePath": relative_path,
                            "searchConfigId": data["search_config_id"], "source": "kaggle_extract"}),
                         clean(data["note"])))
                    cur.executemany(
                        """INSERT INTO public.search_document_chunks
                           (id, digital_copy_id, document_id, search_config_id, chunk_index, chapter, section,
                            page_number, page_end, printed_page_label, chunk_text, token_count, fts_tokens,
                            embedding, created_at)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, to_tsvector('simple', %s),
                                   %s::vector, now())""",
                        [(c["id"], copy_id, document_id, data["search_config_id"], c["index"], clean(c["chapter"]),
                          clean(c["section"]), c["page_number"], c["page_end"], clean(c["printed_page_label"]),
                          clean(c["text"]), c["token_count"], clean(c["text"]), to_vector(c["embedding"]))
                         for c in data["chunks"]])
                report("OK", label, f"{data['processing_status']}, {len(data['chunks'])} đoạn {note}")
            except Exception as ex:  # noqa: BLE001 - một file lỗi không được dừng cả lượt
                report("ERROR", label, f"{type(ex).__name__}: {ex}")

    print("\n" + ", ".join(f"{k}={v}" for k, v in sorted(totals.items())))
    if totals.get("OK") and args.worker_url and not args.dry_run:
        try:
            urllib.request.urlopen(urllib.request.Request(f"{args.worker_url}/api/index/rebuild", method="POST"),
                                   timeout=10)
            print("Đã yêu cầu worker dựng lại chỉ mục TF-IDF.")
        except OSError as ex:
            print(f"Không gọi được worker ({ex}). Bật worker rồi POST {args.worker_url}/api/index/rebuild")


if __name__ == "__main__":
    main()
