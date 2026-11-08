import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

DATABASE_URL = os.environ["DATABASE_URL"]

_upload_root = os.getenv("UPLOAD_ROOT")
UPLOAD_ROOT = Path(_upload_root).resolve() if _upload_root else None

# Thu muc du lieu cua worker:
#   tfidf/   - mo hinh TF-IDF + inverted file muc tai lieu
#   copies/  - dong tu va positional index cua tung ban so
DATA_DIR = Path(os.getenv("DATA_DIR", "./data")).resolve()
MODEL_DIR = DATA_DIR / "tfidf"
COPIES_DIR = DATA_DIR / "copies"

SYNONYMS_FILE = Path(os.getenv("SYNONYMS_FILE", Path(__file__).with_name("synonyms.txt"))).resolve()

# Chia nho tai lieu dai thanh cac phan, moi phan la mot van ban (XLVB muc 1.2). Don vi: so tu goc.
DEFAULT_CHUNK_SIZE = int(os.getenv("DEFAULT_CHUNK_SIZE", "200"))
DEFAULT_OVERLAP = int(os.getenv("DEFAULT_OVERLAP", "40"))

# Cat tia tu dien (XLVB muc 1.4)
TFIDF_MIN_DF = int(os.getenv("TFIDF_MIN_DF", "3"))
TFIDF_MAX_DF = float(os.getenv("TFIDF_MAX_DF", "0.5"))
TFIDF_MAX_FEATURES = int(os.getenv("TFIDF_MAX_FEATURES", "200000"))

# So chunk ung vien lay theo cosine truoc khi gop cac chunk goi nhau thanh doan
SEARCH_CANDIDATE_CHUNKS = int(os.getenv("SEARCH_CANDIDATE_CHUNKS", "300"))
# Do dai trich doan (so tu goc) quanh vung khop
SNIPPET_WORDS = int(os.getenv("SNIPPET_WORDS", "50"))

# Che do tim kiem mac dinh: tfidf | vector | hybrid (moi request co the chon rieng qua searchMode)
SEARCH_MODES = ("tfidf", "vector", "hybrid")
SEARCH_MODE = os.getenv("SEARCH_MODE", "tfidf").strip().lower()
if SEARCH_MODE not in SEARCH_MODES:
    raise ValueError(f"SEARCH_MODE phai la mot trong {SEARCH_MODES}, nhan duoc: {SEARCH_MODE!r}")

# Embedding: doi model khac so chieu thi phai doi kieu cot vector(N) va sinh lai toan bo embedding
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "intfloat/multilingual-e5-base")
EMBEDDING_DIMENSIONS = int(os.getenv("EMBEDDING_DIMENSIONS", "768"))
EMBEDDING_BATCH_SIZE = int(os.getenv("EMBEDDING_BATCH_SIZE", "16"))
# Ho E5 yeu cau tien to phan biet truy van va van ban; model khac thi de trong
EMBEDDING_QUERY_PREFIX = os.getenv("EMBEDDING_QUERY_PREFIX", "query: ")
EMBEDDING_PASSAGE_PREFIX = os.getenv("EMBEDDING_PASSAGE_PREFIX", "passage: ")

# So chunk lay tu moi luong truoc khi gop RRF, va nguong cosine cua luong vector (gia tri tam, chua qua danh gia)
HYBRID_CANDIDATE_CHUNKS = int(os.getenv("HYBRID_CANDIDATE_CHUNKS", "100"))
VECTOR_MIN_SCORE = float(os.getenv("VECTOR_MIN_SCORE", "0.0"))
RRF_K = int(os.getenv("RRF_K", "60"))

MIN_CHARS_PER_PAGE = int(os.getenv("MIN_CHARS_PER_PAGE", "50"))
LOW_QUALITY_THRESHOLD = float(os.getenv("LOW_QUALITY_THRESHOLD", "0.5"))
