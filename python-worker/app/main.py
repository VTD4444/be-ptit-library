import logging
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from threading import Thread
from typing import Literal
from uuid import UUID

from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app import embedder, indexer, searcher, vector_store
from app.db import pool
from app.tfidf_index import holder

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("python-worker")

# Boc tach + lap chi muc TF-IDF xu ly tuan tu tung job (rebuild dung toan bo kho)
index_executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="indexer")


@asynccontextmanager
async def lifespan(_: FastAPI):
    pool.open(wait=True)
    try:
        holder.load_or_build(indexer.active_search_config_id())
    except Exception:  # noqa: BLE001
        log.exception("Khong nap/lap duoc mo hinh TF-IDF luc khoi dong")
    # Nap model o luong rieng: lan dau phai tai ve mat vai phut, khong duoc chan TF-IDF
    Thread(target=embedder.preload, name="embedding-loader", daemon=True).start()
    # Kho vector trong RAM: lan dau tai ca kho tu DB (~1 phut), sau do chi tai phan chenh lech so voi file dem
    Thread(target=vector_store.load, name="vector-store-loader", daemon=True).start()
    yield
    index_executor.shutdown(wait=False, cancel_futures=True)
    pool.close()


app = FastAPI(title="PTIT Library Python Worker", lifespan=lifespan)


class CamelModel(BaseModel):
    # Spring/Jackson gui va nhan JSON dang camelCase
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class PdfIndexRequest(CamelModel):
    digital_copy_id: UUID
    document_id: UUID
    file_path: str
    search_config_id: int | None = None
    background_job_id: int


class SearchRequest(CamelModel):
    # Cau hoi / doan van, hoac truy van Boolean: AND, OR, NOT, "cum tu", ( )
    query: str = Field(min_length=1, max_length=20000)
    limit: int = Field(default=10, ge=1, le=200)
    # true: moi tai lieu chi lay doan khop nhat (tim kiem chung); false: moi doan khop (vi tri trong 1 tai lieu)
    best_per_document: bool = True
    # Gioi han trong cac tai lieu nay (bo loc ngon ngu/dang tai lieu/nganh hoc da tinh o Spring)
    document_ids: list[UUID] | None = None
    min_score: float = Field(default=0.0, ge=0.0, le=1.0)
    max_locations: int = Field(default=20, ge=0, le=200)
    # Bo trong thi dung SEARCH_MODE cua worker
    search_mode: Literal["tfidf", "vector", "hybrid"] | None = None


class LocationDto(CamelModel):
    page: int
    printed_page_label: str | None
    paragraph: int
    sentence: int
    word_start: int
    word_end: int
    text: str
    rects: list[list[float]]
    page_width: float
    page_height: float


class PassageDto(CamelModel):
    document_id: str
    digital_copy_id: str
    chunk_ids: list[str]
    score: float
    word_start: int | None
    word_end: int | None
    page_start: int | None
    page_end: int | None
    chapter: str | None
    section: str | None
    snippet: str
    locations: list[LocationDto]
    highlight_terms: list[str]


class SearchResponse(CamelModel):
    boolean_query: bool
    # Che do thuc su da dung: co the khac yeu cau (Boolean, hoac vector loi thi hybrid chi con TF-IDF)
    search_mode: str
    passages: list[PassageDto]
    indexed_chunks: int


@app.get("/health")
def health():
    index = holder.current
    return {"status": "ok", "indexedChunks": index.size, "features": len(index.vocabulary), "builtAt": index.built_at}


@app.post("/api/extract-index", status_code=202)
def extract_index(req: PdfIndexRequest):
    index_executor.submit(indexer.run_indexing, indexer.IndexRequest(
        background_job_id=req.background_job_id,
        digital_copy_id=req.digital_copy_id,
        document_id=req.document_id,
        file_path=req.file_path,
        search_config_id=req.search_config_id,
    ))
    return {"accepted": True, "jobId": req.background_job_id}


@app.post("/api/search", response_model_by_alias=True)
def search(req: SearchRequest) -> SearchResponse:
    index = holder.current
    passages, parsed, mode = searcher.search(
        index,
        req.query,
        limit=req.limit,
        best_per_document=req.best_per_document,
        document_ids={str(d) for d in req.document_ids} if req.document_ids is not None else None,
        min_score=req.min_score,
        max_locations=req.max_locations,
        mode=req.search_mode,
    )
    return SearchResponse(
        boolean_query=parsed.tree is not None,
        search_mode=mode.upper(),
        passages=[PassageDto.model_validate(p) for p in passages],
        indexed_chunks=index.size,
    )


@app.post("/api/index/rebuild", status_code=202)
def rebuild_index():
    """Lap chi muc lai thu cong (vd: sau khi xoa tai lieu, sua tu dien dong nghia, doi cau hinh tim kiem)."""
    def run():
        try:
            holder.rebuild(indexer.active_search_config_id())
        except Exception:  # noqa: BLE001
            log.exception("Lap chi muc TF-IDF thu cong that bai")
        try:
            vector_store.sync()
        except Exception:  # noqa: BLE001
            log.exception("Dong bo kho vector trong RAM that bai")

    index_executor.submit(run)
    return {"accepted": True}


@app.get("/api/embeddings/status")
def embeddings_status():
    return embedder.status()


@app.post("/api/embeddings/backfill", status_code=202)
def backfill_embeddings(force: bool = False):
    """Sinh embedding cho chunk da co ma khong phai upload lai PDF. force=true: sinh lai het (sau khi doi model)."""
    def run():
        try:
            embedder.embed_pending(force=force)
        except Exception:  # noqa: BLE001
            log.exception("Sinh embedding that bai")

    index_executor.submit(run)
    return {"accepted": True, "force": force}
