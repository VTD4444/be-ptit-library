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
# Sau "Chuong 2" chi co the la het dong, dau phan cach hoac ten chuong viet hoa; "Chuong 2 de cap..." la cau van
CHAPTER_SEPARATORS = ":.-–—"
# Font tieng Viet cu ghi "ư/Ư" thanh U+01A3/U+01A2 ("ƣ/Ƣ", khong co trong tieng Viet) nen phai doi ve ky tu dung
LOOKALIKE_CHARS = str.maketrans({"ƣ": "ư", "Ƣ": "Ư"})
DOTLESS_I_RE = re.compile("ı(?=[\u0300-\u036f])")
SPACE_BEFORE_MARK_RE = re.compile("\\s+(?=[\u0300-\u036f])")
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


def _is_chapter_heading(text: str) -> bool:
    match = CHAPTER_RE.match(text)
    if not match:
        return False
    rest = text[match.end():].lstrip()
    return not rest or rest[0] in CHAPTER_SEPARATORS or rest[0].isupper()


def _clean(text: str) -> str:
    # Doi truoc khi chuan hoa de dau rieng le dat sau "ƣ" van hop nhat duoc thanh "ừ", "ứ"...
    text = text.translate(LOOKALIKE_CHARS)
    # LaTeX ghi "í" thanh "ı" (i khong cham) + dau sac rieng; dau con sot lai sau khoang trang thi gan ve chu truoc
    text = DOTLESS_I_RE.sub("i", text)
    text = SPACE_BEFORE_MARK_RE.sub("", text)
    # NFKC: hop nhat dau tieng Viet nhu NFC, dong thoi tach ligature cua LaTeX ("ﬁ" -> "fi")
    text = unicodedata.normalize("NFKC", text)
    # Bo ky tu vung rieng (Private Use) - thuong la glyph bullet cua font Wingdings/Symbol dinh vao tu
    # Ky tu dieu khien (Cc, vd NUL cua font toan hoc) cung bo: Postgres khong nhan NUL trong cot text
    text = "".join(ch for ch in text if unicodedata.category(ch) not in ("Co", "Cc") or ch.isspace())
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


def _next_base(line_chars: list, i: int):
    """Bbox cua ky tu thuong (khong phai dau cach / dau rieng) dau tien sau vi tri i trong dong."""
    for ch, _ in line_chars[i + 1:]:
        if not ch["c"].isspace() and not unicodedata.combining(ch["c"]):
            return ch["bbox"]
    return None


def _next_char(line_chars: list, i: int) -> str:
    """Ky tu thuong dau tien sau vi tri i (bo qua dau cach / dau rieng), "" neu het dong."""
    for ch, _ in line_chars[i + 1:]:
        if not ch["c"].isspace() and not unicodedata.combining(ch["c"]):
            return ch["c"]
    return ""


def _has_real_space(line_chars: list) -> bool:
    """Dong co it nhat mot dau cach chiem khoang trong that (chu sau bat dau sau dau cach > nguong ranh gioi tu)."""
    for i, (ch, size) in enumerate(line_chars):
        if ch["c"].isspace():
            following = _next_base(line_chars, i)
            if following is not None and following[0] - ch["bbox"][0] > size * WORD_GAP_RATIO:
                return True
    return False


def _is_phantom_space(space, previous, following, size: float) -> bool:
    """
    Mot so font tieng Viet (Word/VNI xuat PDF) chen ky tu dau cach "ma" sat canh dau rieng de dat dau chong len
    chu: dau cach nay nam de len chu dung truoc hoac chu dung sau no thay vi chiem mot khoang trong that.
    Chi xet dau cach ngay canh dau rieng (goi tu _page_lines): co PDF ma dau cach that cung chong len chu.
    """
    tolerance = size * 0.05
    if previous is not None and space[0] < previous[2] - tolerance:
        return True
    return following is not None and following[0] < space[2] - tolerance


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

            line_chars = [(ch, span["size"]) for span in line["spans"] for ch in span["chars"]]
            has_real_space = _has_real_space(line_chars)
            for i, (ch, size) in enumerate(line_chars):
                c, bbox = ch["c"], ch["bbox"]
                if unicodedata.combining(c):
                    # Dau tieng Viet la glyph rieng rong 0, co the dat lech trai/phai chu goc: luon gan vao chu dung
                    # truoc va chi mo rong mep phai cua chu do (khong bao gio thu hep), roi moi tinh khoang trong
                    if chars:
                        chars.append(c)
                    elif words:
                        words[-1] = (_clean(words[-1][0] + c), words[-1][1])
                    if previous is not None:
                        previous = (previous[0], previous[1], max(previous[2], bbox[2]), previous[3])
                    continue
                if c.isspace():
                    beside_mark = any(0 <= j < len(line_chars) and unicodedata.combining(line_chars[j][0]["c"])
                                      for j in (i - 1, i + 1))
                    following = _next_base(line_chars, i)
                    if beside_mark and _is_phantom_space(bbox, previous, following, size):
                        continue
                    # Dau cach rong 0 (font CID xuat tu Word): chu sau dinh lien chu truoc, khong co khoang trang
                    # nhin thay tren trang => khong phai ranh gioi tu ("trấ n" -> "trấn", "khá ch" -> "khách")
                    # Chi ap dung khi: chu truoc la chu co dau (cho dau cach "ma" thay cho glyph dau cu) va dong co
                    # dau cach that rong binh thuong (co PDF ma moi dau cach deu rong 0 => khong phan biet duoc)
                    if (previous is not None and following is not None and chars
                            and len(unicodedata.normalize("NFD", chars[-1])) > 1
                            and has_real_space
                            and following[0] - previous[2] <= size * WORD_GAP_RATIO
                            and following[0] - bbox[0] <= size * WORD_GAP_RATIO
                            and _next_char(line_chars, i).isalpha()):
                        continue
                    flush()
                    previous = None
                    continue
                if previous is not None and bbox[0] - previous[2] > size * WORD_GAP_RATIO:
                    flush()
                chars.append(c)
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
                if _is_chapter_heading(line.text):
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
