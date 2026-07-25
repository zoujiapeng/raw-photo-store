from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    display_name: Mapped[str] = mapped_column(String(120))
    handle: Mapped[str] = mapped_column(String(120), unique=True, index=True)
    external_url: Mapped[str | None] = mapped_column(String(500), nullable=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    reviewer_trust: Mapped[float] = mapped_column(Float, default=0.22)
    is_admin: Mapped[bool] = mapped_column(Boolean, default=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    works: Mapped[list[Work]] = relationship(back_populates="owner")


class Work(Base):
    __tablename__ = "works"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    owner_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    title: Mapped[str] = mapped_column(String(180))
    description: Mapped[str] = mapped_column(Text, default="")
    author_name: Mapped[str] = mapped_column(String(120))
    handle: Mapped[str] = mapped_column(String(120), default="@anonymous")
    external_url: Mapped[str | None] = mapped_column(String(500), nullable=True)
    image_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    preview_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    image_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    image_sha256: Mapped[str | None] = mapped_column(String(64), nullable=True)
    raw_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    raw_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    raw_sha256: Mapped[str | None] = mapped_column(String(64), nullable=True)
    raw_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    score: Mapped[float] = mapped_column(Float, default=50.0)
    confidence: Mapped[float] = mapped_column(Float, default=0.3)
    partition: Mapped[str] = mapped_column(String(24), default="review")
    moderation_status: Mapped[str] = mapped_column(String(32), default="reviewing")
    moderation_summary: Mapped[str] = mapped_column(Text, default="")
    risk_labels: Mapped[str] = mapped_column(Text, default="")
    favorites: Mapped[int] = mapped_column(Integer, default=0)
    downloads: Mapped[int] = mapped_column(Integer, default=0)
    followers: Mapped[int] = mapped_column(Integer, default=0)
    ratings: Mapped[int] = mapped_column(Integer, default=0)
    official_sample: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    owner: Mapped[User | None] = relationship(back_populates="works")
    reviews: Mapped[list[Review]] = relationship(
        back_populates="work", cascade="all, delete-orphan", lazy="selectin"
    )
    audit_events: Mapped[list[AuditEvent]] = relationship(
        back_populates="work", cascade="all, delete-orphan", lazy="selectin"
    )
    licenses: Mapped[list[LicenseGrant]] = relationship(
        back_populates="work", cascade="all, delete-orphan", lazy="selectin"
    )
    favorite_records: Mapped[list[Favorite]] = relationship(
        back_populates="work", cascade="all, delete-orphan", lazy="selectin"
    )


class Review(Base):
    __tablename__ = "reviews"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_id: Mapped[int] = mapped_column(
        ForeignKey("works.id", ondelete="CASCADE"), index=True
    )
    reviewer_user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    author_name: Mapped[str] = mapped_column(String(120))
    body: Mapped[str] = mapped_column(Text)
    score: Mapped[int] = mapped_column(Integer)
    relevance: Mapped[float] = mapped_column(Float)
    professional: Mapped[float] = mapped_column(Float)
    technical: Mapped[float] = mapped_column(Float)
    objective: Mapped[float] = mapped_column(Float)
    constructive: Mapped[float] = mapped_column(Float)
    reviewer_trust: Mapped[float] = mapped_column(Float, default=0.25)
    is_ai: Mapped[bool] = mapped_column(Boolean, default=False)
    folded: Mapped[bool] = mapped_column(Boolean, default=False)
    score_counted: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    work: Mapped[Work] = relationship(back_populates="reviews")


class Favorite(Base):
    __tablename__ = "favorites"
    __table_args__ = (
        UniqueConstraint("work_id", "user_id", name="uq_favorite_work_user"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_id: Mapped[int] = mapped_column(
        ForeignKey("works.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    work: Mapped[Work] = relationship(back_populates="favorite_records")


class AuditEvent(Base):
    __tablename__ = "audit_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_id: Mapped[int] = mapped_column(
        ForeignKey("works.id", ondelete="CASCADE"), index=True
    )
    kind: Mapped[str] = mapped_column(String(80))
    message: Mapped[str] = mapped_column(Text)
    actor_id: Mapped[str | None] = mapped_column(String(120), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    work: Mapped[Work] = relationship(back_populates="audit_events")


class VoteEvent(Base):
    __tablename__ = "vote_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    voter_user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    left_work_id: Mapped[int] = mapped_column(Integer)
    right_work_id: Mapped[int] = mapped_column(Integer)
    winner_work_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    weight: Mapped[float] = mapped_column(Float)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, index=True
    )


class LicenseGrant(Base):
    __tablename__ = "license_grants"
    __table_args__ = (
        UniqueConstraint(
            "work_id", "license_type", "buyer_user_id", name="uq_license_buyer"
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_id: Mapped[int] = mapped_column(
        ForeignKey("works.id", ondelete="CASCADE"), index=True
    )
    license_type: Mapped[str] = mapped_column(String(40))
    price: Mapped[float] = mapped_column(Float)
    terms: Mapped[str] = mapped_column(Text)
    buyer_user_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), nullable=True, index=True
    )
    granted: Mapped[bool] = mapped_column(Boolean, default=False)
    granted_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    work: Mapped[Work] = relationship(back_populates="licenses")


class Report(Base):
    __tablename__ = "reports"
    __table_args__ = (
        UniqueConstraint("work_id", "reporter_user_id", name="uq_report_work_user"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_id: Mapped[int] = mapped_column(Integer, index=True)
    reporter_user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    reason: Mapped[str] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(40), default="open")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Appeal(Base):
    __tablename__ = "appeals"
    __table_args__ = (
        UniqueConstraint("work_id", "author_user_id", "status", name="uq_open_appeal"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    work_id: Mapped[int] = mapped_column(Integer, index=True)
    author_user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    reason: Mapped[str] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(40), default="pending")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
