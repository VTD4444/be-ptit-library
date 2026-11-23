"""
Ghép từng file PDF với biểu ghi có sẵn trong CSDL theo nhan đề, xuất mapping.csv để duyệt trước khi bóc tách.

Chạy nhẹ (không cần GPU, không đọc nội dung PDF ngoài metadata):
    cd python-worker
    .venv\\Scripts\\python -m batch.match_pdfs "D:\\Books" "D:\\Hieu" --out mapping.csv

Kết quả:
- mapping.csv: mỗi file một dòng. status = AUTO (khớp chắc), REVIEW (cần xem lại), NONE (không tìm thấy),
  SCAN (bản scan, chưa có OCR: để trống document_id nên bị bỏ qua).
  Sửa cột document_id nếu ghép sai; để trống document_id thì batch_index bỏ qua file đó.
- documents.csv: toàn bộ biểu ghi (id, nhan đề, đã có bản số chưa) để tra id khi sửa tay.
Cả hai file ghi UTF-8 có BOM để Excel mở đúng tiếng Việt.
"""
import argparse
import csv
import os
import re
import unicodedata
from difflib import SequenceMatcher
from pathlib import Path

import psycopg
from dotenv import load_dotenv

from batch.common import mapping_label

AUTO_SCORE = 0.85
AUTO_MARGIN = 0.05
REVIEW_SCORE = 0.60


def normalize(text: str) -> str:
    """Bỏ dấu, đ→d, chữ thường, chỉ giữ chữ số và chữ cái."""
    text = unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    return " ".join(re.sub(r"[^a-z0-9]+", " ", text).split())


PREFIXES = ("bai giang ", "giao trinh ", "slide ")
YEAR_SUFFIX = re.compile(r"\s+(19|20)\d{2}$")


def file_variants(stem: str) -> set[str]:
    """Các cách đọc tên file: bỏ đuôi năm (" - 2017"), bỏ phần tác giả sau " - ", BG = Bài giảng."""
    parts = [p for p in re.split(r"\s[-–]\s", stem) if p.strip()]
    raw = {stem} | {" - ".join(parts[:i]) for i in range(1, len(parts) + 1)}
    variants = set()
    for text in raw:
        norm = YEAR_SUFFIX.sub("", normalize(text))
        if norm.startswith("bg "):
            norm = "bai giang " + norm[3:]
        variants |= {norm, strip_prefix(norm)}
    return variants - {""}


def strip_prefix(text: str) -> str:
    for prefix in PREFIXES:
        if text.startswith(prefix):
            return text[len(prefix):]
    return text


def similarity(a: str, b: str) -> float:
    """Trung bình độ giống chuỗi và độ phủ từ, để chịu được tên file rút gọn hoặc đảo thứ tự từ."""
    if not a or not b:
        return 0.0
    ratio = SequenceMatcher(None, a, b).ratio()
    ta, tb = set(a.split()), set(b.split())
    overlap = len(ta & tb) / max(len(ta), len(tb))
    return round((ratio + overlap) / 2, 3)


def pdf_title(path: Path) -> str:
    try:
        import fitz  # PyMuPDF, đã có trong requirements của worker

        with fitz.open(path) as doc:
            return (doc.metadata or {}).get("title") or ""
    except Exception:  # noqa: BLE001 - metadata hỏng không được làm dừng cả lượt ghép
        return ""


SCAN_TEXT_RATIO = 0.5  # dưới tỉ lệ này số trang mẫu có chữ thì coi là bản scan
SAMPLE_PAGES = 8


def text_ratio(path: Path) -> float:
    """Tỉ lệ trang mẫu có lớp chữ (>= 50 ký tự). Bản scan (chỉ có ảnh) gần bằng 0, worker chưa có OCR."""
    try:
        import fitz

        with fitz.open(path) as doc:
            n = doc.page_count
            pages = sorted({int(i * (n - 1) / (SAMPLE_PAGES - 1)) for i in range(SAMPLE_PAGES)}) if n > 1 else [0]
            return sum(len(doc[i].get_text().strip()) >= 50 for i in pages) / len(pages)
    except Exception:  # noqa: BLE001 - file hỏng sẽ bị pack_for_kaggle/kaggle_extract báo lỗi riêng
        return 1.0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pdf_dirs", type=Path, nargs="+", help="Một hoặc nhiều thư mục chứa PDF")
    parser.add_argument("--out", type=Path, default=Path("mapping.csv"))
    args = parser.parse_args()

    load_dotenv(Path(__file__).resolve().parent.parent / ".env")
    if len({root.name for root in args.pdf_dirs}) != len(args.pdf_dirs):
        raise SystemExit("Các thư mục PDF phải có tên khác nhau")
    files = sorted((root, p) for root in args.pdf_dirs for p in root.rglob("*")
                   if p.is_file() and p.suffix.lower() == ".pdf")
    if not files:
        raise SystemExit("Không có file .pdf nào")

    with psycopg.connect(os.environ["DATABASE_URL"], prepare_threshold=None) as conn:
        documents = conn.execute(
            """SELECT d.id::text, d.title,
                      EXISTS (SELECT 1 FROM public.digital_copies dc
                              WHERE dc.document_id = d.id AND dc.is_current = true) AS has_digital
               FROM public.documents d WHERE d.deleted_at IS NULL ORDER BY d.title"""
        ).fetchall()
    def title_variants(title: str) -> set[str]:
        # Nhan đề có phụ đề sau ":" (vd "Lồng kính: Tự động hóa và...") còn tên file thường chỉ có phần đầu
        variants = set()
        for text in {title, title.split(":")[0]}:
            norm = normalize(text)
            variants |= {norm, strip_prefix(norm)}
        return variants - {""}

    normalized = [(doc_id, title, has_digital, title_variants(title)) for doc_id, title, has_digital in documents]

    rows = []
    for root, path in files:
        candidates = file_variants(path.stem) | ({normalize(pdf_title(path))} - {""})
        scored = sorted(
            ((max(similarity(c, t) for c in candidates for t in titles), doc_id, title, has_digital)
             for doc_id, title, has_digital, titles in normalized),
            reverse=True,
        )
        (best, doc_id, title, has_digital), second = scored[0], (scored[1] if len(scored) > 1 else (0.0, "", "", False))
        if best >= AUTO_SCORE and best - second[0] >= AUTO_MARGIN:
            status = "AUTO"
        elif best >= REVIEW_SCORE:
            status = "REVIEW"
        else:
            status = "NONE"
        rows.append({
            "file": mapping_label(root, path),
            "document_id": doc_id if status != "NONE" else "",
            "matched_title": title if status != "NONE" else "",
            "score": best,
            "status": status,
            "already_has_digital": "yes" if has_digital and status != "NONE" else "",
            "second_title": second[2],
            "second_score": second[0],
            "note": "",
        })

    # Bản scan: chưa có OCR nên bóc tách không ra chữ. Bỏ trống document_id để các bước sau bỏ qua,
    # giữ biểu ghi đã ghép ở cột note để xử lý sau (vd. khi có OCR thì điền lại id).
    for (root, path), row in zip(files, rows):
        ratio = text_ratio(path)
        if ratio < SCAN_TEXT_RATIO:
            hint = f" (ghép được: {row['matched_title']} | {row['document_id']})" if row["document_id"] else ""
            row.update(status="SCAN", document_id="", already_has_digital="",
                       note=f"Bản scan, {ratio:.0%} trang mẫu có chữ: bỏ qua{hint}")

    # Hai file cùng trỏ vào một biểu ghi thì chắc chắn có file ghép sai
    by_doc: dict[str, list[dict]] = {}
    for row in rows:
        if row["document_id"]:
            by_doc.setdefault(row["document_id"], []).append(row)
    for group in by_doc.values():
        if len(group) > 1:
            for row in group:
                row["status"] = "REVIEW"
                row["note"] = f"{len(group)} file cùng ghép vào biểu ghi này"

    with args.out.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    documents_csv = args.out.with_name("documents.csv")
    with documents_csv.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["document_id", "title", "has_digital_copy"])
        writer.writerows((doc_id, title, "yes" if has_digital else "") for doc_id, title, has_digital in documents)

    counts = {s: sum(r["status"] == s for r in rows) for s in ("AUTO", "REVIEW", "NONE", "SCAN")}
    print(f"{len(rows)} file: " + ", ".join(f"{n} {s}" for s, n in counts.items()))
    print(f"Đã ghi {args.out} và {documents_csv}. Duyệt các dòng REVIEW/NONE trước khi chạy batch_index.")


if __name__ == "__main__":
    main()
