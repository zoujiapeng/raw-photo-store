from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Review, User, Work
from .review_quality import Metrics


def place_work(work: Work) -> str:
    if work.moderation_status in {"needs_raw", "rejected", "appealing"}:
        return "archive"
    if work.raw_verified and work.score >= 84 and work.confidence >= 0.70:
        return "gallery"
    if work.raw_verified and work.score >= 64:
        return "review"
    if work.score >= 45:
        return "workshop"
    return "archive"


def reviewer_trust(db: Session, user_id: int) -> float:
    user = db.get(User, user_id)
    if user is None:
        return 0.0
    counted = db.scalar(
        select(func.count(Review.id)).where(
            Review.reviewer_user_id == user_id,
            Review.score_counted.is_(True),
        )
    ) or 0
    return min(0.78, max(0.12, user.reviewer_trust + counted * 0.012))


def apply_review(work: Work, score: int, metrics: Metrics, trust: float) -> None:
    quality = max(0.0, min(1.0, metrics.average * 0.72 + trust * 0.28))
    influence = max(0.004, min(0.045, 0.004 + trust * quality * 0.045))
    work.score = max(1.0, min(99.9, work.score * (1 - influence) + score * influence))
    work.confidence = min(0.98, work.confidence + 0.010 + quality * 0.020)
    work.ratings += 1
    work.partition = place_work(work)


def apply_blind_vote(work: Work, won: bool, weight: float) -> None:
    work.score = max(
        1.0,
        min(99.9, work.score + (1.25 * weight if won else -0.72 * weight)),
    )
    work.confidence = min(0.98, work.confidence + 0.010)
    work.ratings += 1
    work.partition = place_work(work)
