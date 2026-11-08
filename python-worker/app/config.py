import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")
load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://unused")

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

MIN_CHARS_PER_PAGE = int(os.getenv("MIN_CHARS_PER_PAGE", "50"))
LOW_QUALITY_THRESHOLD = float(os.getenv("LOW_QUALITY_THRESHOLD", "0.5"))
