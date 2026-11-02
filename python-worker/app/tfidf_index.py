"""
Mo hinh TF-IDF + do tuong dong cosine (XLVB muc 1.5 - 1.9) va inverted file muc tai lieu (Lecture 8).

Moi chunk trong search_document_chunks la mot "van ban" d_j. Thu muc MODEL_DIR luu:
  vocabulary.json  - tu dien V (dac trung -> chi so cot)
  idf.npy          - vector IDF, dung lai cho truy van
  matrix.npz       - ma tran dac trung X (N x V) da chuan hoa L2, luu dang ma tran thua
  documents.json   - danh sach van ban: hang -> chunk_id, ban so (copy) chua chunk
  inverted.json    - inverted file: term -> danh sach ban so chua term (dung cho loc Boolean)
Vi tri chi tiet cua term (doan, cau, tu) nam o copy_store (positional index theo tung ban so).
"""
import json
import logging
import math
import shutil
import threading
from collections import Counter, defaultdict
from datetime import datetime, timezone

import numpy as np
from scipy import sparse
from sklearn.feature_extraction.text import TfidfVectorizer

from app import config, copy_store
from app.db import pool
from app.synonyms import canonical_terms
from app.text_preprocess import analyze, ngrams, preprocess

log = logging.getLogger(__name__)


class TfidfIndex:
    def __init__(self, vocabulary: dict[str, int], idf: np.ndarray, matrix: sparse.csr_matrix,
                 chunk_ids: list[str], chunk_copy: list[int], copies: list[dict],
                 inverted: dict[str, list[int]], built_at: str):
        self.vocabulary = vocabulary
        self.idf = idf
        self.matrix = matrix
        self.chunk_ids = chunk_ids
        self.chunk_copy = np.array(chunk_copy, dtype=np.int64)
        self.copies = copies  # [{"copyId", "documentId"}]
        self.inverted = inverted
        self.built_at = built_at
        self.copy_index = {c["copyId"]: i for i, c in enumerate(copies)}
        self.document_copies: dict[str, list[int]] = defaultdict(list)
        for i, c in enumerate(copies):
            self.document_copies[c["documentId"]].append(i)

    @property
    def size(self) -> int:
        return len(self.chunk_ids)

    def postings(self, term: str) -> set[int]:
        return set(self.inverted.get(term, ()))

    def copies_of_documents(self, document_ids: set[str]) -> set[int]:
        return {i for d in document_ids for i in self.document_copies.get(d, ())}

    def vectorize_query(self, text: str) -> sparse.csr_matrix:
        """XLVB muc 1.9 buoc 2: dem tu co trong V (bo qua tu ngoai tu dien) -> sublinear TF -> nhan IDF -> chuan hoa L2."""
        counts = Counter(f for f in analyze(text) if f in self.vocabulary)
        cols = np.array([self.vocabulary[f] for f in counts], dtype=np.int64)
        values = np.array([1 + math.log(tf) for tf in counts.values()], dtype=np.float64) * self.idf[cols]
        norm = np.linalg.norm(values)
        if norm > 0:
            values /= norm
        return sparse.csr_matrix((values, (np.zeros(len(cols), dtype=np.int64), cols)),
                                 shape=(1, len(self.vocabulary)))

    def search_chunks(self, text: str, top_k: int, allowed_copies: set[int] | None = None) -> list[tuple[int, float]]:
        """Tra ve (hang chunk, cosine) giam dan. allowed_copies: chi cham diem chunk cua cac ban so nay."""
        if self.size == 0 or (allowed_copies is not None and not allowed_copies):
            return []
        q = self.vectorize_query(text)
        if q.nnz == 0:
            return []
        # XLVB muc 1.9 buoc 3: s = X q^T (X va q deu da chuan hoa L2 nen tich vo huong chinh la cosine)
        scores = np.asarray((self.matrix @ q.T).todense()).ravel()
        if allowed_copies is not None:
            scores = np.where(np.isin(self.chunk_copy, list(allowed_copies)), scores, 0.0)

        # XLVB muc 1.9 buoc 4: lay top-n theo diem giam dan
        candidates = np.flatnonzero(scores > 0)
        if candidates.size > top_k:
            candidates = candidates[np.argpartition(-scores[candidates], top_k - 1)[:top_k]]
        candidates = candidates[np.argsort(-scores[candidates])]
        return [(int(i), float(scores[i])) for i in candidates]

    def save(self, directory) -> None:
        tmp = directory.with_name(directory.name + ".tmp")
        shutil.rmtree(tmp, ignore_errors=True)
        tmp.mkdir(parents=True)
        (tmp / "vocabulary.json").write_text(json.dumps(self.vocabulary, ensure_ascii=False), encoding="utf-8")
        np.save(tmp / "idf.npy", self.idf)
        sparse.save_npz(tmp / "matrix.npz", self.matrix)
        (tmp / "documents.json").write_text(json.dumps({
            "builtAt": self.built_at,
            "chunkIds": self.chunk_ids,
            "chunkCopy": self.chunk_copy.tolist(),
            "copies": self.copies,
        }), encoding="utf-8")
        (tmp / "inverted.json").write_text(json.dumps(self.inverted, ensure_ascii=False), encoding="utf-8")
        # Thay the nguyen thu muc de khong bao gio doc phai mo hinh ghi do dang
        shutil.rmtree(directory, ignore_errors=True)
        tmp.rename(directory)

    @classmethod
    def load(cls, directory) -> "TfidfIndex":
        documents = json.loads((directory / "documents.json").read_text(encoding="utf-8"))
        return cls(
            vocabulary=json.loads((directory / "vocabulary.json").read_text(encoding="utf-8")),
            idf=np.load(directory / "idf.npy"),
            matrix=sparse.load_npz(directory / "matrix.npz").tocsr(),
            chunk_ids=documents["chunkIds"],
            chunk_copy=documents["chunkCopy"],
            copies=documents["copies"],
            inverted=json.loads((directory / "inverted.json").read_text(encoding="utf-8")),
            built_at=documents["builtAt"],
        )

    @classmethod
    def empty(cls) -> "TfidfIndex":
        return cls({}, np.zeros(0), sparse.csr_matrix((0, 0)), [], [], [], {}, _now())


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _fit(token_lists: list[list[str]]) -> TfidfVectorizer:
    """Fit tren danh sach tu da tien xu ly; analyzer chi con gom dong nghia + ghep unigram/bigram."""
    def make(min_df, max_df):
        # sublinear_tf: TF' = 1 + ln(TF) (muc 1.6.1); smooth_idf: IDF = ln((1+|D|)/(1+DF)) + 1 (muc 1.5);
        # norm l2 (muc 1.6.2); min_df / max_df / max_features: cat tia tu dien (muc 1.4)
        return TfidfVectorizer(analyzer=ngrams, sublinear_tf=True, smooth_idf=True, norm="l2",
                               min_df=min_df, max_df=max_df, max_features=config.TFIDF_MAX_FEATURES)

    vectorizer = make(config.TFIDF_MIN_DF, config.TFIDF_MAX_DF)
    try:
        vectorizer.fit(token_lists)
    except ValueError:
        # Kho con qua nho (vd: moi co 1-2 tai lieu) thi nguong min_df/max_df loai het tu: tam bo cat tia
        log.warning("Kho van ban qua nho cho min_df=%s/max_df=%s, fit lai khong cat tia",
                    config.TFIDF_MIN_DF, config.TFIDF_MAX_DF)
        vectorizer = make(1, 1.0)
        vectorizer.fit(token_lists)
    return vectorizer


def build_from_db(search_config_id: int | None) -> TfidfIndex:
    """Lap chi muc lai tren toan bo chunk cua cac ban so hien hanh (tu dien va IDF phu thuoc ca kho)."""
    with pool.connection() as conn:
        rows = conn.execute(
            """SELECT c.id::text, c.document_id::text, c.digital_copy_id::text, c.chunk_text
               FROM public.search_document_chunks c
               JOIN public.digital_copies dc ON dc.id = c.digital_copy_id AND dc.is_current = true
               JOIN public.documents d ON d.id = c.document_id AND d.deleted_at IS NULL
               WHERE c.search_config_id IS NOT DISTINCT FROM %s AND c.chunk_text IS NOT NULL
               ORDER BY c.digital_copy_id, c.chunk_index""",
            (search_config_id,),
        ).fetchall()
    if not rows:
        return TfidfIndex.empty()

    copies: list[dict] = []
    copy_index: dict[str, int] = {}
    chunk_ids: list[str] = []
    chunk_copy: list[int] = []
    token_lists: list[list[str]] = []
    inverted: dict[str, set[int]] = defaultdict(set)
    loaded: dict[str, tuple[copy_store.CopyData | None, dict[str, int]]] = {}

    for chunk_id, document_id, copy_id, chunk_text in rows:
        if copy_id not in copy_index:
            copy_index[copy_id] = len(copies)
            copies.append({"copyId": copy_id, "documentId": document_id})
            data = copy_store.load(copy_id)
            loaded[copy_id] = (data, {cid: i for i, cid in enumerate(data.chunk_ids)} if data else {})
            if data is not None:
                for term in canonical_terms(data.terms):
                    inverted[term].add(copy_index[copy_id])
        data, chunk_positions = loaded[copy_id]
        tokens = None
        if chunk_id in chunk_positions:
            # Lay token tu positional index: khong phai tach tu lai
            i = chunk_positions[chunk_id]
            tokens = [data.terms[t] for t in data.token_range(data.chunk_starts[i], data.chunk_ends[i])]
        if tokens is None:
            # Ban so chua co du lieu vi tri (vd: boc tach tren may khac): tien xu ly lai tu chunk_text
            tokens = preprocess(chunk_text)
            for term in canonical_terms(tokens):
                inverted[term].add(copy_index[copy_id])
        chunk_ids.append(chunk_id)
        chunk_copy.append(copy_index[copy_id])
        token_lists.append(tokens)

    vectorizer = _fit(token_lists)
    matrix = vectorizer.transform(token_lists).tocsr()
    vocabulary = {term: int(col) for term, col in vectorizer.vocabulary_.items()}
    return TfidfIndex(vocabulary, vectorizer.idf_, matrix, chunk_ids, chunk_copy, copies,
                      {t: sorted(s) for t, s in inverted.items()}, _now())


class IndexHolder:
    """Giu mo hinh dang dung; rebuild tao doi tuong moi roi doi tham chieu nen request tim kiem khong bi chan."""

    def __init__(self):
        self._index = TfidfIndex.empty()
        self._rebuild_lock = threading.Lock()

    @property
    def current(self) -> TfidfIndex:
        return self._index

    def load_or_build(self, search_config_id: int | None) -> None:
        if (config.MODEL_DIR / "inverted.json").exists():
            self._index = TfidfIndex.load(config.MODEL_DIR)
            log.info("Da nap mo hinh TF-IDF: %d van ban, %d dac trung", self._index.size, len(self._index.vocabulary))
        else:
            self.rebuild(search_config_id)

    def rebuild(self, search_config_id: int | None) -> TfidfIndex:
        with self._rebuild_lock:
            index = build_from_db(search_config_id)
            index.save(config.MODEL_DIR)
            self._index = index
            log.info("Da lap chi muc TF-IDF: %d van ban, %d dac trung", index.size, len(index.vocabulary))
            return index


holder = IndexHolder()
