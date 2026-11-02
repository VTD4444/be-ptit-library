"""
Boc tach PDF thanh MOT dong tu chuan duy nhat cho ca tai lieu.
Moi tu mang vi tri day du (Lecture 8 - positional index: record, paragraph, sentence, word no.)
cong them so trang, dong va toa do tren trang de highlight khi doc.
"""
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass, field

import fitz  # PyMuPDF

from app import config

MAX_HEADING_LEN = 120
# Dong lap lai tren >= 50% so trang (va it nhat 3 trang) duoc coi la header/footer
BOILERPLATE_PAGE_RATIO = 0.5
BOILERPLATE_MIN_PAGES = 3

CHAPTER_RE = re.compile(r"^(chương|chuong|chapter|phần|phan|part)\s+([0-9]+|[ivxlcdm]+)\b", re.IGNORECASE)
# "1.2 Gioi thieu", "2.3.1. Mo hinh" - bat buoc chu cai hoa sau so de tranh nham voi so lieu trong cau
SECTION_RE = re.compile(r"^\d+(\.\d+){1,3}\.?\s+[A-ZÀ-Ỹ]")
PAGE_NUMBER_RE = re.compile(r"^\s*(trang\s*|page\s*)?\d{1,4}\s*$", re.IGNORECASE)
SENTENCE_END_RE = re.compile(r"[.?!…]+[\"'”’)\]]*$")
# Dau cach thuong rong ~0.25 co chu; khoang trong > 0.12 co chu (khoang nua dau cach) la ranh gioi tu
WORD_GAP_RATIO = 0.12
# Dong co dau cham dan ". . . . ." (muc luc, danh sach hinh/bang)
LEADER_RE = re.compile(r"(\.\s?){5,}")
TOC_MIN_LEADER_LINES = 3
TOC_LEADER_RATIO = 0.3


@dataclass
class Word:
    text: str
    page: int  # 1-based
    paragraph: int  # 1-based, dem tren toan tai lieu
    sentence: int  # 1-based, dem tren toan tai lieu
    line: int  # 1-based, dem tren toan tai lieu (gop toa do highlight theo dong)
    bbox: tuple[float, float, float, float]


@dataclass
class Heading:
    start: int  # vi tri tu bat dau chuong/muc
    chapter: str | None
    section: str | None


@dataclass
class ExtractionResult:
    words: list[Word]
    headings: list[Heading]
    page_count: int
    quality: float  # ty le trang co van ban trich xuat duoc
    page_labels: dict[int, str] = field(default_factory=dict)
    page_sizes: list[tuple[float, float]] = field(default_factory=list)


@dataclass
class _Line:
    text: str
    block: int
    words: list[tuple[str, tuple[float, float, float, float]]]


def _clean(text: str) -> str:
    # NFKC: hop nhat dau tieng Viet nhu NFC, dong thoi tach ligature cua LaTeX ("ﬁ" -> "fi")
    text = unicodedata.normalize("NFKC", text)
    # Bo ky tu vung rieng (Private Use) - thuong la glyph bullet cua font Wingdings/Symbol dinh vao tu
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Co")
    # Soft hyphen (U+00AD): Word/PDF hay dung cho dau gach noi that ("no-ron")
    text = text.replace("­", "-")
    return re.sub(r"\s+", " ", text).strip()


def _key(text: str) -> str:
    return re.sub(r"[^\w]+", "", _clean(text).lower())


def _boilerplate_key(text: str) -> str:
    # Bo chu so de "Chuong 1 - Trang 5" va "Chuong 1 - Trang 6" duoc tinh la cung mot dong
    return re.sub(r"\d+", "#", _key(text))


def _toc_by_page(doc: fitz.Document) -> dict[int, list[tuple[int, str]]]:
    by_page: dict[int, list[tuple[int, str]]] = {}
    for level, title, page in doc.get_toc(simple=True):
        if level <= 2 and page >= 1 and title.strip():
            by_page.setdefault(page, []).append((level, _clean(title)[:255]))
    return by_page


def _page_lines(page: fitz.Page) -> list[_Line]:
    """
    Dung tu o muc KY TU (rawdict) thay vi get_text("words"): pdfTeX voi font tieng Viet khong ghi ky tu dau cach
    sau glyph co dau chong (e, o, u...) ma chi de mot khoang trong bang do rong dau cach, khien "Hệ cơ sở dữ liệu"
    bi doc thanh "Hệcơ sởdữliệu". Khoang trong giua hai ky tu > WORD_GAP_RATIO x co chu => ranh gioi tu.
    """
    lines: list[_Line] = []
    raw = page.get_text("rawdict", sort=True)
    for block_no, block in enumerate(raw["blocks"]):
        if block.get("type") != 0:
            continue
        for line in block["lines"]:
            words: list[tuple[str, tuple[float, float, float, float]]] = []
            chars: list[str] = []
            box: list[float] | None = None
            previous: tuple[float, ...] | None = None

            def flush():
                nonlocal chars, box
                text = _clean("".join(chars))
                if text and box is not None:
                    words.append((text, (round(box[0], 1), round(box[1], 1), round(box[2], 1), round(box[3], 1))))
                chars, box = [], None

            for span in line["spans"]:
                gap_limit = span["size"] * WORD_GAP_RATIO
                for ch in span["chars"]:
                    bbox = ch["bbox"]
                    if ch["c"].isspace():
                        flush()
                        previous = None
                        continue
                    if previous is not None and bbox[0] - previous[2] > gap_limit:
                        flush()
                    chars.append(ch["c"])
                    box = list(bbox) if box is None else [min(box[0], bbox[0]), min(box[1], bbox[1]),
                                                          max(box[2], bbox[2]), max(box[3], bbox[3])]
                    previous = bbox
            flush()
            if words:
                lines.append(_Line(" ".join(w for w, _ in words), block_no, words))
    return [l for l in lines if not PAGE_NUMBER_RE.match(l.text)]


def _find_boilerplate(pages: list[list[_Line]]) -> set[str]:
    """Tai lieu XLVB muc 1.3 buoc 1 - loai bo phan du thua: header/footer lap lai tren nhieu trang."""
    if len(pages) < BOILERPLATE_MIN_PAGES:
        return set()
    counts = Counter()
    for lines in pages:
        counts.update({_boilerplate_key(l.text) for l in lines if len(l.text) <= MAX_HEADING_LEN})
    threshold = max(BOILERPLATE_MIN_PAGES, len(pages) * BOILERPLATE_PAGE_RATIO)
    return {k for k, n in counts.items() if k and n >= threshold}


def _is_toc_page(lines: list[_Line]) -> bool:
    """Trang muc luc / danh sach hinh / bang: nhieu dong dau cham dan, chi lap lai tieu de => phan du thua."""
    leaders = sum(1 for l in lines if LEADER_RE.search(l.text))
    return leaders >= TOC_MIN_LEADER_LINES and leaders >= len(lines) * TOC_LEADER_RATIO


def extract(path: str) -> ExtractionResult:
    with fitz.open(path) as doc:
        toc = _toc_by_page(doc)
        labels: dict[int, str] = {}
        sizes: list[tuple[float, float]] = []
        pages: list[list[_Line]] = []
        for index, page in enumerate(doc):
            label = page.get_label() if hasattr(page, "get_label") else ""
            if label:
                labels[index + 1] = label
            sizes.append((round(page.rect.width, 1), round(page.rect.height, 1)))
            pages.append(_page_lines(page))
        page_count = doc.page_count

    boilerplate = _find_boilerplate(pages)
    use_toc = bool(toc)
    words: list[Word] = []
    headings: list[Heading] = []
    chapter: str | None = None
    section: str | None = None
    paragraph = sentence = line_no = 0
    sentence_open = False
    pages_with_text = 0

    def apply(level: int, title: str) -> None:
        nonlocal chapter, section
        if level == 1:
            chapter, section = title, None
        else:
            section = title
        if headings and headings[-1].start == len(words):
            headings[-1] = Heading(len(words), chapter, section)
        else:
            headings.append(Heading(len(words), chapter, section))

    for page_no, page_lines in enumerate(pages, start=1):
        page_lines = [l for l in page_lines if _boilerplate_key(l.text) not in boilerplate]
        if sum(len(l.text) for l in page_lines) >= config.MIN_CHARS_PER_PAGE:
            pages_with_text += 1
        if _is_toc_page(page_lines):
            page_lines = []  # van ap dung bookmark cua trang ben duoi

        # Muc luc chi cho biet trang: tieu de xuat hien trong trang thi doi chuong/muc dung dong do,
        # khong tim thay thi coi nhu bat dau tu dau trang
        line_keys = [_key(l.text) for l in page_lines]
        pending = []
        for level, title in toc.get(page_no, []):
            title_key = _key(title)[:40]
            if title_key and any(k.startswith(title_key) for k in line_keys):
                pending.append((level, title, title_key))
            else:
                apply(level, title)

        previous_block = None
        for i, line in enumerate(page_lines):
            is_heading = False
            if use_toc:
                for heading in pending:
                    if line_keys[i].startswith(heading[2]):
                        apply(heading[0], heading[1])
                        pending.remove(heading)
                        is_heading = True
                        break
            elif len(line.text) <= MAX_HEADING_LEN:
                if CHAPTER_RE.match(line.text):
                    title = line.text
                    # "CHUONG 1" thuong co ten chuong o dong ke tiep
                    if (len(line.text.split()) <= 3 and i + 1 < len(page_lines)
                            and len(page_lines[i + 1].text) <= MAX_HEADING_LEN):
                        title = f"{line.text}: {page_lines[i + 1].text}"
                    apply(1, title[:255])
                    is_heading = True
                elif SECTION_RE.match(line.text):
                    apply(2, line.text[:255])
                    is_heading = True

            # Block cua PyMuPDF ~ doan van; tieu de luon tach thanh doan rieng
            if line.block != previous_block or is_heading:
                paragraph += 1
                sentence_open = False
            previous_block = None if is_heading else line.block
            line_no += 1

            for text, bbox in line.words:
                if not sentence_open:
                    sentence += 1
                    sentence_open = True
                words.append(Word(text, page_no, paragraph, sentence, line_no, bbox))
                if SENTENCE_END_RE.search(text):
                    sentence_open = False
            if is_heading:
                sentence_open = False

    quality = pages_with_text / page_count if page_count else 0.0
    return ExtractionResult(words=words, headings=headings, page_count=page_count,
                            quality=round(quality, 4), page_labels=labels, page_sizes=sizes)
