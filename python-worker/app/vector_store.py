"""
Kho embedding trong RAM cho tim kiem vector (che do vector / hybrid).

Vi sao khong dung ORDER BY embedding <=> q tren DB: bang search_document_chunks khong co chi muc vector. Chi muc HNSW
cho ~51k chunk x 768 chieu ton ~150-200 MB, vuot goi free 500 MB cua Supabase (DB da ~413 MB), nen moi lan tim
Postgres phai quet ca bang: 30-35 s, qua thoi gian cho 30 s cua frontend. Worker giu toan bo vector (da chuan hoa L2)
trong RAM va tinh cosine bang mot phep nhan ma tran (vai chuc ms).

Nguon su that van la cot embedding tren DB. Ban sao trong RAM duoc luu dem ra DATA_DIR/vectors de khoi dong lai
khong phai tai lai ca kho (~50 s), chi tai phan chenh lech:
  - khoi dong:             nap file dem roi sync() voi DB
  - sinh embedding moi:    embedder.embed_pending goi put() voi vector vua tinh
  - /api/index/rebuild:    sync() (vd sau khi xoa tai lieu, nap lo bang batch.import_results)

LUU Y (xem README muc "Tim kiem vector trong RAM"):
  - RAM tang tuyen tinh theo so chunk: N x 768 x 4 byte (51k chunk ~ 157 MB). Toi ~500k chunk nen chuyen sang
    chi muc ANN (HNSW tren DB khi co dung luong, hoac faiss/hnswlib trong worker).
  - Chi mot tien trinh worker: chay nhieu worker thi moi tien trinh giu mot ban sao.
  - Ghi embedding vao DB bang cong cu ngoai worker thi phai goi /api/index/rebuild de RAM cap nhat.
"""
import logging
import threading
import time
from pathlib import Path

import numpy as np

from app import config
from app.db import pool

log = logging.getLogger(__name__)

CACHE_FILE = "embeddings.npz"
FETCH_BATCH = 2000

# Chunk dang tim duoc: ban so hien hanh cua tai lieu chua xoa (giong dieu kien truoc day trong cau SQL)
_CURRENT_SQL = """SELECT c.id::text, c.digital_copy_id::text
                  FROM public.search_document_chunks c
                  JOIN public.digital_copies dc ON dc.id = c.digital_copy_id AND dc.is_current = true
                  JOIN public.documents d ON d.id = c.document_id AND d.deleted_at IS NULL
                  WHERE c.embedding IS NOT NULL"""


class VectorStoreNotReady(RuntimeError):
    """Chua nap xong kho vector (lan dau tai tu DB mat ~1 phut): hybrid tam dung TF-IDF."""


class _Store:
    """Bat bien: cap nhat bang cach tao doi tuong moi roi doi tham chieu, nen luong tim kiem khong can khoa."""

    def __init__(self, ids: list[str], copies: list[str], matrix: np.ndarray):
        self.ids = ids
        self.copies = copies
        self.matrix = matrix
        self.row_of = {chunk_id: row for row, chunk_id in enumerate(ids)}
        names = sorted(set(copies))
        self.copy_code = {name: code for code, name in enumerate(names)}
        self.copy_codes = np.fromiter((self.copy_code[c] for c in copies), dtype=np.int32, count=len(copies))

    @classmethod
    def empty(cls) -> "_Store":
        return cls([], [], np.zeros((0, config.EMBEDDING_DIMENSIONS), dtype=np.float32))

    def __len__(self) -> int:
        return len(self.ids)


_store: _Store | None = None
_lock = threading.Lock()


def _cache_path() -> Path:
    return config.DATA_DIR / "vectors" / CACHE_FILE


def _decode(raw: bytes) -> np.ndarray:
    # Dinh dang nhi phan cua pgvector (vector_send): int16 so chieu, int16 du phong, roi float32 big-endian
    return np.frombuffer(raw, dtype=">f4", offset=4).astype(np.float32)


def _load_cache() -> _Store:
    path = _cache_path()
    if not path.exists():
        return _Store.empty()
    try:
        with np.load(path, allow_pickle=False) as data:
            if str(data["model"]) != config.EMBEDDING_MODEL or data["matrix"].shape[1] != config.EMBEDDING_DIMENSIONS:
                log.info("File dem vector thuoc model khac, bo qua")
                return _Store.empty()
            return _Store(data["ids"].tolist(), data["copies"].tolist(), data["matrix"].astype(np.float32))
    except Exception:  # noqa: BLE001 - file dem hong thi tai lai tu DB
        log.exception("Khong doc duoc file dem vector %s, tai lai tu DB", path)
        return _Store.empty()


def _save_cache(store: _Store) -> None:
    path = _cache_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.stem + ".tmp.npz")
    # float16 tren dia cho nhe (~1/2); sai so ~1e-3 khong doi thu hang dang ke
    np.savez(tmp, ids=np.array(store.ids), copies=np.array(store.copies),
             matrix=store.matrix.astype(np.float16), model=np.array(config.EMBEDDING_MODEL))
    tmp.replace(path)


def _fetch(chunk_ids: list[str]) -> dict[str, np.ndarray]:
    vectors: dict[str, np.ndarray] = {}
    for start in range(0, len(chunk_ids), FETCH_BATCH):
        batch = chunk_ids[start:start + FETCH_BATCH]
        with pool.connection() as conn:
            rows = conn.execute("""SELECT c.id::text, vector_send(c.embedding)
                                   FROM public.search_document_chunks c WHERE c.id = ANY(%s::uuid[])""",
                                (batch,)).fetchall()
        vectors.update((chunk_id, _decode(raw)) for chunk_id, raw in rows)
        if len(chunk_ids) > FETCH_BATCH:
            log.info("Tai vector %d/%d", min(start + FETCH_BATCH, len(chunk_ids)), len(chunk_ids))
    return vectors


def sync() -> None:
    """Dong bo voi DB: bo chunk khong con tim duoc, tai vector cua chunk moi. Vector da co thi giu nguyen."""
    global _store
    with _lock:
        started = time.perf_counter()
        current = _store if _store is not None else _load_cache()
        with pool.connection() as conn:
            wanted = conn.execute(_CURRENT_SQL).fetchall()
        missing = [chunk_id for chunk_id, _ in wanted if chunk_id not in current.row_of]
        fetched = _fetch(missing) if missing else {}

        ids, copies, rows, new_vectors = [], [], [], []
        for chunk_id, copy_id in wanted:
            row = current.row_of.get(chunk_id)
            if row is None and chunk_id not in fetched:
                continue  # embedding vua bi xoa giua hai cau truy van
            ids.append(chunk_id)
            copies.append(copy_id)
            rows.append(row if row is not None else -1)
            if row is None:
                new_vectors.append(fetched[chunk_id])
        matrix = np.empty((len(ids), config.EMBEDDING_DIMENSIONS), dtype=np.float32)
        old_rows = np.array(rows, dtype=np.int64)
        kept = old_rows >= 0
        if kept.any():
            matrix[kept] = current.matrix[old_rows[kept]]
        if new_vectors:
            matrix[~kept] = np.vstack(new_vectors)

        store = _Store(ids, copies, matrix)
        changed = len(store) != len(current) or bool(missing) or store.ids != current.ids
        _store = store
        if changed:
            _save_cache(store)
        log.info("Kho vector trong RAM: %d chunk (%d tai moi, %d bo) trong %.1fs, %.0f MB", len(store),
                 len(fetched), len(current) - int(kept.sum()), time.perf_counter() - started, matrix.nbytes / 2**20)


def load() -> None:
    """Goi luc khoi dong o luong rieng; loi thi hybrid tam dung TF-IDF cho toi lan sync sau."""
    try:
        sync()
    except Exception:  # noqa: BLE001
        log.exception("Khong nap duoc kho vector, tim kiem vector tam thoi khong dung duoc")


def put(items: list[tuple[str, str, np.ndarray]]) -> None:
    """Cap nhat vector vua sinh (chunk_id, copy_id, vector da chuan hoa) ma khong tai lai tu DB."""
    global _store
    if not items:
        return
    with _lock:
        if _store is None:
            return  # chua nap xong: lan sync dau se tai ca cac vector nay tu DB
        ids, copies = list(_store.ids), list(_store.copies)
        matrix = _store.matrix.copy()
        appended = []
        for chunk_id, copy_id, vector in items:
            row = _store.row_of.get(chunk_id)
            if row is None:
                ids.append(chunk_id)
                copies.append(copy_id)
                appended.append(np.asarray(vector, dtype=np.float32))
            else:
                matrix[row] = vector
        if appended:
            matrix = np.vstack([matrix, np.vstack(appended)])
        _store = _Store(ids, copies, matrix)
        _save_cache(_store)


def is_ready() -> bool:
    return _store is not None


def size() -> int:
    return len(_store) if _store is not None else 0


def search(query_vector: np.ndarray, top_k: int, copy_ids: list[str] | None = None) -> list[tuple[str, float]]:
    """Tra ve (chunk_id, cosine) giam dan. copy_ids: chi tim trong cac ban so nay."""
    store = _store
    if store is None:
        raise VectorStoreNotReady("Kho vector chua nap xong")
    if not len(store):
        return []
    scores = store.matrix @ np.asarray(query_vector, dtype=np.float32)
    if copy_ids is not None:
        codes = [store.copy_code[c] for c in copy_ids if c in store.copy_code]
        if not codes:
            return []
        scores = np.where(np.isin(store.copy_codes, codes), scores, -np.inf)
    k = min(top_k, len(scores))
    top = np.argpartition(-scores, k - 1)[:k]
    top = top[np.argsort(-scores[top])]
    return [(store.ids[i], float(scores[i])) for i in top if np.isfinite(scores[i])]
