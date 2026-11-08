import logging
import uuid
from dataclasses import dataclass
from itertools import groupby
from pathlib import Path
from uuid import UUID

from app import config, copy_store, embedder
from app.chunker import chunk_words
from app.db import pool
from app.pdf_extract import ExtractionResult, extract
from app.text_preprocess import tokenize_words
from app.tfidf_index import holder

log = logging.getLogger(__name__)


@dataclass
class IndexRequest:
    background_job_id: int
    digital_copy_id: UUID
    document_id: UUID
    file_path: str
    search_config_id: int | None


@dataclass
class ChunkConfig:
    chunk_size: int
    overlap: int


def load_chunk_config(config_id: int | None) -> ChunkConfig:
    row = None
    if config_id is not None:
        with pool.connection() as conn:
            row = conn.execute(
                "SELECT chunk_size, overlap FROM public.search_vector_configs WHERE id = %s",
                (config_id,),
            ).fetchone()
    chunk_size, overlap = row or (None, None)
    return ChunkConfig(
        chunk_size=chunk_size or config.DEFAULT_CHUNK_SIZE,
        overlap=overlap if overlap is not None else config.DEFAULT_OVERLAP,
    )


def active_search_config_id() -> int | None:
    with pool.connection() as conn:
        row = conn.execute("SELECT id FROM public.search_vector_configs WHERE is_active = true LIMIT 1").fetchone()
    return row[0] if row else None


def _resolve_pdf(file_path: str) -> Path:
    path = Path(file_path).resolve()
    if config.UPLOAD_ROOT is not None and not path.is_relative_to(config.UPLOAD_ROOT):
        raise ValueError("Đường dẫn tệp nằm ngoài thư mục lưu trữ cho phép")
    if not path.is_file():
        raise FileNotFoundError(f"Không tìm thấy tệp PDF: {path}")
    return path


def build_positional_index(result: ExtractionResult) -> tuple[list[str], list[int], list[int]]:
    """Tach tu theo tung cau (pyvi can ngu canh cau) -> token kem khoang tu goc tren toan tai lieu."""
    terms, starts, ends = [], [], []
    position = 0
    for _, sentence_words in groupby(result.words, key=lambda w: w.sentence):
        texts = [w.text for w in sentence_words]
        for token in tokenize_words(texts):
            terms.append(token.term)
            starts.append(position + token.start)
            ends.append(position + token.end)
        position += len(texts)
    return terms, starts, ends


def _mark_running(req: IndexRequest) -> None:
    with pool.connection() as conn, conn.transaction():
        conn.execute(
            """UPDATE public.background_jobs
               SET status = 'RUNNING', started_at = now(), finished_at = NULL, error_message = NULL,
                   attempts = attempts + 1, updated_at = now()
               WHERE id = %s""",
            (req.background_job_id,),
        )
        conn.execute(
            "UPDATE public.digital_copies SET processing_status = 'PROCESSING', updated_at = now() WHERE id = %s",
            (req.digital_copy_id,),
        )


def _mark_failed(req: IndexRequest, message: str) -> None:
    with pool.connection() as conn, conn.transaction():
        conn.execute(
            """UPDATE public.background_jobs
               SET status = 'FAILED', error_message = %s, finished_at = now(), updated_at = now()
               WHERE id = %s""",
            (message[:2000], req.background_job_id),
        )
        conn.execute(
            "UPDATE public.digital_copies SET processing_status = 'FAILED', updated_at = now() WHERE id = %s",
            (req.digital_copy_id,),
        )


def run_indexing(req: IndexRequest) -> None:
    log.info("Bat dau boc tach job=%s digital_copy=%s", req.background_job_id, req.digital_copy_id)
    try:
        _mark_running(req)
        pdf_path = _resolve_pdf(req.file_path)
        cfg = load_chunk_config(req.search_config_id)

        result = extract(str(pdf_path))
        terms, token_starts, token_ends = build_positional_index(result)
        chunks = chunk_words(result.words, result.headings, cfg.chunk_size, cfg.overlap)
        chunk_ids = [str(uuid.uuid4()) for _ in chunks]

        # 1) Du lieu vi tri (dong tu + positional index + khoang cua tung chunk) cho ban so nay
        copy_store.save(copy_store.CopyData(
            copy_id=str(req.digital_copy_id),
            document_id=str(req.document_id),
            words=[w.text for w in result.words],
            pages=[w.page for w in result.words],
            paragraphs=[w.paragraph for w in result.words],
            sentences=[w.sentence for w in result.words],
            lines=[w.line for w in result.words],
            bboxes=[list(w.bbox) for w in result.words],
            heading_starts=[h.start for h in result.headings],
            heading_titles=[(h.chapter, h.section) for h in result.headings],
            page_sizes=[list(s) for s in result.page_sizes],
            page_labels=result.page_labels,
            terms=terms,
            token_starts=token_starts,
            token_ends=token_ends,
            chunk_ids=chunk_ids,
            chunk_starts=[c.start for c in chunks],
            chunk_ends=[c.end for c in chunks],
        ))

        # 2) Luu cac phan van ban (moi phan la mot van ban d_j cua mo hinh TF-IDF)
        with pool.connection() as conn, conn.transaction():
            # Xoa ket qua cu de chay lai job khong bi trung chunk
            conn.execute(
                """DELETE FROM public.search_document_chunks
                   WHERE digital_copy_id = %s AND search_config_id IS NOT DISTINCT FROM %s""",
                (req.digital_copy_id, req.search_config_id),
            )
            with conn.cursor() as cur:
                cur.executemany(
                    """INSERT INTO public.search_document_chunks
                       (id, digital_copy_id, document_id, search_config_id, chunk_index, chapter, section,
                        page_number, page_end, printed_page_label, chunk_text, token_count, fts_tokens, created_at)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, to_tsvector('simple', %s), now())""",
                    [
                        (
                            chunk_id, req.digital_copy_id, req.document_id, req.search_config_id, c.index,
                            c.chapter, c.section, c.page_number, c.page_end,
                            result.page_labels.get(c.page_number), c.text, c.token_count, c.text,
                        )
                        for chunk_id, c in zip(chunk_ids, chunks)
                    ],
                )

        # 3) Tu dien va IDF phu thuoc toan bo kho => lap chi muc TF-IDF lai tren tat ca chunk
        index = holder.rebuild(req.search_config_id)

        # 4) Embedding loi khong lam hong job: tai lieu van tim duoc bang TF-IDF, sinh bu sau qua /api/embeddings/backfill
        embedding_failed = False
        try:
            embedder.embed_pending(req.digital_copy_id)
        except Exception:  # noqa: BLE001
            log.exception("Khong sinh duoc embedding cho ban so %s", req.digital_copy_id)
            embedding_failed = True

        low_quality = not chunks or result.quality < config.LOW_QUALITY_THRESHOLD
        status = "LOW_QUALITY" if low_quality else "INDEXED"
        note = None
        if not chunks:
            note = "Không trích xuất được văn bản (có thể là PDF scan, chưa hỗ trợ OCR)"
        elif low_quality:
            note = f"Chỉ {result.quality:.0%} số trang có văn bản trích xuất được"
        elif embedding_failed:
            note = "Chưa sinh được embedding, tài liệu tạm thời chỉ tìm được bằng TF-IDF"

        with pool.connection() as conn, conn.transaction():
            conn.execute(
                """UPDATE public.digital_copies
                   SET processing_status = %s, page_count = %s, extraction_quality = %s, updated_at = now()
                   WHERE id = %s""",
                (status, result.page_count, result.quality, req.digital_copy_id),
            )
            conn.execute(
                """UPDATE public.background_jobs
                   SET status = 'COMPLETED', error_message = %s, finished_at = now(), updated_at = now()
                   WHERE id = %s""",
                (note, req.background_job_id),
            )
        log.info("Hoan tat job=%s: %d trang, %d tu, %d token, %d chunk, trang thai %s; kho %d chunk / %d dac trung",
                 req.background_job_id, result.page_count, len(result.words), len(terms), len(chunks), status,
                 index.size, len(index.vocabulary))
    except Exception as ex:  # noqa: BLE001 - moi loi deu phai ghi nhan vao job
        log.exception("Job %s that bai", req.background_job_id)
        try:
            _mark_failed(req, f"{type(ex).__name__}: {ex}")
        except Exception:  # noqa: BLE001
            log.exception("Khong cap nhat duoc trang thai FAILED cho job %s", req.background_job_id)
