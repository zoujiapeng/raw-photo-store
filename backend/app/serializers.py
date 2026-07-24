from __future__ import annotations

from .models import AuditEvent, LicenseGrant, Review, Work
from .schemas import AuditOut, LicenseOut, ReviewMetricsOut, ReviewOut, WorkOut


def review_out(review: Review) -> ReviewOut:
    average = (review.relevance + review.professional + review.technical + review.objective + review.constructive) / 5
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
        reviewer_id=review.reviewer_id,
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


def audit_out(event: AuditEvent) -> AuditOut:
    return AuditOut(id=event.id, kind=event.kind, message=event.message, actor_id=event.actor_id, created_at=event.created_at)


def license_out(grant: LicenseGrant) -> LicenseOut:
    return LicenseOut(
        id=grant.id,
        license_type=grant.license_type,
        price=grant.price,
        terms=grant.terms,
        buyer_id=grant.buyer_id,
        granted=grant.granted,
        granted_at=grant.granted_at,
    )


def work_out(work: Work, hide_identity: bool = False) -> WorkOut:
    return WorkOut(
        id=work.id,
        title=work.title,
        description=work.description,
        author_name="隐藏" if hide_identity else work.author_name,
        handle="@blind" if hide_identity else work.handle,
        external_url=None if hide_identity else work.external_url,
        image_name=work.image_name,
        image_url=f"/v1/works/{work.id}/image" if work.image_path else None,
        raw_name=work.raw_name,
        raw_verified=work.raw_verified,
        score=work.score,
        confidence=work.confidence,
        partition=work.partition,
        moderation_status=work.moderation_status,
        moderation_summary=work.moderation_summary,
        risk_labels=[item for item in work.risk_labels.split(",") if item],
        favorites=0 if hide_identity else work.favorites,
        downloads=0 if hide_identity else work.downloads,
        followers=0 if hide_identity else work.followers,
        ratings=work.ratings,
        official_sample=work.official_sample,
        created_at=work.created_at,
        reviews=[] if hide_identity else [review_out(review) for review in sorted(work.reviews, key=lambda item: item.id or 0, reverse=True)],
        audit=[] if hide_identity else [audit_out(event) for event in sorted(work.audit_events, key=lambda item: item.id or 0)],
        licenses=[] if hide_identity else [license_out(grant) for grant in work.licenses],
    )
