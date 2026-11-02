"""
Du lieu vi tri cua tung ban so, luu tai DATA_DIR/copies/{digital_copy_id}.json.gz:
- Dong tu chuan (words): moi tu co trang, doan, cau, dong, toa do -> nguon duy nhat de dinh vi.
- Positional index (Lecture 8): danh sach token theo thu tu, moi token tro ve khoang tu goc [start, end].
- Chunk chi la khoang [start, end) tren dong tu; cac chunk goi nhau dung chung vi tri tu nen gop duoc.
"""
import bisect
import gzip
import json
import threading
from collections import OrderedDict
from dataclasses import dataclass

from app import config


@dataclass
class CopyData:
    copy_id: str
    document_id: str
    words: list[str]
    pages: list[int]
    paragraphs: list[int]
    sentences: list[int]
    lines: list[int]
    bboxes: list[list[float]]
    heading_starts: list[int]
    heading_titles: list[tuple[str | None, str | None]]
    page_sizes: list[list[float]]
    page_labels: dict[int, str]
    terms: list[str]
    token_starts: list[int]
    token_ends: list[int]
    chunk_ids: list[str]
    chunk_starts: list[int]
    chunk_ends: list[int]

    def token_range(self, start: int, end: int) -> range:
        """Chi so cac token bat dau trong khoang tu goc [start, end)."""
        return range(bisect.bisect_left(self.token_starts, start), bisect.bisect_left(self.token_starts, end))

    def heading_at(self, position: int) -> tuple[str | None, str | None]:
        i = bisect.bisect_right(self.heading_starts, position) - 1
        return self.heading_titles[i] if i >= 0 else (None, None)

    def to_json(self) -> dict:
        return {
            "copyId": self.copy_id, "documentId": self.document_id,
            "words": self.words, "pages": self.pages, "paragraphs": self.paragraphs,
            "sentences": self.sentences, "lines": self.lines, "bboxes": self.bboxes,
            "headingStarts": self.heading_starts, "headingTitles": self.heading_titles,
            "pageSizes": self.page_sizes, "pageLabels": {str(k): v for k, v in self.page_labels.items()},
            "terms": self.terms, "tokenStarts": self.token_starts, "tokenEnds": self.token_ends,
            "chunkIds": self.chunk_ids, "chunkStarts": self.chunk_starts, "chunkEnds": self.chunk_ends,
        }

    @classmethod
    def from_json(cls, d: dict) -> "CopyData":
        return cls(
            copy_id=d["copyId"], document_id=d["documentId"],
            words=d["words"], pages=d["pages"], paragraphs=d["paragraphs"],
            sentences=d["sentences"], lines=d["lines"], bboxes=d["bboxes"],
            heading_starts=d["headingStarts"], heading_titles=[tuple(t) for t in d["headingTitles"]],
            page_sizes=d["pageSizes"], page_labels={int(k): v for k, v in d["pageLabels"].items()},
            terms=d["terms"], token_starts=d["tokenStarts"], token_ends=d["tokenEnds"],
            chunk_ids=d["chunkIds"], chunk_starts=d["chunkStarts"], chunk_ends=d["chunkEnds"],
        )


_cache: "OrderedDict[str, tuple[float, CopyData]]" = OrderedDict()
_cache_lock = threading.Lock()
_CACHE_SIZE = 64


def _path(copy_id: str):
    return config.COPIES_DIR / f"{copy_id}.json.gz"


def save(data: CopyData) -> None:
    config.COPIES_DIR.mkdir(parents=True, exist_ok=True)
    path = _path(data.copy_id)
    tmp = path.with_suffix(".tmp")
    with gzip.open(tmp, "wt", encoding="utf-8") as f:
        json.dump(data.to_json(), f, ensure_ascii=False, separators=(",", ":"))
    tmp.replace(path)


def load(copy_id: str) -> CopyData | None:
    path = _path(copy_id)
    try:
        mtime = path.stat().st_mtime
    except FileNotFoundError:
        return None
    with _cache_lock:
        cached = _cache.get(copy_id)
        if cached and cached[0] == mtime:
            _cache.move_to_end(copy_id)
            return cached[1]
    with gzip.open(path, "rt", encoding="utf-8") as f:
        data = CopyData.from_json(json.load(f))
    with _cache_lock:
        _cache[copy_id] = (mtime, data)
        _cache.move_to_end(copy_id)
        while len(_cache) > _CACHE_SIZE:
            _cache.popitem(last=False)
    return data
