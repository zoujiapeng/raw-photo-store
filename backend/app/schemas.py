from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ReviewMetricsOut(BaseModel):
    relevance: float
    professional: float
    technical: float
    objective: float
    constructive: float
    quality: float
    strongest: str


class ReviewCreate(BaseModel):
    reviewer_id: str = Field(min_length=1, max_length=120)
    author_name: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=4000)
    score: int = Field(ge=1, le=100)


class ReviewOut(BaseModel):
    id: int
    reviewer_id: str
    author_name: str
    body: str
    score: int
    metrics: ReviewMetricsOut
    reviewer_trust: float
    is_ai: bool
    folded: bool
    score_counted: bool
    created_at: datetime


class AuditOut(BaseModel):
    id: int
    kind: str
    message: str
    actor_id: str | None
    created_at: datetime


class LicenseOut(BaseModel):
    id: int
    license_type: str
    price: float
    terms: str
    buyer_id: str | None
    granted: bool
    granted_at: datetime | None


class WorkOut(BaseModel):
    id: int
    title: str
    description: str
    author_name: str
    handle: str
    external_url: str | None
    image_name: str | None
    image_url: str | None
    raw_name: str | None
    raw_verified: bool
    score: float
    confidence: float
    partition: str
    moderation_status: str
    moderation_summary: str
    risk_labels: list[str]
    favorites: int
    downloads: int
    followers: int
    ratings: int
    official_sample: bool
    created_at: datetime
    reviews: list[ReviewOut]
    audit: list[AuditOut]
    licenses: list[LicenseOut]


class FavoriteRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=120)
    favorite: bool = True


class BlindPairOut(BaseModel):
    left: WorkOut
    right: WorkOut
    sequence: int


class BlindVoteRequest(BaseModel):
    voter_id: str = Field(min_length=1, max_length=120)
    left_work_id: int
    right_work_id: int
    winner_work_id: int | None = None


class AppealCreate(BaseModel):
    author_id: str = Field(min_length=1, max_length=120)
    reason: str = Field(min_length=3, max_length=4000)


class ReportCreate(BaseModel):
    reporter_id: str = Field(min_length=1, max_length=120)
    reason: str = Field(min_length=3, max_length=4000)


class LicensePurchase(BaseModel):
    buyer_id: str = Field(min_length=1, max_length=120)


class MessageOut(BaseModel):
    message: str


class HealthOut(BaseModel):
    status: str
    database: str
    ai_mode: str
