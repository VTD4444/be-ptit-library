"""
Mo hinh truy van Boolean (Lecture 8): AND, OR, NOT, cum tu chinh xac "..." (rang buoc vi tri: cac token lien ke)
va ngoac don. Hai tu dung canh nhau khong co toan tu => AND ngam dinh.

  expr     := and_expr (OR and_expr)*
  and_expr := not_expr ((AND)? not_expr)*
  not_expr := NOT not_expr | atom
  atom     := "phrase" | words | ( expr )

Truy van khong co toan tu/ngoac kep (cau hoi, doan van) => khong loc Boolean, chi xep hang TF-IDF.
Theo Lecture 8 phan "hybrid retrieval": Boolean dung de LOC tai lieu, TF-IDF + cosine dung de XEP HANG.
"""
import re
from dataclasses import dataclass, field

from app.synonyms import canonical_terms
from app.text_preprocess import preprocess

OPERATORS = {"AND", "OR", "NOT"}
LEXER_RE = re.compile(r'"([^"]*)"|(\()|(\))|([^\s()"]+)')


@dataclass
class Terms:
    terms: list[str]  # AND cua cac tu (da tien xu ly + dong nghia); rong => khong rang buoc


@dataclass
class Phrase:
    terms: list[str]  # cac token phai lien ke theo dung thu tu


@dataclass
class And:
    left: object
    right: object


@dataclass
class Or:
    left: object
    right: object


@dataclass
class Not:
    operand: object


@dataclass
class ParsedQuery:
    tree: object | None  # None => khong loc Boolean
    ranking_text: str  # phan khang dinh cua truy van, dung de xep hang TF-IDF
    highlight: list[list[str]] = field(default_factory=list)  # chuoi term can dinh vi (1 phan tu = tu don)


def _canonical_terms(text: str) -> list[str]:
    return canonical_terms(preprocess(text))


def parse(query: str) -> ParsedQuery:
    lexemes: list[tuple[str, str]] = []
    for phrase, lpar, rpar, word in LEXER_RE.findall(query):
        if lpar:
            lexemes.append(("(", lpar))
        elif rpar:
            lexemes.append((")", rpar))
        elif word in OPERATORS:
            lexemes.append(("op", word))
        elif word:
            lexemes.append(("word", word))
        else:
            lexemes.append(("phrase", phrase))

    is_boolean = any(kind in ("op", "phrase", "(") for kind, _ in lexemes)
    if not is_boolean:
        terms = _canonical_terms(query)
        return ParsedQuery(None, query, [[t] for t in dict.fromkeys(terms)])

    positive_texts: list[str] = []
    highlight: list[list[str]] = []
    pos = 0

    def peek(kind: str, value: str | None = None) -> bool:
        return pos < len(lexemes) and lexemes[pos][0] == kind and (value is None or lexemes[pos][1] == value)

    def parse_expr(negated: bool):
        nonlocal pos
        node = parse_and(negated)
        while peek("op", "OR"):
            pos += 1
            node = Or(node, parse_and(negated))
        return node

    def parse_and(negated: bool):
        nonlocal pos
        node = parse_not(negated)
        while pos < len(lexemes) and not peek(")") and not peek("op", "OR"):
            if peek("op", "AND"):
                pos += 1
            node = And(node, parse_not(negated))
        return node

    def parse_not(negated: bool):
        nonlocal pos
        if peek("op", "NOT"):
            pos += 1
            return Not(parse_not(not negated))
        return parse_atom(negated)

    def parse_atom(negated: bool):
        nonlocal pos
        if pos >= len(lexemes):
            return Terms([])
        kind, value = lexemes[pos]
        if kind == "(":
            pos += 1
            node = parse_expr(negated)
            if peek(")"):
                pos += 1
            return node
        if kind == "phrase":
            pos += 1
            terms = _canonical_terms(value)
            if not negated and terms:
                positive_texts.append(value)
                highlight.append(terms)
            return Phrase(terms)
        if kind == "word":
            words = []
            while peek("word"):
                words.append(lexemes[pos][1])
                pos += 1
            text = " ".join(words)
            terms = _canonical_terms(text)
            if not negated and terms:
                positive_texts.append(text)
                highlight.extend([t] for t in terms)
            return Terms(terms)
        # Toan tu dat sai cho (vd: "AND" o dau, ")" thua): bo qua
        pos += 1
        return Terms([])

    tree = parse_expr(False)
    while pos < len(lexemes):  # phan con lai sau ")" thua
        if peek(")"):
            pos += 1
            continue
        tree = And(tree, parse_expr(False))
    return ParsedQuery(tree, " ".join(positive_texts), highlight)
