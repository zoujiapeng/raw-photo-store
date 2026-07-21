package com.zoujiapeng.rawjudge.data

import com.zoujiapeng.rawjudge.domain.AuditEvent
import com.zoujiapeng.rawjudge.domain.ModerationStatus
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.Review
import com.zoujiapeng.rawjudge.domain.ReviewAnalyzer
import com.zoujiapeng.rawjudge.domain.Work

object DemoData {
    fun works(): List<Work> = listOf(
        sample(
            id = 1,
            title = "雨夜地铁口",
            author = "RAWJudge 官方样例",
            handle = "@rawjudge.sample",
            score = 91.4,
            confidence = 0.82,
            partition = Partition.GALLERY,
            summary = "RAW 与成片方向一致；高光压制稳定，画面叙事集中。",
            followers = 0,
            favorites = 128,
            reviews = listOf(
                review(11, "封闭测试评审 01", "构图上用雨伞和地铁口形成三角关系，主体清楚；建议再压一点右侧广告屏高光，色彩会更稳定。", 92, 0.78f),
                review(12, "AI 初评（已标注）", "RAW 细节保留完整，暗部没有过度拉亮，雨夜影调统一。建议公开 EXIF 方便技术讨论。", 90, 0.35f, isAi = true),
                review(13, "封闭测试评审 02", "好看", 100, 0.50f)
            )
        ),
        sample(
            id = 2,
            title = "低饱和窗边人像",
            author = "RAWJudge 官方样例",
            handle = "@rawjudge.sample",
            score = 78.9,
            confidence = 0.63,
            partition = Partition.REVIEW,
            summary = "RAW 完整；建议评审重点讨论色彩、人物视线和背景整理。",
            favorites = 88,
            reviews = listOf(
                review(21, "封闭测试评审 03", "色彩控制克制，窗框把视线导向人物眼神；可以降低背景衣架存在感，主体会更干净。", 82, 0.72f),
                review(22, "AI 初评（已标注）", "镜头焦段压缩空间自然，曝光略保守，但 RAW 仍有足够高光和肤色调整余量。", 78, 0.35f, isAi = true)
            )
        ),
        sample(
            id = 3,
            title = "山脊晨雾",
            author = "RAWJudge 官方样例",
            handle = "@rawjudge.sample",
            score = 69.2,
            confidence = 0.58,
            partition = Partition.REVIEW,
            summary = "RAW 与成片一致；远景层次较好，但主体辨识度不足。",
            favorites = 41,
            reviews = listOf(review(31, "封闭测试评审 04", "层次存在，但主体落点弱。建议裁掉下方空白，让山脊线成为更明确的视觉结构。", 70, 0.68f))
        ),
        sample(
            id = 4,
            title = "练习：街角背光",
            author = "RAWJudge 官方样例",
            handle = "@rawjudge.sample",
            score = 55.7,
            confidence = 0.51,
            partition = Partition.WORKSHOP,
            summary = "成片有光线意识，但背景信息拥挤；适合工坊讨论。",
            favorites = 9,
            reviews = listOf(review(41, "封闭测试评审 05", "背光方向不错，但主体与路牌重叠。可以等人物向前一步，或降低机位避开背景干扰。", 58, 0.64f))
        ),
        Work(
            id = 5,
            title = "缺 RAW 的官方规则样例",
            description = "用于说明审核流程；不是伪造用户内容。",
            authorName = "RAWJudge 官方样例",
            handle = "@rawjudge.sample",
            rawVerified = false,
            score = 38.2,
            confidence = 0.24,
            partition = Partition.ARCHIVE,
            moderationStatus = ModerationStatus.NEEDS_RAW,
            moderationSummary = "作品暂未公开：没有附带 RAW，不能进入认证分区。",
            isOfficialSample = true,
            auditTrail = listOf(
                AuditEvent(501, "sample", "官方规则样例，所有互动数据均为演示数据。"),
                AuditEvent(502, "moderation", "打回原因：RAW 缺失。")
            ),
            paletteSeed = 5
        )
    )

    private fun sample(
        id: Long,
        title: String,
        author: String,
        handle: String,
        score: Double,
        confidence: Double,
        partition: Partition,
        summary: String,
        followers: Int = 0,
        favorites: Int,
        reviews: List<Review>
    ) = Work(
        id = id,
        title = title,
        description = "官方占位作品。正式环境只保留明确标注的官方样例，不制造虚假用户。",
        authorName = author,
        handle = handle,
        rawFileName = "sample-$id.dng",
        rawVerified = true,
        score = score,
        confidence = confidence,
        partition = partition,
        moderationStatus = if (partition == Partition.GALLERY) ModerationStatus.PUBLISHED else ModerationStatus.REVIEWING,
        moderationSummary = summary,
        favorites = favorites,
        followers = followers,
        ratings = reviews.size,
        isOfficialSample = true,
        reviews = reviews,
        auditTrail = listOf(
            AuditEvent(id * 100 + 1, "sample", "官方样例；不是实际用户投稿。"),
            AuditEvent(id * 100 + 2, "raw", "RAW hash 与扩展名已在演示数据中标记为通过。"),
            AuditEvent(id * 100 + 3, "placement", "依据质量分 $score 与置信度 $confidence 进入 ${partition.label}。")
        ),
        paletteSeed = id.toInt()
    )

    private fun review(id: Long, author: String, body: String, score: Int, trust: Float, isAi: Boolean = false) = Review(
        id = id,
        author = author,
        body = body,
        score = score,
        metrics = ReviewAnalyzer.analyze(body),
        isAi = isAi,
        reviewerTrust = trust
    )
}
