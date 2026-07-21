package com.zoujiapeng.rawjudge.domain

import kotlin.math.absoluteValue

object ModerationEngine {
    private val rawExtensions = setOf("dng", "cr2", "cr3", "nef", "arw", "rw2", "orf", "raf", "raw", "pef")
    private val generatedHints = setOf("midjourney", "stable diffusion", "comfyui", "ai生成", "生成式", "prompt", "flux model")
    private val regionSensitiveHints = setOf("时政", "公共议题", "竞选", "政治标语", "敏感旗帜", "public election", "campaign rally")
    fun moderate(draft: UploadDraft, nextId: Long): ModerationResult {
        val combined = listOf(draft.title, draft.description, draft.imageFileName.orEmpty(), draft.rawFileName.orEmpty()).joinToString(" ").lowercase()
        val rawExtension = draft.rawFileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        val rawOk = rawExtension in rawExtensions
        val generatedRisk = generatedHints.any(combined::contains)
        val regionalRisk = regionSensitiveHints.any(combined::contains)
        val status = when { !rawOk -> ModerationStatus.NEEDS_RAW; generatedRisk || regionalRisk -> ModerationStatus.REJECTED; else -> ModerationStatus.REVIEWING }
        val deterministic = (combined.hashCode() xor nextId.hashCode()).absoluteValue
        val baseScore = 61.0 + (deterministic % 2800) / 100.0
        val score = (baseScore + if (rawOk) 4.0 else -24.0 - if (generatedRisk) 18.0 else 0.0 - if (regionalRisk) 12.0 else 0.0).coerceIn(5.0, 96.0)
        val confidence = when (status) { ModerationStatus.REVIEWING -> 0.46; ModerationStatus.NEEDS_RAW -> 0.24; ModerationStatus.REJECTED -> 0.31; else -> 0.40 }
        val labels = buildSet { if (!rawOk) add("raw_missing_or_unsupported"); if (generatedRisk) add("generated_image_suspected"); if (regionalRisk) add("regional_policy_review") }
        val summary = when { !rawOk -> "作品暂未公开：没有检测到受支持的 RAW 文件。请补充 DNG、CR2/CR3、NEF、ARW、RAF 等原始文件。"; generatedRisk -> "系统未能确认成片与 RAW 的一致性。检测到生成式处理线索；可重新上传、标记为 AI 辅助作品或提交申诉。"; regionalRisk -> "作品触发地区内容规则复核，当前不进入公开分区。可选择仅自己可见、调整后重投或提交申诉。"; else -> "AI 初审通过：RAW 文件格式有效，作品进入盲评池。初始分只用于分配评审，不直接决定最终分区。" }
        val events = listOf(AuditEvent(nextId * 100 + 1, "upload", "已记录成片 ${draft.imageFileName ?: "未命名"}。"), AuditEvent(nextId * 100 + 2, "raw", if (rawOk) "RAW 扩展名验证通过：${draft.rawFileName}" else "RAW 缺失或格式不支持。"), AuditEvent(nextId * 100 + 3, "moderation", summary))
        return ModerationResult(status, rawOk && !generatedRisk, score, confidence, summary, labels, events)
    }
}
