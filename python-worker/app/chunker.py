import bisect
from dataclasses import dataclass

from app.pdf_extract import Heading, Word

# Doan qua ngan (vd: chi co dong tieu de) duoc gop vao doan ke tiep thay vi tao chunk rieng
MIN_RUN_WORDS = 20


@dataclass
class Chunk:
    index: int
    start: int  # vi tri tu goc dau tien (bao gom)
    end: int  # vi tri tu goc ket thuc (khong bao gom)
    page_number: int
    page_end: int
    chapter: str | None
    section: str | None
    text: str

    @property
    def token_count(self) -> int:
        return self.end - self.start


def chunk_words(words: list[Word], headings: list[Heading], chunk_size: int, overlap: int) -> list[Chunk]:
    """
    Chia dong tu thanh cac khoang [start, end) bang cua so truot (chunk_size, overlap).
    Chunk khong vat qua ranh gioi chuong/muc; cac chunk ke nhau goi len nhau `overlap` tu
    va deu tham chieu cung mot vi tri tu goc, nen khi tim kiem co the gop lai thanh doan thong nhat.
    """
    if not words:
        return []
    chunk_size = max(chunk_size, 10)
    overlap = min(max(overlap, 0), chunk_size // 2)
    step = chunk_size - overlap

    # Ranh gioi cac "run" cung (chapter, section)
    boundaries = sorted({0, len(words), *(h.start for h in headings if 0 < h.start < len(words))})
    heading_starts = [h.start for h in headings]

    def heading_at(position: int) -> tuple[str | None, str | None]:
        i = bisect.bisect_right(heading_starts, position) - 1
        return (headings[i].chapter, headings[i].section) if i >= 0 else (None, None)

    runs = list(zip(boundaries, boundaries[1:]))
    chunks: list[Chunk] = []
    carry_start: int | None = None
    for position, (run_start, run_end) in enumerate(runs):
        start = carry_start if carry_start is not None else run_start
        carry_start = None
        if run_end - start < MIN_RUN_WORDS and position < len(runs) - 1:
            carry_start = start
            continue

        chapter, section = heading_at(run_start)
        for offset in range(start, run_end, step):
            end = min(offset + chunk_size, run_end)
            if offset > start and end - offset <= overlap:
                break
            pages = [w.page for w in words[offset:end]]
            chunks.append(Chunk(
                index=len(chunks),
                start=offset,
                end=end,
                page_number=min(pages),
                page_end=max(pages),
                chapter=chapter,
                section=section,
                text=" ".join(w.text for w in words[offset:end]),
            ))
    return chunks
