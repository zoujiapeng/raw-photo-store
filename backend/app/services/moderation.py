from __future__ import annotations

from dataclasses import dataclass

from .ai_provider import ai_provider


@dataclass(frozen=True)
class Moderation:
    status: str
    raw_verified: bool
    score: float
    confidence: float
    summary: str
    labels: list[str]


async def moderate(
    title: str,
    description: str,
    image_name: str,
    raw_name: str | None,
    image_hash: str,
    raw_hash: str | None,
    raw_signature_valid: bool,
) -> Moderation:
    raw_ok = bool(raw_name and raw_hash and raw_signature_valid)
    text = f"{title} {description} {image_name} {raw_name or ''}".lower()
    generated_hint = any(
        keyword in text
        for keyword in (
            "midjourney",
            "stable diffusion",
            "ai生成",
            "生成式",
            "comfyui",
            "prompt",
        )
    )
    if not raw_ok:
        return Moderation(
            "needs_raw",
            False,
            35.0,
            0.25,
            "未确认可读取的 RAW 文件头；请上传与扩展名匹配的相机原始文件。",
            ["raw_missing_or_invalid"],
        )
    if generated_hint:
        return Moderation(
            "rejected",
            False,
            30.0,
            0.35,
            "检测到生成式处理线索，当前作品不能进入 RAW 认证公开分区；可提交申诉。",
            ["generated_image_suspected"],
        )
    score = 68 + (int(image_hash[:4], 16) % 1800) / 100
    confidence = 0.48
    labels: list[str] = []
    summary = "RAW 文件头与扩展名匹配，进入盲评；这仍不等同于真实性已最终证明。"
    enrichment = await ai_provider.assess_metadata(title, description, raw_name)
    if enrichment:
        score = max(1, min(99, enrichment.quality_score))
        confidence = max(confidence, min(0.65, enrichment.confidence * 0.65))
        summary += f" AI 初评：{enrichment.summary}"
        labels.extend(enrichment.labels)
    return Moderation("reviewing", True, score, confidence, summary, labels)
