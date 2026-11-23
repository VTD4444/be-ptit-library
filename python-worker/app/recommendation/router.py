"""FastAPI router cho phan he Goi y tai lieu."""

from uuid import UUID

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app import config
from app.recommendation.ease_service import predict_for_user, rebuild_ease_model
from app.recommendation.similar_books import (
    DEFAULT_MIN_SCORE,
    DEFAULT_MODEL_VERSION,
    DEFAULT_TOP_K,
    rebuild_similarities,
)

router = APIRouter(prefix="/api/recommendations", tags=["Recommendation"])


class _CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SimilarRebuildRequest(_CamelModel):
    model_version: str = DEFAULT_MODEL_VERSION
    min_score: float = Field(default=DEFAULT_MIN_SCORE, ge=0.0, le=1.0)
    top_k: int = Field(default=DEFAULT_TOP_K, ge=1, le=50)


class SimilarRebuildResponse(_CamelModel):
    model_version: str
    documents_processed: int
    pairs_saved: int
    duration_ms: float


class EaseRebuildRequest(_CamelModel):
    model_version: str = config.EASE_MODEL_VERSION
    reg_lambda: float = Field(default=config.EASE_LAMBDA, gt=0.0)
    cache_top_k: int = Field(default=config.EASE_CACHE_TOP_K, ge=1, le=50)
    cache_ttl_hours: int = Field(default=config.EASE_CACHE_TTL_HOURS, ge=1, le=720)
    only_available: bool = False


class EaseRebuildResponse(_CamelModel):
    model_version: str
    reg_lambda: float
    users_count: int
    documents_count: int
    positive_pairs: int
    cached_rows: int
    duration_ms: float


class EasePredictRequest(_CamelModel):
    user_id: UUID
    limit: int = Field(default=10, ge=1, le=50)
    only_available: bool = False


class EasePredictItemDto(_CamelModel):
    document_id: str
    score: float
    rank: int
    reason: str
    rec_model_version: str


class EasePredictResponse(_CamelModel):
    model_ready: bool
    model_version: str
    personalization_enabled: bool
    items: list[EasePredictItemDto]


@router.post("/similar/rebuild", response_model=SimilarRebuildResponse, response_model_by_alias=True)
def rebuild_similar_books(req: SimilarRebuildRequest | None = None) -> SimilarRebuildResponse:
    params = req or SimilarRebuildRequest()
    stats = rebuild_similarities(
        model_version=params.model_version,
        min_score=params.min_score,
        top_k=params.top_k,
    )
    return SimilarRebuildResponse(
        model_version=stats.model_version,
        documents_processed=stats.documents_processed,
        pairs_saved=stats.pairs_saved,
        duration_ms=stats.duration_ms,
    )


@router.post("/me/rebuild", response_model=EaseRebuildResponse, response_model_by_alias=True)
def rebuild_personal_ease(req: EaseRebuildRequest | None = None) -> EaseRebuildResponse:
    params = req or EaseRebuildRequest()
    stats = rebuild_ease_model(
        model_version=params.model_version,
        reg_lambda=params.reg_lambda,
        cache_top_k=params.cache_top_k,
        cache_ttl_hours=params.cache_ttl_hours,
        only_available=params.only_available,
    )
    return EaseRebuildResponse(
        model_version=stats.model_version,
        reg_lambda=stats.reg_lambda,
        users_count=stats.users_count,
        documents_count=stats.documents_count,
        positive_pairs=stats.positive_pairs,
        cached_rows=stats.cached_rows,
        duration_ms=stats.duration_ms,
    )


@router.post("/me/predict", response_model=EasePredictResponse, response_model_by_alias=True)
def predict_personal_recommendations(req: EasePredictRequest) -> EasePredictResponse:
    res = predict_for_user(
        user_id=str(req.user_id),
        limit=req.limit,
        only_available=req.only_available,
    )
    return EasePredictResponse(
        model_ready=res.model_ready,
        model_version=res.model_version,
        personalization_enabled=res.personalization_enabled,
        items=[
            EasePredictItemDto(
                document_id=it.document_id,
                score=it.score,
                rank=it.rank,
                reason=it.reason,
                rec_model_version=it.rec_model_version,
            )
            for it in res.items
        ],
    )
