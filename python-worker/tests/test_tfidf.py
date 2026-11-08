import math
import os
from unittest.mock import MagicMock

import numpy as np
import psycopg
import pytest
from scipy import sparse

os.environ.setdefault("DATABASE_URL", "postgresql://unused")

from app import config, copy_store, embedder, searcher, synonyms  # noqa: E402
from app.boolean_query import And, Not, Or, Phrase, Terms, parse  # noqa: E402
from app.chunker import chunk_words  # noqa: E402
from app.copy_store import CopyData  # noqa: E402
from app.pdf_extract import Heading, Word, _clean, _is_chapter_heading  # noqa: E402
from app.searcher import _evaluate, _find_spans, _locations, _unify  # noqa: E402
from app.stopwords import STOPWORDS  # noqa: E402
from app.text_preprocess import ngrams, preprocess, tokenize_words  # noqa: E402
from app.tfidf_index import TfidfIndex, _fit  # noqa: E402


@pytest.fixture
def no_pruning(monkeypatch):
    monkeypatch.setattr(config, "TFIDF_MIN_DF", 1)
    monkeypatch.setattr(config, "TFIDF_MAX_DF", 1.0)


@pytest.fixture
def tmp_copies(monkeypatch, tmp_path):
    monkeypatch.setattr(config, "COPIES_DIR", tmp_path / "copies")
    copy_store._cache.clear()
    return tmp_path


# ---------------------------------------------------------------- Embedding va tim kiem lai

class _StubIndex:
    def __init__(self, tfidf_hits):
        self.tfidf_hits = tfidf_hits
        self.chunk_rows = {"a": 0, "b": 1, "c": 2}
        self.copies = [{"copyId": "copy-1", "documentId": "doc-1"}]

    def search_chunks(self, text, top_k, allowed=None):
        return self.tfidf_hits


def test_rrf_prefers_chunk_found_by_both_rankings_and_stays_in_unit_range():
    fused = searcher.reciprocal_rank_fusion([[(0, 0.9), (1, 0.8)], [(2, 0.99), (1, 0.7)]], k=60)

    assert fused[0][0] == 1
    assert {row for row, _ in fused} == {0, 1, 2}
    assert all(0 < score <= 1 for _, score in fused)


def test_rrf_gives_one_to_chunk_ranked_first_everywhere():
    assert searcher.reciprocal_rank_fusion([[(0, 0.2)], [(0, 0.3)]], k=60) == [(0, pytest.approx(1.0))]


def test_rank_chunks_tfidf_mode_never_calls_vector(monkeypatch):
    def fail(*_):
        raise AssertionError("tfidf khong duoc goi vector")
    monkeypatch.setattr(embedder, "search", fail)

    hits, mode = searcher._rank_chunks(_StubIndex([(0, 0.5), (1, 0.01)]), "q", None, 0.05, "tfidf")

    assert (hits, mode) == ([(0, 0.5)], "tfidf")


def test_rank_chunks_vector_mode_applies_threshold_and_drops_unknown_chunks(monkeypatch):
    monkeypatch.setattr(config, "VECTOR_MIN_SCORE", 0.5)
    monkeypatch.setattr(embedder, "search", lambda *_: [("b", 0.9), ("khong-co", 0.8), ("a", 0.1)])

    hits, mode = searcher._rank_chunks(_StubIndex([]), "q", None, 0.05, "vector")

    assert (hits, mode) == ([(1, 0.9)], "vector")


def test_rank_chunks_hybrid_tfidf_threshold_does_not_cut_fused_scores(monkeypatch):
    monkeypatch.setattr(embedder, "search", lambda *_: [("b", 0.9)])

    hits, mode = searcher._rank_chunks(_StubIndex([(0, 0.5)]), "q", None, 0.05, "hybrid")

    assert mode == "hybrid"
    assert {row for row, _ in hits} == {0, 1}


def test_rank_chunks_hybrid_falls_back_to_tfidf_when_vector_fails(monkeypatch):
    def fail(*_):
        raise embedder.EmbedderNotReady("chua nap")
    monkeypatch.setattr(embedder, "search", fail)

    hits, mode = searcher._rank_chunks(_StubIndex([(0, 0.5)]), "q", None, 0.05, "hybrid")

    assert (hits, mode) == ([(0, 0.5)], "tfidf")


def test_rank_chunks_vector_mode_reports_failure(monkeypatch):
    def fail(*_):
        raise embedder.EmbedderNotReady("chua nap")
    monkeypatch.setattr(embedder, "search", fail)

    with pytest.raises(embedder.EmbedderNotReady):
        searcher._rank_chunks(_StubIndex([]), "q", None, 0.05, "vector")


class _FlakyPool:
    def __init__(self, failures):
        self.failures = failures
        self.calls = 0

    def connection(self):
        self.calls += 1
        if self.calls <= self.failures:
            raise psycopg.OperationalError("mang dut")
        return MagicMock()


def test_write_batch_retries_transient_network_errors(monkeypatch):
    flaky = _FlakyPool(failures=2)
    monkeypatch.setattr(embedder, "pool", flaky)
    monkeypatch.setattr(embedder, "WRITE_RETRY_SECONDS", 0)

    embedder._write_batch([("[0.1]", "a")])

    assert flaky.calls == 3


def test_write_batch_gives_up_after_all_attempts(monkeypatch):
    flaky = _FlakyPool(failures=99)
    monkeypatch.setattr(embedder, "pool", flaky)
    monkeypatch.setattr(embedder, "WRITE_RETRY_SECONDS", 0)

    with pytest.raises(psycopg.OperationalError):
        embedder._write_batch([("[0.1]", "a")])

    assert flaky.calls == embedder.WRITE_ATTEMPTS


def test_to_pgvector_format():
    assert embedder.to_pgvector([0.5, -0.25]) == "[0.500000,-0.250000]"


# ---------------------------------------------------------------- Boc tach PDF

def test_clean_replaces_lookalike_u_horn():
    assert _clean("ngƣời") == "người"
    assert _clean("CHƢƠNG") == "CHƯƠNG"
    # Dau rieng le sau ky tu sai van phai hop nhat thanh mot ky tu
    assert _clean("nhƣ̃ng") == "những"


@pytest.mark.parametrize("line", [
    "Chương 2",
    "CHƯƠNG 2",
    "Chương 2: Tấn công ứng dụng web",
    "Chương 2. Tấn công ứng dụng web",
    "CHƯƠNG 2 TẤN CÔNG ỨNG DỤNG WEB",
    "Chapter 3 - Indexing",
    "Phần IV",
])
def test_chapter_heading_accepts_real_headings(line):
    assert _is_chapter_heading(line)


@pytest.mark.parametrize("line", [
    "Chương 2 đề cập đến các dạng tấn công phổ biến lên ứng dụng web",
    "Chương 5, mục 2 trình bày",
    "Phần 1 cho thấy kết quả",
    "Chapter 3 describes indexing",
    "Phần mềm quản lý",
])
def test_chapter_heading_rejects_sentences(line):
    assert not _is_chapter_heading(line)


# ---------------------------------------------------------------- Tien xu ly

def test_preprocess_vietnamese_segments_words_and_cleans():
    tokens = preprocess("Tuy nhiên, Học máy và Cơ sở dữ liệu năm 2024 rất quan trọng!")

    assert tokens, tokens
    assert all(t == t.lower() for t in tokens)
    assert not any(ch.isdigit() for t in tokens for ch in t)
    assert not any(t in STOPWORDS for t in tokens)
    assert any("_" in t for t in tokens)  # tu nhieu am tiet duoc giu lien nhau


def test_tokens_point_back_to_original_words():
    words = ["Học", "máy,", "và", "mạng", "nơ-ron", "2024."]
    tokens = tokenize_words(words)

    covered = {i for t in tokens for i in range(t.start, t.end + 1)}
    assert {0, 1, 3, 4} <= covered  # "nơ-ron" -> am tiet "nơ", "ron" cung thuoc tu goc so 4
    assert 2 not in covered  # tu dung "và"
    assert 5 not in covered  # chu so
    assert all(0 <= t.start <= t.end < len(words) for t in tokens)


def test_synonyms_replace_token_sequences_and_keep_origin(monkeypatch):
    # "co so du lieu" sau tach tu la 2 token -> bien the 1 token "csdl" phai thay bang ca chuoi
    monkeypatch.setattr(synonyms, "synonym_rules", lambda: [(("csdl",), ("cơ_sở", "dữ_liệu"))])

    assert synonyms.canonical_sequence(["csdl", "quan_hệ"]) == [
        ("cơ_sở", 0, 0), ("dữ_liệu", 0, 0), ("quan_hệ", 1, 1)]
    assert ngrams(["csdl", "quan_hệ"]) == [
        "cơ_sở", "dữ_liệu", "quan_hệ", "cơ_sở dữ_liệu", "dữ_liệu quan_hệ"]


def test_synonym_file_is_tokenized_like_documents():
    synonyms.synonym_rules.cache_clear()
    rules = dict(synonyms.synonym_rules())
    assert rules[("csdl",)] == tuple(preprocess("cơ sở dữ liệu"))
    assert rules[("database",)] == tuple(preprocess("cơ sở dữ liệu"))


# ---------------------------------------------------------------- TF-IDF (XLVB)

def test_idf_and_sublinear_weight_match_document_example(no_pruning):
    # Muc 1.6: |D| = 1000, "rabbit" xuat hien o 20 van ban, "said" o 900; trong d_1 moi tu xuat hien 8 lan
    docs = [["rabbit"] * 8 + ["said"] * 8]
    docs += [["rabbit", "said"] for _ in range(19)]
    docs += [["said", "filler"] for _ in range(880)]
    docs += [["filler"] for _ in range(100)]
    vectorizer = _fit(docs)

    idf_rabbit = vectorizer.idf_[vectorizer.vocabulary_["rabbit"]]
    idf_said = vectorizer.idf_[vectorizer.vocabulary_["said"]]
    assert idf_rabbit == pytest.approx(math.log(1001 / 21) + 1)  # ~4.86
    assert idf_said == pytest.approx(math.log(1001 / 901) + 1)  # ~1.11
    assert (1 + math.log(8)) * idf_rabbit == pytest.approx(14.98, abs=0.01)
    assert (1 + math.log(8)) * idf_said == pytest.approx(3.40, abs=0.01)


def test_min_df_and_max_df_prune_vocabulary(monkeypatch):
    monkeypatch.setattr(config, "TFIDF_MIN_DF", 3)
    monkeypatch.setattr(config, "TFIDF_MAX_DF", 0.5)
    docs = [["chung", "hiếm"]] + [["chung", "vừa"] for _ in range(3)] + [["chung"] for _ in range(4)]
    vocabulary = _fit(docs).vocabulary_

    assert "vừa" in vocabulary  # DF = 3
    assert "hiếm" not in vocabulary  # DF = 1 < min_df
    assert "chung" not in vocabulary  # DF/|D| = 1 > max_df


def _index(token_lists: list[list[str]], chunk_copy: list[int], copies: list[dict],
           inverted: dict[str, list[int]] | None = None) -> TfidfIndex:
    vectorizer = _fit(token_lists)
    return TfidfIndex({t: int(c) for t, c in vectorizer.vocabulary_.items()}, vectorizer.idf_,
                      vectorizer.transform(token_lists).tocsr(), [f"chunk-{i}" for i in range(len(token_lists))],
                      chunk_copy, copies, inverted or {}, "now")


def test_search_chunks_ranks_by_cosine_and_filters_copies(no_pruning):
    texts = ["Mạng nơ-ron tích chập dùng để xử lý ảnh", "Cơ sở dữ liệu quan hệ", "Học máy với mạng nơ-ron sâu"]
    copies = [{"copyId": f"c{i}", "documentId": f"d{i}"} for i in range(3)]
    index = _index([preprocess(t) for t in texts], [0, 1, 2], copies)

    hits = index.search_chunks("xử lý ảnh bằng mạng nơ-ron", top_k=10)
    assert hits[0][0] == 0
    assert 1 not in [row for row, _ in hits]
    assert all(0 < s <= 1 for _, s in hits)
    assert {row for row, _ in index.search_chunks("xử lý ảnh bằng mạng nơ-ron", 10, {2})} <= {2}

    q = index.vectorize_query("mạng nơ-ron")
    assert np.linalg.norm(q.toarray()) == pytest.approx(1.0)  # chuan hoa L2


# ---------------------------------------------------------------- Boolean (Lecture 8)

def test_parse_boolean_query_structure():
    parsed = parse('"xử lý ảnh" AND NOT y tế')

    assert isinstance(parsed.tree, And)
    assert isinstance(parsed.tree.left, Phrase)
    assert isinstance(parsed.tree.right, Not)
    assert parsed.ranking_text == "xử lý ảnh"  # phan phu dinh khong dung de xep hang / highlight


def test_plain_question_is_not_boolean():
    parsed = parse("mạng nơ-ron là gì")
    assert parsed.tree is None
    assert parsed.ranking_text == "mạng nơ-ron là gì"


def test_evaluate_and_or_not_on_inverted_file():
    # Vi du Lecture 8: Term1: R1,R3; Term2: R1,R2; Term3: R2,R3,R4; Term4: R1..R4 (chi so 0..3)
    inverted = {"t1": [0, 2], "t2": [0, 1], "t3": [1, 2, 3], "t4": [0, 1, 2, 3]}
    copies = [{"copyId": f"c{i}", "documentId": f"d{i}"} for i in range(4)]
    index = TfidfIndex({}, np.zeros(0), sparse.csr_matrix((0, 0)), [], [], copies, inverted, "now")
    universe = set(range(4))

    assert _evaluate(Terms(["t1", "t3"]), index, universe) == {2}  # Term1 AND Term3 -> R3
    assert _evaluate(Or(Terms(["t1"]), Terms(["t2"])), index, universe) == {0, 1, 2}  # -> R1, R2, R3
    assert _evaluate(And(Terms(["t4"]), Not(Terms(["t1"]))), index, universe) == {1, 3}  # -> R2, R4


def test_phrase_requires_adjacent_tokens(tmp_copies):
    copy_store.save(_copy("c0", terms=["xử_lý", "ảnh", "số"]))
    copy_store.save(_copy("c1", terms=["ảnh", "xử_lý", "số"]))
    copies = [{"copyId": "c0", "documentId": "d0"}, {"copyId": "c1", "documentId": "d1"}]
    index = TfidfIndex({}, np.zeros(0), sparse.csr_matrix((0, 0)), [], [], copies,
                       {"xử_lý": [0, 1], "ảnh": [0, 1], "số": [0, 1]}, "now")

    assert _evaluate(Phrase(["xử_lý", "ảnh"]), index, {0, 1}) == {0}


# ---------------------------------------------------------------- Chunk goi nhau & dinh vi

def _copy(copy_id, terms, words=None, pages=None, lines=None, sentences=None, chunks=()):
    n = len(words) if words else len(terms)
    words = words or [t.replace("_", " ") for t in terms]
    return CopyData(
        copy_id=copy_id, document_id="d-" + copy_id, words=words,
        pages=pages or [1] * n, paragraphs=[1] * n, sentences=sentences or [1] * n,
        lines=lines or list(range(1, n + 1)),
        bboxes=[[float(i), 10.0, float(i) + 1, 20.0] for i in range(n)],
        heading_starts=[0], heading_titles=[("Chương 1", "1.1 Mở đầu")],
        page_sizes=[[600.0, 800.0], [600.0, 800.0]], page_labels={1: "i"},
        terms=terms, token_starts=list(range(len(terms))), token_ends=list(range(len(terms))),
        chunk_ids=[c[0] for c in chunks], chunk_starts=[c[1] for c in chunks], chunk_ends=[c[2] for c in chunks],
    )


def test_overlapping_chunks_are_unified_into_one_passage(tmp_copies):
    terms = [f"w{i}" for i in range(30)]
    copy_store.save(_copy("c0", terms, chunks=[("a", 0, 10), ("b", 8, 18), ("c", 20, 30)]))
    copies = [{"copyId": "c0", "documentId": "d0"}]
    index = TfidfIndex({}, np.zeros(0), sparse.csr_matrix((0, 0)), ["a", "b", "c"], [0, 0, 0], copies, {}, "now")

    passages = sorted(_unify(index, [(0, 0.4), (1, 0.7), (2, 0.2)]), key=lambda p: p.word_start)

    assert [(p.word_start, p.word_end, p.chunk_ids) for p in passages] == [(0, 18, ["a", "b"]), (20, 30, ["c"])]
    assert passages[0].score == 0.7  # diem doan = max diem cac chunk
    assert (passages[0].chapter, passages[0].section) == ("Chương 1", "1.1 Mở đầu")


def test_locations_merge_adjacent_words_and_split_by_page():
    # "mạng nơ ron" lien ke tren cung dong (trang 1), lap lai o cuoi trang 1 tran sang trang 2
    words = ["mạng", "nơ-ron", "khác", "mạng", "nơ-ron"]
    data = _copy("c0", terms=["mạng", "nơ_ron", "khác", "mạng", "nơ_ron"], words=words,
                 pages=[1, 1, 1, 1, 2], lines=[1, 1, 2, 3, 4])

    spans = _find_spans(data, 0, 5, [["mạng", "nơ_ron"]])
    assert spans == [(0, 1), (3, 4)]

    locations = _locations(data, spans, limit=10)
    assert [(l.page, l.word_start, l.word_end, l.text) for l in locations] == [
        (1, 0, 1, "mạng nơ-ron"), (1, 3, 3, "mạng"), (2, 4, 4, "nơ-ron")]
    assert locations[0].rects == [[0.0, 10.0, 2.0, 20.0]]  # 2 tu cung dong gop thanh 1 hinh chu nhat
    assert locations[0].printed_page_label == "i"


def test_adjacent_matches_are_not_merged_across_sentences():
    # Tieu de "... tich chap" ket thuc cau, cau sau bat dau bang "Mang": hai vung highlight rieng
    data = _copy("c0", terms=["tích", "chập", "mạng"], sentences=[1, 1, 2])
    assert _find_spans(data, 0, 3, [["tích"], ["chập"], ["mạng"]]) == [(0, 1), (2, 2)]


def test_locate_through_synonym(monkeypatch):
    monkeypatch.setattr(synonyms, "synonym_rules", lambda: [(("database",), ("cơ_sở", "dữ_liệu"))])
    data = _copy("c0", terms=["database", "quan_hệ"])
    # Truy van "co so du lieu" khop tu goc "database" trong van ban
    assert _find_spans(data, 0, 2, [["cơ_sở", "dữ_liệu"]]) == [(0, 0)]


def test_chunks_do_not_cross_sections_and_overlap():
    words = [Word(f"w{i}", 1 if i < 30 else 2, 1, 1, i, (0, 0, 1, 1)) for i in range(60)]
    headings = [Heading(0, "Chương 1", "1.1 A"), Heading(30, "Chương 1", "1.2 B")]

    chunks = chunk_words(words, headings, chunk_size=20, overlap=5)

    assert [(c.start, c.end, c.section) for c in chunks] == [
        (0, 20, "1.1 A"), (15, 30, "1.1 A"), (30, 50, "1.2 B"), (45, 60, "1.2 B")]
    assert chunks[2].page_number == 2
