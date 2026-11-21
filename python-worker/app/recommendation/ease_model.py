"""Thuật toán EASE (Embarrassingly Shallow Autoencoders) cho gợi ý cá nhân hóa trên RAM."""
from __future__ import annotations

import json
import logging
import shutil
import threading
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

log = logging.getLogger(__name__)


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class EaseArtifact:
    """Giữ ma trận trọng số sách-sách B (M x M) và ánh xạ document_id."""

    def __init__(
        self,
        model_version: str,
        reg_lambda: float,
        doc_ids: list[str],
        b_matrix: np.ndarray,
        built_at: str,
    ):
        self.model_version = model_version
        self.reg_lambda = float(reg_lambda)
        self.doc_ids = list(doc_ids)
        self.b_matrix = np.asarray(b_matrix, dtype=np.float64)
        self.built_at = built_at
        self.doc_index: dict[str, int] = {doc_id: idx for idx, doc_id in enumerate(self.doc_ids)}

    @property
    def is_ready(self) -> bool:
        n = len(self.doc_ids)
        return n >= 2 and self.b_matrix.shape == (n, n)

    def score_user(
        self,
        positive_doc_ids: set[str],
        exclude_doc_ids: set[str] | None = None,
        allowed_doc_ids: set[str] | None = None,
        limit: int = 10,
    ) -> list[tuple[str, float]]:
        """Online scoring: s(u) = sum_{i in I_u+} B[i, :], bỏ sách đã thích/đang mượn."""
        if not self.is_ready or not positive_doc_ids or limit <= 0:
            return []

        pos_indices = [self.doc_index[d] for d in positive_doc_ids if d in self.doc_index]
        if not pos_indices:
            return []

        scores = self.b_matrix[pos_indices].sum(axis=0).astype(np.float64, copy=True)

        excluded = set(positive_doc_ids)
        if exclude_doc_ids:
            excluded.update(exclude_doc_ids)
        for doc_id in excluded:
            idx = self.doc_index.get(doc_id)
            if idx is not None:
                scores[idx] = -np.inf

        if allowed_doc_ids is not None:
            allowed_mask = np.zeros(len(self.doc_ids), dtype=bool)
            for doc_id in allowed_doc_ids:
                idx = self.doc_index.get(doc_id)
                if idx is not None:
                    allowed_mask[idx] = True
            scores[~allowed_mask] = -np.inf

        # Chỉ giữ các tài liệu có điểm liên kết dương > 0
        candidates = np.flatnonzero(np.isfinite(scores) & (scores > 0.0))
        if candidates.size == 0:
            return []

        top_n = min(limit, int(candidates.size))
        if candidates.size > top_n:
            candidates = candidates[np.argpartition(-scores[candidates], top_n - 1)[:top_n]]
        candidates = candidates[np.argsort(-scores[candidates])]

        return [(self.doc_ids[int(idx)], round(float(scores[idx]), 4)) for idx in candidates]

    def save(self, directory: Path) -> None:
        tmp = directory.with_name(directory.name + ".tmp")
        shutil.rmtree(tmp, ignore_errors=True)
        tmp.mkdir(parents=True, exist_ok=True)
        np.save(tmp / "b_matrix.npy", self.b_matrix)
        (tmp / "meta.json").write_text(
            json.dumps(
                {
                    "modelVersion": self.model_version,
                    "regLambda": self.reg_lambda,
                    "docIds": self.doc_ids,
                    "builtAt": self.built_at,
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        shutil.rmtree(directory, ignore_errors=True)
        tmp.rename(directory)

    @classmethod
    def load(cls, directory: Path) -> "EaseArtifact":
        meta = json.loads((directory / "meta.json").read_text(encoding="utf-8"))
        b_matrix = np.load(directory / "b_matrix.npy")
        return cls(
            model_version=meta["modelVersion"],
            reg_lambda=float(meta["regLambda"]),
            doc_ids=list(meta["docIds"]),
            b_matrix=b_matrix,
            built_at=meta["builtAt"],
        )

    @classmethod
    def empty(cls, model_version: str = "ease-v1", reg_lambda: float = 500.0) -> "EaseArtifact":
        return cls(model_version, reg_lambda, [], np.zeros((0, 0), dtype=np.float64), _utc_now_iso())


def fit_ease(
    positive_pairs: list[tuple[str, str]],
    doc_ids: list[str],
    model_version: str = "ease-v1",
    reg_lambda: float = 500.0,
) -> EaseArtifact:
    """Huấn luyện ma trận EASE B theo nghiệm đóng P = (R^T R + λI)^-1, B_ij = -P_ij / P_jj."""
    if reg_lambda <= 0:
        raise ValueError("reg_lambda phai lon hon 0")

    unique_docs = list(dict.fromkeys(doc_ids))
    if len(unique_docs) < 2 or not positive_pairs:
        return EaseArtifact.empty(model_version=model_version, reg_lambda=reg_lambda)

    doc_index = {d: idx for idx, d in enumerate(unique_docs)}
    user_index: dict[str, int] = {}
    filtered_coords: list[tuple[int, int]] = []
    for user_id, doc_id in positive_pairs:
        col = doc_index.get(doc_id)
        if col is None:
            continue
        row = user_index.setdefault(user_id, len(user_index))
        filtered_coords.append((row, col))

    if not filtered_coords:
        return EaseArtifact.empty(model_version=model_version, reg_lambda=reg_lambda)

    R = np.zeros((len(user_index), len(unique_docs)), dtype=np.float64)
    rows_idx, cols_idx = zip(*filtered_coords)
    R[rows_idx, cols_idx] = 1.0

    gram = R.T @ R
    diag_idx = np.diag_indices(len(unique_docs))
    gram[diag_idx] += float(reg_lambda)

    precision = np.linalg.inv(gram)
    b_matrix = -precision / np.diag(precision)
    b_matrix[diag_idx] = 0.0

    return EaseArtifact(
        model_version=model_version,
        reg_lambda=float(reg_lambda),
        doc_ids=unique_docs,
        b_matrix=b_matrix,
        built_at=_utc_now_iso(),
    )


class EaseHolder:
    """Giữ artifact EASE trên RAM, cho phép swap nguyên khối khi rebuild."""

    def __init__(self, directory: Path | None = None):
        self._artifact = EaseArtifact.empty()
        self._lock = threading.Lock()
        self._directory = directory

    @property
    def current(self) -> EaseArtifact:
        return self._artifact

    def load_if_exists(self, directory: Path | None = None) -> bool:
        target = directory or self._directory
        if target is None or not (target / "meta.json").exists() or not (target / "b_matrix.npy").exists():
            log.info("Chua co artifact EASE tren dia (%s), doi rebuild", target)
            return False
        artifact = EaseArtifact.load(target)
        with self._lock:
            self._artifact = artifact
        log.info("Da nap mo hinh EASE %s: %d tai lieu", artifact.model_version, len(artifact.doc_ids))
        return True

    def replace(self, artifact: EaseArtifact, persist_dir: Path | None = None) -> None:
        target = persist_dir or self._directory
        with self._lock:
            if target is not None:
                artifact.save(target)
            self._artifact = artifact
