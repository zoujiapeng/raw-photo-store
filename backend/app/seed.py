from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .models import AuditEvent, LicenseGrant, Review, Work
from .services.review_quality import analyze_review


SAMPLES = [
    ("雨夜地铁口", 91.4, 0.82, "gallery", "RAW 与成片方向一致；高光压制稳定，画面叙事集中。", "构图上用雨伞和地铁口形成三角关系，主体清楚；建议再压一点右侧广告屏高光。"),
    ("低饱和窗边人像", 78.9, 0.63, "review", "RAW 完整；建议重点讨论色彩、人物视线和背景整理。", "色彩控制克制，窗框把视线导向人物眼神；可以降低背景衣架存在感。"),
    ("山脊晨雾", 69.2, 0.58, "review", "远景层次较好，但主体辨识度不足。", "层次存在，但主体落点弱。建议裁掉下方空白，让山脊线成为明确结构。"),
    ("练习：街角背光", 55.7, 0.51, "workshop", "有光线意识，但背景信息拥挤；适合工坊讨论。", "背光方向不错，但主体与路牌重叠；建议降低机位避开背景干扰。"),
]


def seed_official_samples(db: Session) -> None:
    count = db.scalar(select(func.count(Work.id))) or 0
    if count:
        return
    for index, (title, score, confidence, partition, summary, review_body) in enumerate(SAMPLES, start=1):
        metrics = analyze_review(review_body)
        work = Work(
            title=title,
            description="官方占位作品。正式环境不制造虚假用户、假互动或假购买。",
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
            risk_labels="",
            favorites=(130 - index * 17),
            downloads=index,
            ratings=1,
            official_sample=True,
        )
        work.reviews.append(
            Review(
                reviewer_id=f"closed-test-{index}",
                author_name=f"封闭测试评审 {index:02d}",
                body=review_body,
                score=round(score),
                relevance=metrics.relevance,
                professional=metrics.professional,
                technical=metrics.technical,
                objective=metrics.objective,
                constructive=metrics.constructive,
                reviewer_trust=0.62,
                folded=False,
            )
        )
        work.audit_events.extend(
            [
                AuditEvent(kind="sample", message="官方样例；不是实际用户投稿。"),
                AuditEvent(kind="raw", message="演示数据标记 RAW 验证通过。"),
                AuditEvent(kind="placement", message=f"依据质量分 {score} 与置信度 {confidence} 进入 {partition}。"),
            ]
        )
        work.licenses.extend(
            [
                LicenseGrant(license_type="preview", price=0.8, terms="个人欣赏；禁止商用、转售和 AI 训练。"),
                LicenseGrant(license_type="raw_study", price=1.8, terms="仅学习研究；版权仍归作者。"),
            ]
        )
        db.add(work)
    db.commit()
