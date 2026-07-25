from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .models import AuditEvent, LicenseGrant, Review, Work
from .services.review_quality import analyze_review

SAMPLES = [
    (
        "雨夜地铁口",
        91.4,
        0.82,
        "gallery",
        "RAW 与成片方向一致；高光压制稳定，画面叙事集中。",
        "AI 样例评语：构图上雨伞和地铁口形成三角关系；可进一步压低右侧高光。",
    ),
    (
        "低饱和窗边人像",
        78.9,
        0.63,
        "review",
        "RAW 完整；建议重点讨论色彩、人物视线和背景整理。",
        "AI 样例评语：窗框将视线引向人物；可降低背景衣架存在感。",
    ),
    (
        "山脊晨雾",
        69.2,
        0.58,
        "review",
        "远景层次较好，但主体辨识度不足。",
        "AI 样例评语：层次存在但落点较弱；可裁掉下方空白强化山脊结构。",
    ),
    (
        "练习：街角背光",
        55.7,
        0.51,
        "workshop",
        "有光线意识，但背景信息拥挤；适合工坊讨论。",
        "AI 样例评语：主体与路牌重叠；可降低机位避开背景干扰。",
    ),
]


def seed_official_samples(db: Session) -> None:
    count = db.scalar(select(func.count(Work.id))) or 0
    if count:
        return
    for index, (title, score, confidence, partition, summary, review_body) in enumerate(
        SAMPLES, start=1
    ):
        metrics = analyze_review(review_body)
        work = Work(
            owner_id=None,
            title=title,
            description="官方界面占位作品；不是实际用户、真实 RAW 或真实互动数据。",
            author_name="RAWJudge 官方样例",
            handle="@rawjudge.sample",
            image_name=f"official-sample-{index}.jpg",
            raw_name=f"official-sample-{index}.dng",
            raw_verified=True,
            score=score,
            confidence=confidence,
            partition=partition,
            moderation_status="published" if partition == "gallery" else "reviewing",
            moderation_summary=summary,
            risk_labels="official_sample",
            favorites=0,
            downloads=0,
            followers=0,
            ratings=0,
            official_sample=True,
        )
        work.reviews.append(
            Review(
                reviewer_user_id=None,
                author_name="AI 初评（官方样例）",
                body=review_body,
                score=round(score),
                relevance=metrics.relevance,
                professional=metrics.professional,
                technical=metrics.technical,
                objective=metrics.objective,
                constructive=metrics.constructive,
                reviewer_trust=0.0,
                is_ai=True,
                folded=False,
                score_counted=False,
            )
        )
        work.audit_events.extend(
            [
                AuditEvent(kind="sample", message="官方样例；不是实际用户投稿。"),
                AuditEvent(kind="raw", message="仅演示 RAW 验证状态，不提供源文件。"),
                AuditEvent(
                    kind="placement",
                    message=f"演示质量分 {score} 与置信度 {confidence}；不参与真实榜单。",
                ),
            ]
        )
        work.licenses.extend(
            [
                LicenseGrant(
                    license_type="preview",
                    price=0.0,
                    terms="官方样例不可购买；仅用于界面演示。",
                ),
                LicenseGrant(
                    license_type="raw_study",
                    price=0.0,
                    terms="官方样例不提供 RAW 文件。",
                ),
            ]
        )
        db.add(work)
    db.commit()
