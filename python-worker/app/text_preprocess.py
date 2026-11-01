"""
Tien xu ly van ban (XLVB muc 1.3, Lecture 8 "Text Preprocessing Pipeline"), dieu chinh cho tieng Viet:
- Tach tu bang pyvi: tieng Viet viet cach theo am tiet, mot tu thuong gom nhieu am tiet ("hoc may" -> "hoc_may").
- Moi token giu lai khoang tu goc [start, end] trong dong tu cua tai lieu de dinh vi chinh xac (positional index).
- Chuan hoa dang tu (stemming/lemma) chi cho tu tieng Anh xen lan; tieng Viet khong bien hinh.
- Dong nghia (synonym reduction) ap dung o buoc sinh dac trung (canonical) de sua tu dien dong nghia
  chi can lap chi muc lai, khong phai boc tach lai PDF.
Dung CHUNG cho ca luc lap chi muc lan luc xu ly truy van.
"""
import re
import unicodedata
from dataclasses import dataclass
from functools import lru_cache

import simplemma
from pyvi import ViTokenizer

from app.stopwords import STOPWORDS
from app.synonyms import canonical_terms

LETTERS_RE = re.compile(r"[^\W\d_]+")
ASCII_RE = re.compile(r"^[a-z]+$")


@dataclass(frozen=True)
class Token:
    term: str
    start: int  # chi so tu goc dau tien (bao gom)
    end: int  # chi so tu goc cuoi cung (bao gom)


@lru_cache(maxsize=100_000)
def _lemmatize(syllable: str) -> str:
    # Chuan hoa dang tu: chi cho tu tieng Anh (vd: "networks" -> "network")
    if ASCII_RE.match(syllable):
        return simplemma.lemmatize(syllable, lang="en").lower()
    return syllable


def tokenize_words(words: list[str]) -> list[Token]:
    """
    Danh sach tu goc (nhu trong van ban, con dau cau) -> token da tien xu ly kem vi tri tu goc.
    Nen goi theo tung cau de pyvi co ngu canh.
    """
    syllables: list[str] = []
    owners: list[int] = []
    for i, word in enumerate(words):
        # Bo chu so, dau cau, ky tu dac biet - chi giu am tiet gom chu cai ("no-ron" -> "no", "ron")
        for syllable in LETTERS_RE.findall(unicodedata.normalize("NFC", word)):
            syllables.append(syllable)
            owners.append(i)
    if not syllables:
        return []

    # pyvi chi dung de quyet dinh gom bao nhieu am tiet thanh mot tu
    groups = ViTokenizer.tokenize(" ".join(syllables)).split()
    sizes = [len(g.split("_")) for g in groups]
    if sum(sizes) != len(syllables):
        sizes = [1] * len(syllables)

    tokens: list[Token] = []
    k = 0
    for size in sizes:
        parts = [s.lower() for s in syllables[k:k + size]]  # chu thuong
        start, end = owners[k], owners[k + size - 1]
        k += size
        term = "_".join(parts)
        if term in STOPWORDS:  # bo tu dung
            continue
        tokens.append(Token("_".join(_lemmatize(p) for p in parts), start, end))
    return tokens


def preprocess(text: str) -> list[str]:
    """Van ban -> danh sach tu goc (chua ap dung dong nghia)."""
    return [t.term for t in tokenize_words(text.split())]


def ngrams(tokens: list[str]) -> list[str]:
    """Tu goc -> dac trung unigram + bigram sau khi gom dong nghia ve tu dai dien."""
    terms = canonical_terms(tokens)
    return terms + [f"{a} {b}" for a, b in zip(terms, terms[1:])]


def analyze(text: str) -> list[str]:
    return ngrams(preprocess(text))
