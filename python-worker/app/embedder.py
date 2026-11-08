"""
Sinh embedding cho chunk va tim chunk gan nghia bang pgvector.
Model chi nap mot lan; thu vien nang duoc import khi can de chay TF-IDF khong phu thuoc PyTorch.
"""
import logging
import threading
import time
from uuid import UUID

import numpy as np
import psycopg

from app import config
from app.db import pool

log = logging.getLogger(__name__)

WRITE_ATTEMPTS = 4
WRITE_RETRY_SECONDS = 5
PROGRESS_LOG_BATCHES = 5

_model = None
_load_lock = threading.Lock()


class EmbedderNotReady(RuntimeError):
    """Model chua nap xong: tim kiem khong duoc cho vi Spring se het thoi gian truoc."""


def is_ready() -> bool:
    return _model is not None


def load_model():
    global _model
    with _load_lock:
        if _model is None:
            from sentence_transformers import SentenceTransformer

            model = SentenceTransformer(config.EMBEDDING_MODEL, device="cpu")
            dimensions = model.get_sentence_embedding_dimension()
            if dimensions != config.EMBEDDING_DIMENSIONS:
                raise ValueError(f"Model {config.EMBEDDING_MODEL} co {dimensions} chieu, "
                                 f"cau hinh EMBEDDING_DIMENSIONS={config.EMBEDDING_DIMENSIONS}")
            _model = model
            log.info("Da nap model embedding %s (%d chieu)", config.EMBEDDING_MODEL, dimensions)
    return _model


def preload() -> None:
    try:
        load_model()
    except Exception:  # noqa: BLE001 - TF-IDF van phai chay duoc khi khong nap duoc model
        log.exception("Khong nap duoc model embedding, tim kiem vector tam thoi khong dung duoc")


def to_pgvector(vector) -> str:
    return "[" + ",".join(f"{float(x):.6f}" for x in vector) + "]"


def _encode(model, texts: list[str], prefix: str) -> np.ndarray:
    # Chuan hoa L2 de tich vo huong chinh la cosine
    return model.encode([prefix + t for t in texts], batch_size=config.EMBEDDING_BATCH_SIZE,
                        normalize_embeddings=True, show_progress_bar=False)


def _write_batch(params: list[tuple[str, object]]) -> None:
    # Mang chap chon khong duoc lam mat ca luot: thu lai lo nay truoc khi bao loi
    for attempt in range(1, WRITE_ATTEMPTS + 1):
        try:
            with pool.connection() as conn, conn.transaction(), conn.cursor() as cur:
                cur.executemany("UPDATE public.search_document_chunks SET embedding = %s::vector WHERE id = %s", params)
            return
        except psycopg.OperationalError as ex:
            if attempt == WRITE_ATTEMPTS:
                raise
            log.warning("Ghi embedding loi lan %d/%d, thu lai: %s", attempt, WRITE_ATTEMPTS, ex)
            time.sleep(WRITE_RETRY_SECONDS * attempt)


def embed_pending(digital_copy_id: UUID | None = None, force: bool = False) -> int:
    """Sinh embedding cho chunk con thieu (force: sinh lai het, dung khi doi model). Tra ve so chunk da ghi."""
    conditions = ["c.chunk_text IS NOT NULL"]
    params: list = []
    if digital_copy_id is not None:
        conditions.append("c.digital_copy_id = %s")
        params.append(digital_copy_id)
    if not force:
        conditions.append("c.embedding IS NULL")
    with pool.connection() as conn:
        rows = conn.execute(
            f"""SELECT c.id, c.chunk_text
                FROM public.search_document_chunks c
                JOIN public.digital_copies dc ON dc.id = c.digital_copy_id AND dc.is_current = true
                WHERE {' AND '.join(conditions)}
                ORDER BY c.digital_copy_id, c.chunk_index""",
            params,
        ).fetchall()
    if not rows:
        return 0

    model = load_model()
    written = 0
    encode_seconds = write_seconds = 0.0
    # Ghi tung lo voi ket noi rieng: khong giu ket noi khi dang tinh, va ket noi dut chi mat mot lo
    for batch_no, start in enumerate(range(0, len(rows), config.EMBEDDING_BATCH_SIZE), start=1):
        batch = rows[start:start + config.EMBEDDING_BATCH_SIZE]
        started = time.perf_counter()
        vectors = _encode(model, [text for _, text in batch], config.EMBEDDING_PASSAGE_PREFIX)
        encoded = time.perf_counter()
        _write_batch([(to_pgvector(vector), chunk_id) for (chunk_id, _), vector in zip(batch, vectors)])
        encode_seconds += encoded - started
        write_seconds += time.perf_counter() - encoded
        written += len(batch)
        if batch_no % PROGRESS_LOG_BATCHES == 0:
            log.info("Embedding %d/%d chunk (tinh %.0fs, ghi DB %.0fs)", written, len(rows), encode_seconds, write_seconds)
    log.info("Da sinh embedding cho %d chunk (tinh %.0fs, ghi DB %.0fs)", written, encode_seconds, write_seconds)
    return written


def search(text: str, top_k: int, copy_ids: list[str] | None = None) -> list[tuple[str, float]]:
    """Tra ve (chunk_id, cosine) giam dan. copy_ids: chi tim trong cac ban so nay."""
    if _model is None:
        raise EmbedderNotReady(f"Model {config.EMBEDDING_MODEL} chua nap xong")
    query_vector = to_pgvector(_encode(_model, [text], config.EMBEDDING_QUERY_PREFIX)[0])
    copy_filter = "AND c.digital_copy_id = ANY(%(copies)s::uuid[])" if copy_ids is not None else ""
    with pool.connection() as conn:
        rows = conn.execute(
            f"""SELECT c.id::text, 1 - (c.embedding <=> %(query)s::vector)
                FROM public.search_document_chunks c
                JOIN public.digital_copies dc ON dc.id = c.digital_copy_id AND dc.is_current = true
                JOIN public.documents d ON d.id = c.document_id AND d.deleted_at IS NULL
                WHERE c.embedding IS NOT NULL {copy_filter}
                ORDER BY c.embedding <=> %(query)s::vector
                LIMIT %(limit)s""",
            {"query": query_vector, "copies": copy_ids, "limit": top_k},
        ).fetchall()
    return [(chunk_id, float(score)) for chunk_id, score in rows]


def status() -> dict:
    with pool.connection() as conn:
        total, embedded = conn.execute(
            """SELECT count(*), count(c.embedding)
               FROM public.search_document_chunks c
               JOIN public.digital_copies dc ON dc.id = c.digital_copy_id AND dc.is_current = true"""
        ).fetchone()
    return {"model": config.EMBEDDING_MODEL, "dimensions": config.EMBEDDING_DIMENSIONS, "modelLoaded": is_ready(),
            "totalChunks": total, "embeddedChunks": embedded}
