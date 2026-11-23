"""
Tinh toan do tuong dong noi dung giua cac dau sach bang TF-IDF Weighted + Cosine Similarity tho,
luu vao bang public.document_similarities.

Tai su dung pipeline tien xu ly + ngrams (app.text_preprocess) va cau hinh TF-IDF (app.tfidf_index).
"""
import logging
import threading
import time
from dataclasses import dataclass

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

from app.db import pool
from app.text_preprocess import ngrams, preprocess

log = logging.getLogger(__name__)

DEFAULT_MODEL_VERSION = "tfidf-v1"
DEFAULT_MIN_SCORE = 0.05
DEFAULT_TOP_K = 20
SOURCE_CONTENT_BASED = "CONTENT_BASED"

_rebuild_lock = threading.Lock()


@dataclass(frozen=True)
class BookTextRow:
    document_id: str
    title: str
    abstract: str
    authors: str


@dataclass(frozen=True)
class RebuildStats:
    model_version: str
    documents_processed: int
    pairs_saved: int
    duration_ms: float


def _field_ngrams(text: str) -> list[str]:
    cleaned = text.strip()
    if not cleaned:
        return []
    return ngrams(preprocess(cleaned))


def build_weighted_features(row: BookTextRow) -> list[str]:
    """Sinh dac trung unigram + bigram doc lap theo truong: Title x2 + Authors x1 + Abstract x1."""
    weighted_fields = (
        (row.title, 2),
        (row.authors, 1),
        (row.abstract, 1),
    )
    features: list[str] = []
    for value, weight in weighted_fields:
        field_feats = _field_ngrams(value)
        if field_feats:
            # Tinh ngrams doc lap tung truong de tranh gop nham tu/bigram qua ranh gioi truong.
            features.extend(field_feats * weight)
    return features


def _identity(features: list[str]) -> list[str]:
    return features


def _fit_tfidf_matrix(corpus: list[list[str]], max_df: float):
    vec = TfidfVectorizer(
        analyzer=_identity,
        min_df=1,
        max_df=max_df,
        sublinear_tf=True,
        smooth_idf=True,
        norm="l2",
    )
    return vec.fit_transform(corpus)


def compute_similar_pairs(
    books: list[BookTextRow],
    min_score: float = DEFAULT_MIN_SCORE,
    top_k: int = DEFAULT_TOP_K,
) -> list[tuple[str, str, float]]:
    """Tra ve danh sach (doc_a_id, doc_b_id, sim_score) da loc theo nguong va gioi han top_k moi sach."""
    n = len(books)
    if n < 2:
        return []

    corpus = [build_weighted_features(b) for b in books]
    if not any(corpus):
        return []

    try:
        tfidf_mat = _fit_tfidf_matrix(corpus, max_df=0.90 if n >= 3 else 1.0)
    except ValueError:
        tfidf_mat = _fit_tfidf_matrix(corpus, max_df=1.0)

    raw_sim = (tfidf_mat @ tfidf_mat.T).toarray()
    np.fill_diagonal(raw_sim, 0.0)

    limit = min(max(1, top_k), n - 1)
    pairs: list[tuple[str, str, float]] = []
    for i, book_a in enumerate(books):
        scores = raw_sim[i]
        top_idx = np.argsort(-scores)[:limit]
        for j in top_idx:
            score = round(float(scores[j]), 4)
            if j != i and score >= min_score:
                pairs.append((book_a.document_id, books[int(j)].document_id, score))
    return pairs


def _load_books_from_db() -> list[BookTextRow]:
    sql = """
        SELECT d.id::text,
               COALESCE(d.title, ''),
               COALESCE(d.abstract, ''),
               COALESCE(STRING_AGG(a.name, ' ' ORDER BY da.author_order), '') AS authors
        FROM public.documents d
        LEFT JOIN public.document_authors da ON da.document_id = d.id
        LEFT JOIN public.authors a ON a.id = da.author_id
        WHERE d.deleted_at IS NULL
        GROUP BY d.id, d.title, d.abstract
        ORDER BY d.id
    """
    with pool.connection() as conn:
        rows = conn.execute(sql).fetchall()

    return [
        BookTextRow(
            document_id=row[0],
            title=row[1],
            abstract=row[2],
            authors=row[3],
        )
        for row in rows
    ]


def _save_pairs_to_db(
    pairs: list[tuple[str, str, float]],
    model_version: str,
) -> None:
    with pool.connection() as conn, conn.transaction():
        conn.execute(
            "DELETE FROM public.document_similarities WHERE model_version = %s",
            (model_version,),
        )
        if pairs:
            with conn.cursor() as cur:
                cur.executemany(
                    """
                    INSERT INTO public.document_similarities
                        (document_a_id, document_b_id, model_version, sim_score, source)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    [(a, b, model_version, sc, SOURCE_CONTENT_BASED) for a, b, sc in pairs],
                )


def rebuild_similarities(
    model_version: str = DEFAULT_MODEL_VERSION,
    min_score: float = DEFAULT_MIN_SCORE,
    top_k: int = DEFAULT_TOP_K,
) -> RebuildStats:
    """Tinh lai toan bo ma tran sach tuong tu TF-IDF Weighted va ghi vao PostgreSQL."""
    with _rebuild_lock:
        t0 = time.perf_counter()
        books = _load_books_from_db()
        pairs = compute_similar_pairs(books, min_score=min_score, top_k=top_k)
        _save_pairs_to_db(pairs, model_version=model_version)
        elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)
        log.info(
            "Da tinh lai sach tuong tu (%s): %d tai lieu -> %d cap (min_score=%.2f) trong %.1f ms",
            model_version, len(books), len(pairs), min_score, elapsed_ms,
        )
        return RebuildStats(
            model_version=model_version,
            documents_processed=len(books),
            pairs_saved=len(pairs),
            duration_ms=elapsed_ms,
        )
