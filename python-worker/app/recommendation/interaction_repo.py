"""Truy vấn PostgreSQL phục vụ huấn luyện EASE, Online Scoring và lưu Snapshot Cache."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from app.db import pool

POSITIVE_WEIGHT_THRESHOLD = 3.0
EASE_ALGORITHM_TYPE = "EASE"


@dataclass(frozen=True)
class UserInteractionContext:
    personalization_enabled: bool
    positive_doc_ids: set[str]
    active_borrow_doc_ids: set[str]
    allowed_doc_ids: set[str] | None


def load_active_document_ids() -> list[str]:
    sql = """
        SELECT id::text
        FROM public.documents
        WHERE deleted_at IS NULL
        ORDER BY id
    """
    with pool.connection() as conn:
        rows = conn.execute(sql).fetchall()
    return [r[0] for r in rows]


def load_positive_interactions(min_weight: float = POSITIVE_WEIGHT_THRESHOLD) -> list[tuple[str, str]]:
    """Lấy các cặp (user_id, document_id) có tương tác tích cực (r_ui >= 3.0)."""
    sql = """
        SELECT DISTINCT il.user_id::text, il.document_id::text
        FROM public.interaction_logs il
        JOIN public.documents d ON d.id = il.document_id AND d.deleted_at IS NULL
        WHERE il.implicit_weight >= %s
    """
    with pool.connection() as conn:
        rows = conn.execute(sql, (min_weight,)).fetchall()
    return [(r[0], r[1]) for r in rows]


def load_active_borrows_by_user() -> dict[str, set[str]]:
    sql = """
        SELECT c.user_id::text, pc.document_id::text
        FROM public.circulation c
        JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
        WHERE c.return_date IS NULL
    """
    with pool.connection() as conn:
        rows = conn.execute(sql).fetchall()
    result: dict[str, set[str]] = {}
    for user_id, doc_id in rows:
        result.setdefault(user_id, set()).add(doc_id)
    return result


def load_available_document_ids(conn=None) -> set[str]:
    """Khả dụng: có bản giấy AVAILABLE (chưa xóa) HOẶC bản số hiện hành INDEXED."""
    sql = """
        SELECT d.id::text
        FROM public.documents d
        WHERE d.deleted_at IS NULL
          AND (
              EXISTS (
                  SELECT 1 FROM public.physical_copies pc
                  WHERE pc.document_id = d.id AND pc.deleted_at IS NULL AND pc.status = 'AVAILABLE'
              )
              OR EXISTS (
                  SELECT 1 FROM public.digital_copies dc
                  WHERE dc.document_id = d.id AND dc.is_current = true AND dc.processing_status = 'INDEXED'
              )
          )
    """
    if conn is not None:
        return {r[0] for r in conn.execute(sql).fetchall()}
    with pool.connection() as new_conn:
        return {r[0] for r in new_conn.execute(sql).fetchall()}


def load_user_interaction_context(user_id: str, only_available: bool = False) -> UserInteractionContext:
    """Đọc cấu hình cá nhân hóa, lịch sử I_u+ và sách đang mượn của riêng 1 user."""
    with pool.connection() as conn:
        prof_row = conn.execute(
            "SELECT personalization_enabled FROM public.user_profiles WHERE user_id = %s",
            (user_id,),
        ).fetchone()
        personalization_enabled = bool(prof_row[0]) if prof_row is not None else True

        pos_rows = conn.execute(
            """
            SELECT DISTINCT il.document_id::text
            FROM public.interaction_logs il
            JOIN public.documents d ON d.id = il.document_id AND d.deleted_at IS NULL
            WHERE il.user_id = %s AND il.implicit_weight >= %s
            """,
            (user_id, POSITIVE_WEIGHT_THRESHOLD),
        ).fetchall()

        borrow_rows = conn.execute(
            """
            SELECT DISTINCT pc.document_id::text
            FROM public.circulation c
            JOIN public.physical_copies pc ON pc.id = c.physical_copy_id
            WHERE c.user_id = %s AND c.return_date IS NULL
            """,
            (user_id,),
        ).fetchall()

        allowed_doc_ids = load_available_document_ids(conn) if only_available else None

    return UserInteractionContext(
        personalization_enabled=personalization_enabled,
        positive_doc_ids={r[0] for r in pos_rows},
        active_borrow_doc_ids={r[0] for r in borrow_rows},
        allowed_doc_ids=allowed_doc_ids,
    )


def save_ease_snapshots(
    model_version: str,
    snapshots: list[tuple[str, str, float, int, str]],
    ttl_hours: int,
) -> int:
    """Đăng ký phiên bản EASE vào rec_algorithm_registry và đổ snapshot vào recommendations_cache."""
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(hours=max(1, ttl_hours))

    with pool.connection() as conn, conn.transaction():
        conn.execute(
            """
            UPDATE public.rec_algorithm_registry
            SET is_active = false
            WHERE algorithm_type = %s AND rec_model_version <> %s
            """,
            (EASE_ALGORITHM_TYPE, model_version),
        )
        conn.execute(
            """
            INSERT INTO public.rec_algorithm_registry (rec_model_version, algorithm_type, is_active)
            VALUES (%s, %s, true)
            ON CONFLICT (rec_model_version) DO UPDATE
            SET algorithm_type = EXCLUDED.algorithm_type, is_active = true
            """,
            (model_version, EASE_ALGORITHM_TYPE),
        )
        conn.execute(
            "DELETE FROM public.recommendations_cache WHERE rec_model_version = %s",
            (model_version,),
        )
        if snapshots:
            with conn.cursor() as cur:
                cur.executemany(
                    """
                    INSERT INTO public.recommendations_cache
                        (user_id, document_id, rec_model_version, score, rank, reason, generated_at, expires_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    [
                        (uid, doc_id, model_version, score, rank, reason, now, expires_at)
                        for uid, doc_id, score, rank, reason in snapshots
                    ],
                )
    return len(snapshots)
