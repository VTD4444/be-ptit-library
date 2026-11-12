import os
import struct
from contextlib import contextmanager

import numpy as np
import pytest

os.environ.setdefault("DATABASE_URL", "postgresql://unused")

from app import config, vector_store  # noqa: E402

DIM = 3


def _send(vector) -> bytes:
    # Dinh dang nhi phan cua pgvector: int16 so chieu, int16 du phong, float32 big-endian
    return struct.pack(">hh", len(vector), 0) + struct.pack(f">{len(vector)}f", *vector)


class _FakeDb:
    """current: [(chunk_id, copy_id)] dang tim duoc; vectors: chunk_id -> vector tren DB."""

    def __init__(self, current, vectors):
        self.current = current
        self.vectors = vectors
        self.fetched: list[str] = []

    @contextmanager
    def connection(self):
        yield self

    def execute(self, sql, params=None):
        db = self

        class _Result:
            def fetchall(self_inner):
                if "vector_send" in sql:
                    ids = params[0]
                    db.fetched.extend(ids)
                    return [(i, _send(db.vectors[i])) for i in ids if i in db.vectors]
                return list(db.current)

        return _Result()


@pytest.fixture
def store(monkeypatch, tmp_path):
    monkeypatch.setattr(config, "DATA_DIR", tmp_path)
    monkeypatch.setattr(config, "EMBEDDING_DIMENSIONS", DIM)
    monkeypatch.setattr(vector_store, "_store", None)

    def use(db):
        monkeypatch.setattr(vector_store, "pool", db)
        return db

    return use


def _unit(*values):
    v = np.array(values, dtype=np.float32)
    return v / np.linalg.norm(v)


def test_search_before_load_is_not_ready(store):
    with pytest.raises(vector_store.VectorStoreNotReady):
        vector_store.search(_unit(1, 0, 0), 5)


def test_sync_then_search_ranks_by_cosine_and_filters_copies(store):
    store(_FakeDb([("a", "c1"), ("b", "c1"), ("c", "c2")],
                  {"a": _unit(1, 0, 0), "b": _unit(1, 1, 0), "c": _unit(0, 1, 0)}))
    vector_store.sync()

    hits = vector_store.search(_unit(1, 0, 0), 2)
    assert [h[0] for h in hits] == ["a", "b"]
    assert hits[0][1] == pytest.approx(1.0, abs=1e-6)

    assert [h[0] for h in vector_store.search(_unit(1, 0, 0), 5, ["c2"])] == ["c"]
    assert vector_store.search(_unit(1, 0, 0), 5, ["khong-co"]) == []


def test_sync_fetches_only_new_chunks_and_drops_removed(store):
    db = store(_FakeDb([("a", "c1"), ("b", "c1")], {"a": _unit(1, 0, 0), "b": _unit(0, 1, 0)}))
    vector_store.sync()
    assert sorted(db.fetched) == ["a", "b"]

    db.fetched.clear()
    db.current = [("b", "c1"), ("d", "c3")]  # a bi xoa, d moi
    db.vectors["d"] = _unit(0, 0, 1)
    vector_store.sync()

    assert db.fetched == ["d"]
    assert vector_store.size() == 2
    assert vector_store.search(_unit(0, 0, 1), 1)[0][0] == "d"


def test_restart_reuses_disk_cache(store, monkeypatch):
    db = store(_FakeDb([("a", "c1"), ("b", "c1")], {"a": _unit(1, 0, 0), "b": _unit(0, 1, 0)}))
    vector_store.sync()
    assert (config.DATA_DIR / "vectors" / vector_store.CACHE_FILE).exists()

    monkeypatch.setattr(vector_store, "_store", None)  # gia lap khoi dong lai
    db.fetched.clear()
    vector_store.sync()

    assert db.fetched == []
    assert vector_store.search(_unit(0, 1, 0), 1)[0][0] == "b"


def test_cache_from_other_model_is_ignored(store, monkeypatch):
    db = store(_FakeDb([("a", "c1")], {"a": _unit(1, 0, 0)}))
    vector_store.sync()
    monkeypatch.setattr(config, "EMBEDDING_MODEL", "model-khac")
    monkeypatch.setattr(vector_store, "_store", None)
    db.fetched.clear()

    vector_store.sync()

    assert db.fetched == ["a"]


def test_put_updates_and_appends_without_db(store):
    store(_FakeDb([("a", "c1")], {"a": _unit(1, 0, 0)}))
    vector_store.sync()

    vector_store.put([("a", "c1", _unit(0, 1, 0)), ("n", "c9", _unit(0, 0, 1))])

    assert vector_store.size() == 2
    assert vector_store.search(_unit(0, 1, 0), 1)[0][0] == "a"
    assert vector_store.search(_unit(0, 0, 1), 5, ["c9"])[0][0] == "n"
