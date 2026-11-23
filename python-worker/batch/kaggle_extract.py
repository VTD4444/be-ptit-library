"""
Bước chạy trên Kaggle (GPU), KHÔNG kết nối CSDL: bóc tách PDF, chia đoạn, sinh embedding và ghi kết quả ra file.
Dùng lại đúng các hàm của worker (pdf_extract.extract, indexer.build_positional_index, chunker.chunk_words,
copy_store) nên kết quả giống hệt khi tải PDF lên qua Spring.

    python -m batch.kaggle_extract --bundle /kaggle/input/<dataset> --out /kaggle/working/output --device cuda

--bundle là thư mục giải nén từ zip của pack_for_kaggle: mapping.csv, chunk_config.json, pdfs/<mã>.pdf (tên ASCII, xem common.bundle_name)

Đầu ra (tải về rồi chạy import_results trên máy có CSDL):
    results/{digital_copy_id}.json.gz  bản số + các đoạn văn bản + embedding (base64 float32)
    copies/{digital_copy_id}.json.gz   dữ liệu vị trí từng từ (để tô sáng kết quả tìm kiếm)
    report.csv                         kết quả từng file
Chạy lại an toàn: file đã có trong results/ được bỏ qua.
"""
import argparse
import base64
import csv
import gzip
import json
import os
import sys
import time
from pathlib import Path

WORKER = Path(__file__).resolve().parent.parent
REPORT_FIELDS = ["file", "document_id", "digital_copy_id", "result", "processing_status", "pages",
                 "quality", "chunks", "seconds", "note"]


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("output"))
    parser.add_argument("--device", default="cpu", help="cuda để sinh embedding bằng GPU")
    parser.add_argument("--batch-size", type=int, default=64, help="Số đoạn mã hóa mỗi lượt (GPU T4: 64)")
    parser.add_argument("--limit", type=int, default=0, help="Chỉ xử lý N file đầu (chạy thử)")
    parser.add_argument("--no-embedding", action="store_true",
                        help="Bỏ qua embedding (worker sinh bù sau qua /api/embeddings/backfill)")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    out = args.out.resolve()
    # copy_store ghi vào DATA_DIR/copies: trỏ DATA_DIR vào thư mục đầu ra trước khi import app.*
    os.environ["DATA_DIR"] = str(out)
    sys.path.insert(0, str(WORKER))

    from app import config, copy_store
    from app.chunker import chunk_words
    from app.indexer import build_positional_index
    from app.pdf_extract import extract
    from batch.common import bundle_name, check_pdf, chunk_id_for, copy_id_for, read_mapping, sha256

    bundle = args.bundle.resolve()
    chunk_config = json.loads((bundle / "chunk_config.json").read_text(encoding="utf-8"))
    rows = read_mapping(bundle / "mapping.csv")
    if args.limit:
        rows = rows[:args.limit]
    results_dir = out / "results"
    results_dir.mkdir(parents=True, exist_ok=True)
    (out / "copies").mkdir(parents=True, exist_ok=True)

    model = None
    if not args.no_embedding:
        from sentence_transformers import SentenceTransformer

        model = SentenceTransformer(chunk_config["embedding_model"], device=args.device)
        if model.get_sentence_embedding_dimension() != chunk_config["embedding_dimensions"]:
            raise SystemExit("Số chiều model embedding không khớp CSDL")

    report_path = out / "report.csv"
    new_report = not report_path.exists()
    report_file = report_path.open("a", newline="", encoding="utf-8-sig")
    report = csv.DictWriter(report_file, fieldnames=REPORT_FIELDS)
    if new_report:
        report.writeheader()

    totals: dict[str, int] = {}
    started_all = time.perf_counter()
    for number, row in enumerate(rows, start=1):
        started = time.perf_counter()
        label, document_id = row["file"], row["document_id"].strip()
        entry = {"file": label, "document_id": document_id}

        def finish(result: str, note: str = "", **extra) -> None:
            entry.update(result=result, note=note, seconds=round(time.perf_counter() - started, 1), **extra)
            report.writerow(entry)
            report_file.flush()
            totals[result] = totals.get(result, 0) + 1
            print(f"[{number}/{len(rows)}] {result:<6} {label} {note}", flush=True)

        try:
            pdf = bundle / bundle_name(label)
            problem = check_pdf(pdf)
            if problem:
                finish("ERROR", problem)
                continue
            file_hash = sha256(pdf)
            copy_id = copy_id_for(document_id, file_hash)
            target = results_dir / f"{copy_id}.json.gz"
            if target.exists():
                finish("SKIP", "Đã xử lý ở lượt trước", digital_copy_id=copy_id)
                continue

            result = extract(str(pdf))
            terms, token_starts, token_ends = build_positional_index(result)
            chunks = chunk_words(result.words, result.headings,
                                 chunk_config["chunk_size"], chunk_config["overlap"])
            chunk_ids = [str(chunk_id_for(copy_id, c.index)) for c in chunks]

            copy_store.save(copy_store.CopyData(
                copy_id=str(copy_id), document_id=document_id,
                words=[w.text for w in result.words], pages=[w.page for w in result.words],
                paragraphs=[w.paragraph for w in result.words], sentences=[w.sentence for w in result.words],
                lines=[w.line for w in result.words], bboxes=[list(w.bbox) for w in result.words],
                heading_starts=[h.start for h in result.headings],
                heading_titles=[(h.chapter, h.section) for h in result.headings],
                page_sizes=[list(s) for s in result.page_sizes], page_labels=result.page_labels,
                terms=terms, token_starts=token_starts, token_ends=token_ends, chunk_ids=chunk_ids,
                chunk_starts=[c.start for c in chunks], chunk_ends=[c.end for c in chunks],
            ))

            embeddings = [None] * len(chunks)
            if model is not None and chunks:
                vectors = model.encode([chunk_config["passage_prefix"] + c.text for c in chunks],
                                       batch_size=args.batch_size, normalize_embeddings=True,
                                       show_progress_bar=False, convert_to_numpy=True).astype("float32")
                embeddings = [base64.b64encode(v.tobytes()).decode("ascii") for v in vectors]

            # Cùng quy tắc trạng thái với indexer.run_indexing
            low_quality = not chunks or result.quality < config.LOW_QUALITY_THRESHOLD
            status = "LOW_QUALITY" if low_quality else "INDEXED"
            if not chunks:
                note = "Không trích xuất được văn bản (có thể là PDF scan, chưa hỗ trợ OCR)"
            elif low_quality:
                note = f"Chỉ {result.quality:.0%} số trang có văn bản trích xuất được"
            elif model is None:
                note = "Chưa sinh được embedding, tài liệu tạm thời chỉ tìm được bằng TF-IDF"
            else:
                note = None

            payload = {
                "format": 1,
                "document_id": document_id, "digital_copy_id": str(copy_id),
                "file": label, "file_name": pdf.name, "file_hash": file_hash, "file_size": pdf.stat().st_size,
                "page_count": result.page_count, "extraction_quality": result.quality,
                "processing_status": status, "note": note,
                "search_config_id": chunk_config.get("search_config_id"),
                "embedding_model": chunk_config["embedding_model"] if model is not None else None,
                "chunks": [{
                    "id": chunk_id, "index": c.index, "chapter": c.chapter, "section": c.section,
                    "page_number": c.page_number, "page_end": c.page_end,
                    "printed_page_label": result.page_labels.get(c.page_number),
                    "text": c.text, "token_count": c.token_count, "embedding": embedding,
                } for chunk_id, c, embedding in zip(chunk_ids, chunks, embeddings)],
            }
            # Ghi ra file tạm rồi đổi tên: file kết quả tồn tại nghĩa là đã xử lý trọn vẹn
            temporary = target.with_suffix(".tmp")
            with gzip.open(temporary, "wt", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
            temporary.replace(target)
            finish("OK" if status == "INDEXED" else "LOWQ", note or "", digital_copy_id=copy_id,
                   processing_status=status, pages=result.page_count, quality=round(result.quality, 3),
                   chunks=len(chunks))
        except Exception as ex:  # noqa: BLE001 - một file lỗi không được dừng cả lượt
            finish("ERROR", f"{type(ex).__name__}: {ex}")

    report_file.close()
    minutes = (time.perf_counter() - started_all) / 60
    print(f"\nXong {len(rows)} file trong {minutes:.1f} phút: "
          + ", ".join(f"{k}={v}" for k, v in sorted(totals.items())))
    print(f"Kết quả: {out} (tải về rồi chạy batch.import_results)")


if __name__ == "__main__":
    main()
