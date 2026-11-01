"""
Gom dong nghia ve mot tu dai dien (Lecture 8: "Find synonyms, choose a representative word").

Tu dien doc tu SYNONYMS_FILE, moi dong viet tu nhien:   dai dien: bien the 1, bien the 2
Ca hai ve duoc tien xu ly bang dung pipeline (tach tu pyvi...) thanh CHUOI token, vi tieng Viet mot khai niem
thuong la nhieu token ("co so du lieu" -> co_so + du_lieu). Thay the dien ra tren chuoi token, moi token moi
van tro ve khoang token goc de dinh vi. Sua tep xong can lap chi muc lai.
"""
import logging
from functools import lru_cache

from app import config

log = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def synonym_rules() -> list[tuple[tuple[str, ...], tuple[str, ...]]]:
    from app.text_preprocess import preprocess  # tranh import vong

    rules: list[tuple[tuple[str, ...], tuple[str, ...]]] = []
    try:
        lines = config.SYNONYMS_FILE.read_text(encoding="utf-8").splitlines()
    except FileNotFoundError:
        log.warning("Khong tim thay tu dien dong nghia %s", config.SYNONYMS_FILE)
        return rules
    for line in lines:
        line = line.split("#", 1)[0].strip()
        if ":" not in line:
            continue
        representative, variants = line.split(":", 1)
        target = tuple(preprocess(representative))
        if not target:
            continue
        for variant in variants.split(","):
            source = tuple(preprocess(variant))
            if source and source != target:
                rules.append((source, target))
    # Uu tien bien the dai nhat khi nhieu quy tac cung khop
    rules.sort(key=lambda r: len(r[0]), reverse=True)
    return rules


def canonical_sequence(terms: list[str]) -> list[tuple[str, int, int]]:
    """
    Chuoi token -> chuoi token da gom dong nghia, kem (term, token_dau, token_cuoi) tro ve chuoi goc.
    Vd: ["csdl", "quan_hệ"] -> [("cơ_sở", 0, 0), ("dữ_liệu", 0, 0), ("quan_hệ", 1, 1)]
    """
    rules = synonym_rules()
    result: list[tuple[str, int, int]] = []
    i = 0
    while i < len(terms):
        for source, target in rules:
            n = len(source)
            if tuple(terms[i:i + n]) == source:
                result.extend((t, i, i + n - 1) for t in target)
                i += n
                break
        else:
            result.append((terms[i], i, i))
            i += 1
    return result


def canonical_terms(terms: list[str]) -> list[str]:
    return [t for t, _, _ in canonical_sequence(terms)]
