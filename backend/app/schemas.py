from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field, HttpUrl, field_validator


class AnonymousAuthCreate(BaseModel):
    display_name: str = Field(default="摄影者", min_length=1, max_length=120)
    handle: str | None = Field(default=None, max_length=120)


class UserUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=120)
    handle: str | None = Field(default=None, min_length=2, max_length=120)
    external_url: HttpUrl | None = None


class UserOut(BaseModel):
    id: int
    display_name: str
    handle: str
    external_url: str | None
    reviewer_trust: float
    is_admin: bool
    created_at: datetime


class AuthOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class ReviewMetricsOut(BaseModel):
    relevance: float
    professional: float
    technical: float
    objective: float
    constructive: float
    quality: float
    strongest: str


class ReviewCreate(BaseModel):
    body: str = Field(min_length=1, max_length=4000)
    score: int = Field(ge=1, le=100)


class ReviewOut(BaseModel):
    id: int
    reviewer_user_id: int | None
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
    buyer_user_id: int | None
    granted: bool
    granted_at: datetime | None


class WorkOut(BaseModel):
    id: int
    owner_id: int | None
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
    is_owner: bool
    is_favorite: bool
    can_download_original: bool
    can_download_raw: bool
    created_at: datetime
    reviews: list[ReviewOut]
    audit: list[AuditOut]
    licenses: list[LicenseOut]


class BlindWorkOut(BaseModel):
    id: int
    image_url: str | None
    raw_verified: bool
    confidence: float


class FavoriteRequest(BaseModel):
    favorite: bool = True


class BlindPairOut(BaseModel):
    left: BlindWorkOut
    right: BlindWorkOut
    sequence: int


class BlindVoteRequest(BaseModel):
    left_work_id: int
    right_work_id: int
    winner_work_id: int | None = None


class AppealCreate(BaseModel):
    reason: str = Field(min_length=3, max_length=4000)


class ReportCreate(BaseModel):
    reason: str = Field(min_length=3, max_length=4000)


class AdminModerationUpdate(BaseModel):
    moderation_status: str = Field(pattern="^(reviewing|published|rejected|needs_raw)$")
    summary: str = Field(min_length=3, max_length=4000)


class MessageOut(BaseModel):
    message: str


class HealthOut(BaseModel):
    status: str
    database: str
    ai_mode: str


class ReadyOut(BaseModel):
    status: str
    database: str
