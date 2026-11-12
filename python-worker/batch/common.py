"""Hàm dùng chung cho các bước xử lý hàng loạt."""
import csv
import hashlib
import uuid
from pathlib import Path

MAX_PDF_BYTES = 100 * 1024 * 1024  # trùng spring.servlet.multipart.max-file-size
JOB_TYPE = "PDF_EXTRACTION_AND_INDEXING"  # trùng IndexingJobService.PDF_INDEXING_JOB_TYPE
# Id cố định theo (biểu ghi, nội dung file): chạy lại Kaggle hay nạp lại đều ra cùng id, không tạo bản trùng
ID_NAMESPACE = uuid.UUID("6f1c2b8e-4d0a-4c6b-9a57-2f0e3c1d9b44")


def mapping_label(root: Path, path: Path) -> str:
    """Tên file trong mapping.csv: <tên thư mục gốc>/<đường dẫn con>, để dùng được nhiều thư mục cùng lúc."""
    return f"{root.name}/{path.relative_to(root).as_posix()}"


def locate(label: str, roots: list[Path]) -> Path:
    head, _, rest = label.partition("/")
    for root in roots:
        if root.name == head:
            return root / rest
    raise FileNotFoundError(f"Không có thư mục gốc '{head}' cho {label}")


def bundle_name(label: str) -> str:
    """Tên PDF trong zip gửi Kaggle: Kaggle cấm ký tự như & [ ] trong tên file, nên dùng tên ASCII cố định theo nhãn.
    Tên gốc vẫn nằm ở cột file của mapping.csv."""
    return f"pdfs/{hashlib.sha256(label.encode('utf-8')).hexdigest()[:20]}.pdf"


def read_mapping(path: Path) -> list[dict]:
    rows = list(csv.DictReader(path.open(encoding="utf-8-sig")))
    return [r for r in rows if (r.get("document_id") or "").strip()]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def check_pdf(path: Path) -> str | None:
    if not path.is_file():
        return "Không tìm thấy file"
    size = path.stat().st_size
    if size == 0:
        return "File rỗng"
    if size > MAX_PDF_BYTES:
        return "Vượt quá 100 MB"
    with path.open("rb") as f:
        if f.read(5) != b"%PDF-":
            return "Nội dung không phải PDF"
    return None


def copy_id_for(document_id: str, file_hash: str) -> uuid.UUID:
    return uuid.uuid5(ID_NAMESPACE, f"{document_id.lower()}:{file_hash}")


def chunk_id_for(copy_id: uuid.UUID, index: int) -> uuid.UUID:
    return uuid.uuid5(copy_id, str(index))
