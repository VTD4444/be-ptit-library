"""
Đóng gói code worker + PDF đã ghép + mapping.csv + cấu hình chia đoạn thành một zip để tạo Kaggle Dataset.

    cd python-worker
    .venv\\Scripts\\python -m batch.pack_for_kaggle mapping.csv "D:\\Books" "D:\\Hieu" --out kaggle-upload.zip

Zip KHÔNG chứa .env, .venv, data/ hay mật khẩu: bước Kaggle không kết nối CSDL.
Cấu hình chia đoạn (search_vector_configs đang bật) và model embedding được đọc từ CSDL lúc đóng gói và ghi vào
chunk_config.json, để kết quả trên Kaggle khớp với cách worker sẽ tự bóc tách.
"""
import argparse
import json
import os
import sys
import zipfile
from pathlib import Path

WORKER = Path(__file__).resolve().parent.parent
GPU_UNSAFE = ("torch", "--extra-index-url")  # Kaggle đã có PyTorch bản GPU; bản CPU trong requirements sẽ đè mất


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mapping", type=Path)
    parser.add_argument("pdf_dirs", type=Path, nargs="+")
    parser.add_argument("--out", type=Path, default=Path("kaggle-upload.zip"))
    args = parser.parse_args()

    sys.path.insert(0, str(WORKER))
    from dotenv import load_dotenv

    load_dotenv(WORKER / ".env")
    import psycopg

    from app import config
    from batch.common import bundle_name, check_pdf, locate, read_mapping

    rows = read_mapping(args.mapping)
    problems = []
    for row in rows:
        try:
            problem = check_pdf(locate(row["file"], args.pdf_dirs))
        except FileNotFoundError as ex:
            problem = str(ex)
        if problem:
            problems.append(f"{row['file']}: {problem}")
    if problems:
        raise SystemExit("Sửa các file sau trong mapping.csv (hoặc xóa document_id để bỏ qua):\n  " + "\n  ".join(problems))

    with psycopg.connect(os.environ["DATABASE_URL"], prepare_threshold=None) as conn:
        active = conn.execute("SELECT id, chunk_size, overlap FROM public.search_vector_configs "
                              "WHERE is_active = true LIMIT 1").fetchone()
    config_id, chunk_size, overlap = active or (None, None, None)
    chunk_config = {
        "search_config_id": config_id,
        "chunk_size": chunk_size or config.DEFAULT_CHUNK_SIZE,
        "overlap": overlap if overlap is not None else config.DEFAULT_OVERLAP,
        "embedding_model": config.EMBEDDING_MODEL,
        "embedding_dimensions": config.EMBEDDING_DIMENSIONS,
        "passage_prefix": config.EMBEDDING_PASSAGE_PREFIX,
    }

    requirements = [line for line in (WORKER / "requirements.txt").read_text(encoding="utf-8").splitlines()
                    if not line.strip().startswith(GPU_UNSAFE)]
    total = 0
    with zipfile.ZipFile(args.out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for package in ("app", "batch"):
            for path in sorted((WORKER / package).rglob("*")):
                if path.is_file() and "__pycache__" not in path.parts and path.suffix in {".py", ".txt"}:
                    zf.write(path, f"worker/{path.relative_to(WORKER).as_posix()}")
        zf.writestr("worker/requirements-kaggle.txt", "\n".join(requirements) + "\n")
        zf.writestr("chunk_config.json", json.dumps(chunk_config, ensure_ascii=False, indent=2))
        zf.write(args.mapping, "mapping.csv")
        for row in rows:
            source = locate(row["file"], args.pdf_dirs)
            zf.write(source, bundle_name(row["file"]), compress_type=zipfile.ZIP_STORED)  # PDF vốn đã nén
            total += source.stat().st_size
    print(f"Đã tạo {args.out}: {len(rows)} PDF ({total / 1024 / 1024:.0f} MB) + code worker + mapping.csv")
    print(f"Cấu hình chia đoạn: {chunk_config}")


if __name__ == "__main__":
    main()
