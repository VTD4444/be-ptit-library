"""Điều phối Batch Rebuild EASE và Online Scoring gợi ý 'Dành cho bạn'."""
from __future__ import annotations

import logging
import threading
import time
from collections import defaultdict
from dataclasses import dataclass

from app import config
from app.recommendation import interaction_repo
from app.recommendation.ease_model import EaseHolder, fit_ease

log = logging.getLogger(__name__)

DEFAULT_EASE_REASON = "Phù hợp với các tài liệu bạn đã quan tâm"

_rebuild_lock = threading.Lock()
ease_holder = EaseHolder(directory=config.EASE_DIR)


@dataclass(frozen=True)
class EaseRebuildStats:
    model_version: str
    reg_lambda: float
    users_count: int
    documents_count: int
    positive_pairs: int
    cached_rows: int
    duration_ms: float


@dataclass(frozen=True)
class RecommendedDocItem:
    document_id: str
    score: float
    rank: int
    reason: str
    rec_model_version: str


@dataclass(frozen=True)
class EasePredictResult:
    model_ready: bool
    model_version: str
    personalization_enabled: bool
    items: list[RecommendedDocItem]


def rebuild_ease_model(
    model_version: str = config.EASE_MODEL_VERSION,
    reg_lambda: float = config.EASE_LAMBDA,
    cache_top_k: int = config.EASE_CACHE_TOP_K,
    cache_ttl_hours: int = config.EASE_CACHE_TTL_HOURS,
    only_available: bool = False,
) -> EaseRebuildStats:
    """Train lại ma trận EASE từ interaction_logs, nạp vào RAM và lưu snapshot dự phòng."""
    with _rebuild_lock:
        t0 = time.perf_counter()
        doc_ids = interaction_repo.load_active_document_ids()
        pairs = interaction_repo.load_positive_interactions()
        artifact = fit_ease(
            positive_pairs=pairs,
            doc_ids=doc_ids,
            model_version=model_version,
            reg_lambda=reg_lambda,
        )
        ease_holder.replace(artifact)

        user_pos: dict[str, set[str]] = defaultdict(set)
        for uid, doc_id in pairs:
            user_pos[uid].add(doc_id)

        borrows_by_user = interaction_repo.load_active_borrows_by_user()
        allowed = interaction_repo.load_available_document_ids() if only_available else None

        snapshots: list[tuple[str, str, float, int, str]] = []
        if artifact.is_ready:
            for uid, pos_docs in user_pos.items():
                ranked = artifact.score_user(
                    positive_doc_ids=pos_docs,
                    exclude_doc_ids=borrows_by_user.get(uid),
                    allowed_doc_ids=allowed,
                    limit=cache_top_k,
                )
                for rank, (doc_id, score) in enumerate(ranked, start=1):
                    snapshots.append((uid, doc_id, score, rank, DEFAULT_EASE_REASON))

        cached_rows = interaction_repo.save_ease_snapshots(
            model_version=model_version,
            snapshots=snapshots,
            ttl_hours=cache_ttl_hours,
        )
        elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)
        log.info(
            "Rebuild EASE (%s, lam=%.1f): %d users, %d docs, %d pairs -> %d snapshot rows trong %.1f ms",
            model_version, reg_lambda, len(user_pos), len(doc_ids), len(pairs), cached_rows, elapsed_ms,
        )
        return EaseRebuildStats(
            model_version=model_version,
            reg_lambda=float(reg_lambda),
            users_count=len(user_pos),
            documents_count=len(doc_ids),
            positive_pairs=len(pairs),
            cached_rows=cached_rows,
            duration_ms=elapsed_ms,
        )


def predict_for_user(
    user_id: str,
    limit: int = 10,
    only_available: bool = False,
) -> EasePredictResult:
    """Chấm điểm Online Scoring trực tiếp trên ma trận B đang giữ sẵn trong RAM."""
    artifact = ease_holder.current
    if not artifact.is_ready:
        return EasePredictResult(
            model_ready=False,
            model_version=artifact.model_version,
            personalization_enabled=True,
            items=[],
        )

    ctx = interaction_repo.load_user_interaction_context(user_id, only_available=only_available)
    if not ctx.personalization_enabled or not ctx.positive_doc_ids:
        return EasePredictResult(
            model_ready=True,
            model_version=artifact.model_version,
            personalization_enabled=ctx.personalization_enabled,
            items=[],
        )

    ranked = artifact.score_user(
        positive_doc_ids=ctx.positive_doc_ids,
        exclude_doc_ids=ctx.active_borrow_doc_ids,
        allowed_doc_ids=ctx.allowed_doc_ids,
        limit=limit,
    )
    items = [
        RecommendedDocItem(
            document_id=doc_id,
            score=score,
            rank=rank,
            reason=DEFAULT_EASE_REASON,
            rec_model_version=artifact.model_version,
        )
        for rank, (doc_id, score) in enumerate(ranked, start=1)
    ]
    return EasePredictResult(
        model_ready=True,
        model_version=artifact.model_version,
        personalization_enabled=True,
        items=items,
    )
