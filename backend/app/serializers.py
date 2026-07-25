from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import Principal
from .models import AuditEvent, Favorite, LicenseGrant, Review, User, Work
from .schemas import (
    AuditOut,
    BlindWorkOut,
    LicenseOut,
    ReviewMetricsOut,
    ReviewOut,
    UserOut,
    WorkOut,
)


def user_out(user: User) -> UserOut:
    return UserOut(
        id=user.id,
        display_name=user.display_name,
        handle=user.handle,
        external_url=user.external_url,
        reviewer_trust=user.reviewer_trust,
        is_admin=user.is_admin,
        created_at=user.created_at,
    )


def review_out(review: Review) -> ReviewOut:
    average = (
        review.relevance
        + review.professional
        + review.technical
        + review.objective
        + review.constructive
    ) / 5
    quality = max(0.0, min(1.0, average * 0.72 + review.reviewer_trust * 0.28))
    values = {
        "relevance": review.relevance,
        "professional": review.professional,
        "technical": review.technical,
        "objective": review.objective,
        "constructive": review.constructive,
    }
    return ReviewOut(
        id=review.id,
        reviewer_user_id=review.reviewer_user_id,
        author_name=review.author_name,
        body=review.body,
        score=review.score,
        metrics=ReviewMetricsOut(
            relevance=review.relevance,
            professional=review.professional,
            technical=review.technical,
            objective=review.objective,
            constructive=review.constructive,
            quality=quality,
            strongest=max(values, key=values.get),
        ),
        reviewer_trust=review.reviewer_trust,
        is_ai=review.is_ai,
        folded=review.folded,
        score_counted=review.score_counted,
        created_at=review.created_at,
    )


def audit_out(event: AuditEvent, reveal_actor: bool) -> AuditOut:
    return AuditOut(
        id=event.id,
        kind=event.kind,
        message=event.message,
        actor_id=event.actor_id if reveal_actor else None,
        created_at=event.created_at,
    )


def license_out(grant: LicenseGrant) -> LicenseOut:
    return LicenseOut(
        id=grant.id,
        license_type=grant.license_type,
        price=grant.price,
        terms=grant.terms,
        buyer_user_id=grant.buyer_user_id,
        granted=grant.granted,
        granted_at=grant.granted_at,
    )


def blind_work_out(work: Work) -> BlindWorkOut:
    return BlindWorkOut(
        id=work.id,
        image_url=f"/v1/works/{work.id}/preview" if work.preview_path else None,
        raw_verified=work.raw_verified,
        confidence=work.confidence,
    )


def work_out(
    work: Work,
    principal: Principal | None = None,
    db: Session | None = None,
) -> WorkOut:
    user_id = principal.user_id if principal else None
    is_owner = bool(
        principal
        and (principal.is_admin or (user_id is not None and work.owner_id == user_id))
    )
    is_favorite = False
    grants: list[LicenseGrant] = []
    if db is not None and user_id is not None:
        is_favorite = (
            db.scalar(
                select(Favorite).where(
                    Favorite.work_id == work.id,
                    Favorite.user_id == user_id,
                )
            )
            is not None
        )
        grants = list(
            db.scalars(
                select(LicenseGrant).where(
                    LicenseGrant.work_id == work.id,
                    LicenseGrant.buyer_user_id == user_id,
                    LicenseGrant.granted.is_(True),
                )
            ).all()
        )
    offers = [item for item in work.licenses if item.buyer_user_id is None]
    visible_licenses = offers + grants
    can_download_original = is_owner or any(
        item.license_type == "preview" and item.granted for item in grants
    )
    can_download_raw = is_owner or any(
        item.license_type == "raw_study" and item.granted for item in grants
    )
    return WorkOut(
        id=work.id,
        owner_id=work.owner_id,
        title=work.title,
        description=work.description,
        author_name=work.author_name,
        handle=work.handle,
        external_url=work.external_url,
        image_name=work.image_name,
        image_url=f"/v1/works/{work.id}/preview" if work.preview_path else None,
        raw_name=work.raw_name,
        raw_verified=work.raw_verified,
        score=work.score,
        confidence=work.confidence,
        partition=work.partition,
        moderation_status=work.moderation_status,
        moderation_summary=work.moderation_summary,
        risk_labels=[item for item in work.risk_labels.split(",") if item],
        favorites=work.favorites,
        downloads=work.downloads,
        followers=work.followers,
        ratings=work.ratings,
        official_sample=work.official_sample,
        is_owner=is_owner,
        is_favorite=is_favorite,
        can_download_original=can_download_original,
        can_download_raw=can_download_raw,
        created_at=work.created_at,
        reviews=[
            review_out(review)
            for review in sorted(work.reviews, key=lambda item: item.id or 0, reverse=True)
        ],
        audit=[
            audit_out(event, reveal_actor=is_owner)
            for event in sorted(work.audit_events, key=lambda item: item.id or 0)
        ],
        licenses=[license_out(grant) for grant in visible_licenses],
    )
