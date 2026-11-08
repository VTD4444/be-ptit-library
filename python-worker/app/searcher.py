"""
Tim kiem noi dung:
  1. Phan tich truy van (boolean_query): Boolean -> LOC tai lieu tren inverted file (Lecture 8).
  2. Xep hang chunk bang TF-IDF + cosine (XLVB muc 1.9).
  3. Thong nhat chunk: cac chunk goi nhau (overlap) cung khop se bi trung noi dung/vi tri, nen gop cac khoang
     [start, end) chong len nhau cua cung ban so thanh MOT doan; diem doan = max diem chunk (OR co trong so).
  4. Dinh vi: dung positional index tim chinh xac cac tu/cum tu khop trong doan -> trang, doan, cau,
     vi tri tu va toa do de highlight.

Che do xep hang (buoc 2): tfidf | vector (cosine embedding qua pgvector) | hybrid (gop hai luong bang RRF).
Truy van Boolean luon dung tfidf vi vector khong hieu NOT va "cum tu".
"""
import logging
import re
from dataclasses import dataclass, field

from app import config, copy_store, embedder
from app.boolean_query import And, Not, Or, ParsedQuery, Phrase, Terms, parse
from app.db import pool
from app.synonyms import canonical_sequence, canonical_terms
from app.tfidf_index import TfidfIndex

log = logging.getLogger(__name__)

LETTERS_RE = re.compile(r"[^\W\d_]+")


@dataclass
class Location:
    page: int
    printed_page_label: str | None
    paragraph: int
    sentence: int
    word_start: int
    word_end: int  # bao gom
    text: str
    rects: list[list[float]]  # [x0, y0, x1, y1] theo don vi diem PDF, goc tren-trai cua trang
    page_width: float
    page_height: float


@dataclass
class Passage:
    document_id: str
    digital_copy_id: str
    chunk_ids: list[str]
    score: float
    word_start: int | None
    word_end: int | None  # khong bao gom
    page_start: int | None
    page_end: int | None
    chapter: str | None
    section: str | None
    snippet: str
    locations: list[Location] = field(default_factory=list)
    highlight_terms: list[str] = field(default_factory=list)


# ---------------------------------------------------------------- Boolean (Lecture 8)

def _has_sequence(terms: list[str], sequence: list[str]) -> bool:
    n = len(sequence)
    return any(terms[i:i + n] == sequence for i in range(len(terms) - n + 1))


def _evaluate(node, index: TfidfIndex, universe: set[int]) -> set[int]:
    if isinstance(node, Terms):
        if not node.terms:
            return set(universe)
        # Toi uu Lecture 8: giao tu danh sach postings ngan nhat
        postings = sorted((index.postings(t) for t in dict.fromkeys(node.terms)), key=len)
        result = postings[0]
        for p in postings[1:]:
            result = result & p
        return result & universe
    if isinstance(node, Phrase):
        if not node.terms:
            return set(universe)
        candidates = _evaluate(Terms(node.terms), index, universe)
        if len(node.terms) == 1:
            return candidates
        # Rang buoc vi tri: cac token phai lien ke dung thu tu
        matched = set()
        for copy_idx in candidates:
            data = copy_store.load(index.copies[copy_idx]["copyId"])
            if data is not None and _has_sequence(canonical_terms(data.terms), node.terms):
                matched.add(copy_idx)
        return matched
    if isinstance(node, And):
        left = _evaluate(node.left, index, universe)
        return left & _evaluate(node.right, index, left) if left else set()
    if isinstance(node, Or):
        return _evaluate(node.left, index, universe) | _evaluate(node.right, index, universe)
    if isinstance(node, Not):
        # NOT: phan bu tren toan bo tap tai lieu
        return universe - _evaluate(node.operand, index, universe)
    return set(universe)


# ---------------------------------------------------------------- Dinh vi

def _find_spans(data: copy_store.CopyData, start: int, end: int, highlight: list[list[str]]) -> list[tuple[int, int]]:
    token_ids = list(data.token_range(start, end))
    # Gom dong nghia tren chuoi token cua doan; moi term moi tro ve khoang token goc
    sequence = canonical_sequence([data.terms[t] for t in token_ids])
    terms = [term for term, _, _ in sequence]
    singles = {seq[0] for seq in highlight if len(seq) == 1}
    phrases = [seq for seq in highlight if len(seq) > 1]

    def word_span(first: int, last: int) -> tuple[int, int]:
        return data.token_starts[token_ids[sequence[first][1]]], data.token_ends[token_ids[sequence[last][2]]]

    spans: list[tuple[int, int]] = []
    for i in range(len(sequence)):
        if terms[i] in singles:
            spans.append(word_span(i, i))
        for seq in phrases:
            if terms[i:i + len(seq)] == seq:
                spans.append(word_span(i, i + len(seq) - 1))

    # Gop cac vung lien ke / chong nhau TRONG CUNG MOT CAU (vd: "mang" + "no ron" -> mot vung highlight)
    merged: list[list[int]] = []
    for a, b in sorted(spans):
        if merged and a <= merged[-1][1] + 1 and data.sentences[a] == data.sentences[merged[-1][1]]:
            merged[-1][1] = max(merged[-1][1], b)
        else:
            merged.append([a, b])
    return [(a, b) for a, b in merged]


def _locations(data: copy_store.CopyData, spans: list[tuple[int, int]], limit: int) -> list[Location]:
    locations: list[Location] = []
    for a, b in spans:
        # Tach vung vat qua trang thanh tung vi tri rieng tren moi trang
        segment_start = a
        for w in range(a, b + 1):
            if w == b or data.pages[w + 1] != data.pages[w]:
                locations.append(_location(data, segment_start, w))
                segment_start = w + 1
                if len(locations) >= limit:
                    return locations
    return locations


def _location(data: copy_store.CopyData, a: int, b: int) -> Location:
    # Gop toa do cac tu tren cung mot dong thanh mot hinh chu nhat
    rects: list[list[float]] = []
    current_line = None
    for w in range(a, b + 1):
        x0, y0, x1, y1 = data.bboxes[w]
        if data.lines[w] == current_line:
            r = rects[-1]
            rects[-1] = [min(r[0], x0), min(r[1], y0), max(r[2], x1), max(r[3], y1)]
        else:
            rects.append([x0, y0, x1, y1])
            current_line = data.lines[w]
    page = data.pages[a]
    width, height = data.page_sizes[page - 1]
    return Location(
        page=page,
        printed_page_label=data.page_labels.get(page),
        paragraph=data.paragraphs[a],
        sentence=data.sentences[a],
        word_start=a,
        word_end=b,
        text=" ".join(data.words[a:b + 1]),
        rects=rects,
        page_width=width,
        page_height=height,
    )


def _snippet(data: copy_store.CopyData, start: int, end: int, locations: list[Location]) -> str:
    """Trich doan SNIPPET_WORDS tu quanh cum vi tri khop day nhat trong doan."""
    window = config.SNIPPET_WORDS
    positions = sorted(loc.word_start for loc in locations)
    anchor = start
    best = 0
    for i, p in enumerate(positions):
        count = sum(1 for q in positions[i:] if q < p + window)
        if count > best:
            best, anchor = count, p
    s = max(start, anchor - window // 5)
    e = min(end, s + window)
    s = max(start, e - window)
    text = " ".join(data.words[s:e])
    return ("… " if s > start else "") + text + (" …" if e < end else "")


# ---------------------------------------------------------------- Tim kiem

def _fallback_passages(index: TfidfIndex, rows: list[tuple[int, float]]) -> list[Passage]:
    """Chunk cua ban so chua co du lieu vi tri: lay thong tin tu DB, khong dinh vi duoc tung tu."""
    chunk_ids = [index.chunk_ids[r] for r, _ in rows]
    with pool.connection() as conn:
        details = {r[0]: r[1:] for r in conn.execute(
            """SELECT id::text, page_number, page_end, chapter, section, chunk_text
               FROM public.search_document_chunks WHERE id = ANY(%s::uuid[])""",
            (chunk_ids,),
        ).fetchall()}
    passages = []
    for row, score in rows:
        chunk_id = index.chunk_ids[row]
        if chunk_id not in details:
            continue
        page, page_end, chapter, section, text = details[chunk_id]
        copy = index.copies[index.chunk_copy[row]]
        words = (text or "").split()
        snippet = " ".join(words[:config.SNIPPET_WORDS]) + (" …" if len(words) > config.SNIPPET_WORDS else "")
        passages.append(Passage(copy["documentId"], copy["copyId"], [chunk_id], score, None, None,
                                page, page_end, chapter, section, snippet))
    return passages


def _unify(index: TfidfIndex, hits: list[tuple[int, float]]) -> list[Passage]:
    by_copy: dict[int, list[tuple[int, float]]] = {}
    for row, score in hits:
        by_copy.setdefault(int(index.chunk_copy[row]), []).append((row, score))

    passages: list[Passage] = []
    for copy_idx, rows in by_copy.items():
        copy = index.copies[copy_idx]
        data = copy_store.load(copy["copyId"])
        positions = {cid: i for i, cid in enumerate(data.chunk_ids)} if data else {}
        intervals = []
        missing = []
        for row, score in rows:
            i = positions.get(index.chunk_ids[row])
            if i is None:
                missing.append((row, score))
            else:
                intervals.append((data.chunk_starts[i], data.chunk_ends[i], score, index.chunk_ids[row]))
        passages.extend(_fallback_passages(index, missing) if missing else [])

        # Gop cac chunk goi nhau: khoang [start, end) chong len nhau => cung mot doan van ban
        intervals.sort()
        groups: list[list] = []
        for start, end, score, chunk_id in intervals:
            if groups and start < groups[-1][1]:
                g = groups[-1]
                g[1] = max(g[1], end)
                g[2] = max(g[2], score)
                g[3].append(chunk_id)
            else:
                groups.append([start, end, score, [chunk_id]])
        for start, end, score, chunk_ids in groups:
            chapter, section = data.heading_at(start)
            passages.append(Passage(copy["documentId"], copy["copyId"], chunk_ids, score, start, end,
                                    data.pages[start], data.pages[end - 1], chapter, section, ""))
    return passages


def reciprocal_rank_fusion(rankings: list[list[tuple[int, float]]], k: int) -> list[tuple[int, float]]:
    """
    RRF: diem chunk = tong 1 / (k + hang) tren cac luong. Chi dung thu hang nen khong can dua cosine TF-IDF va
    cosine embedding ve cung thang. Chia cho diem toi da de ket qua nam trong [0, 1] nhu cac che do khac.
    """
    scores: dict[int, float] = {}
    for ranking in rankings:
        for rank, (row, _) in enumerate(ranking, start=1):
            scores[row] = scores.get(row, 0.0) + 1 / (k + rank)
    best_possible = len(rankings) / (k + 1)
    return sorted(((row, score / best_possible) for row, score in scores.items()), key=lambda hit: -hit[1])


def _vector_hits(index: TfidfIndex, text: str, allowed: set[int] | None) -> list[tuple[int, float]]:
    copy_ids = [index.copies[i]["copyId"] for i in allowed] if allowed is not None else None
    hits = embedder.search(text, config.HYBRID_CANDIDATE_CHUNKS, copy_ids)
    # Chunk ngoai mo hinh dang dung (chua lap chi muc lai) thi khong gop va dinh vi duoc
    return [(index.chunk_rows[chunk_id], score) for chunk_id, score in hits
            if chunk_id in index.chunk_rows and score >= config.VECTOR_MIN_SCORE]


def _rank_chunks(index: TfidfIndex, text: str, allowed: set[int] | None, min_score: float,
                 mode: str) -> tuple[list[tuple[int, float]], str]:
    """Tra ve (hang chunk, diem) giam dan va che do thuc su da dung."""
    tfidf_hits: list[tuple[int, float]] = []
    if mode != "vector":
        tfidf_hits = [(row, score) for row, score in index.search_chunks(text, config.SEARCH_CANDIDATE_CHUNKS, allowed)
                      if score >= min_score]
    if mode == "tfidf":
        return tfidf_hits, mode

    try:
        vector_hits = _vector_hits(index, text, allowed)
    except Exception as ex:  # noqa: BLE001 - hybrid van tra duoc ket qua TF-IDF khi model hoac DB loi
        if mode == "vector":
            raise
        log.warning("Khong tim duoc bang vector, chi dung TF-IDF: %s", ex)
        return tfidf_hits, "tfidf"
    if mode == "vector":
        return vector_hits, mode
    fused = reciprocal_rank_fusion([tfidf_hits[:config.HYBRID_CANDIDATE_CHUNKS], vector_hits], config.RRF_K)
    return fused, mode


def search(index: TfidfIndex, query: str, limit: int, best_per_document: bool,
           document_ids: set[str] | None, min_score: float, max_locations: int,
           mode: str | None = None) -> tuple[list[Passage], ParsedQuery, str]:
    mode = mode or config.SEARCH_MODE
    parsed = parse(query)
    allowed = index.copies_of_documents(document_ids) if document_ids is not None else None
    if parsed.tree is not None:
        mode = "tfidf"
        universe = allowed if allowed is not None else set(range(len(index.copies)))
        allowed = _evaluate(parsed.tree, index, universe)
    if not parsed.ranking_text.strip() or (allowed is not None and not allowed):
        return [], parsed, mode

    hits, mode = _rank_chunks(index, parsed.ranking_text, allowed, min_score, mode)
    passages = sorted(_unify(index, hits), key=lambda p: p.score, reverse=True)

    results: list[Passage] = []
    seen_documents: set[str] = set()
    for passage in passages:
        if best_per_document and passage.document_id in seen_documents:
            continue
        seen_documents.add(passage.document_id)
        if passage.word_start is not None:
            data = copy_store.load(passage.digital_copy_id)
            spans = _find_spans(data, passage.word_start, passage.word_end, parsed.highlight)
            term_locations = _locations(data, spans, max_locations)
            # Khop theo nghia co the khong chua tu nao cua truy van: tra ve vi tri ca doan de van to duoc tren trang
            passage.locations = term_locations or (
                _locations(data, [(passage.word_start, passage.word_end - 1)], max_locations) if mode != "tfidf" else [])
            if passage.locations:
                # Chuong/muc theo vi tri khop dau tien (doan co the bat dau ngay truoc tieu de muc)
                passage.chapter, passage.section = data.heading_at(passage.locations[0].word_start)
            passage.snippet = _snippet(data, passage.word_start, passage.word_end, term_locations)
            passage.highlight_terms = sorted({s.lower() for loc in term_locations
                                              for s in LETTERS_RE.findall(loc.text)})
        results.append(passage)
        if len(results) >= limit:
            break
    return results, parsed, mode
