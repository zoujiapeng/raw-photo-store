package com.zoujiapeng.rawjudge.data

import com.zoujiapeng.rawjudge.domain.AuditEvent
import com.zoujiapeng.rawjudge.domain.ModerationStatus
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.Review
import com.zoujiapeng.rawjudge.domain.ReviewMetrics
import com.zoujiapeng.rawjudge.domain.Work

object DemoData {
    fun works(): List<Work> = listOf(
        sample(1, "雨夜地铁口", 91.4, 0.82, Partition.GALLERY, "高光压制稳定，画面叙事集中。"),
        sample(2, "低饱和窗边人像", 78.9, 0.63, Partition.REVIEW, "建议重点讨论色彩、人物视线和背景整理。"),
        sample(3, "山脊晨雾", 69.2, 0.58, Partition.REVIEW, "远景层次较好，但主体辨识度不足。"),
        sample(4, "练习：街角背光", 55.7, 0.51, Partition.WORKSHOP, "有光线意识，但背景信息拥挤。")
    )

    private fun sample(
        id: Long,
        title: String,
        score: Double,
        confidence: Double,
        partition: Partition,
        summary: String
    ): Work = Work(
        id = -id,
        title = title,
        description = "离线官方占位图；不是实际用户、真实 RAW 或真实互动数据。",
        authorName = "RAWJudge 离线样例",
        handle = "@rawjudge.offline",
        rawFileName = "offline-sample-$id.dng",
        rawVerified = true,
        score = score,
        confidence = confidence,
        partition = partition,
        moderationStatus = if (partition == Partition.GALLERY) {
            ModerationStatus.PUBLISHED
        } else {
            ModerationStatus.REVIEWING
        },
        moderationSummary = summary,
        favorites = 0,
        downloads = 0,
        followers = 0,
        ratings = 0,
        isOfficialSample = true,
        reviews = listOf(
            Review(
                id = -id,
                reviewerUserId = null,
                author = "AI 初评（离线样例）",
                body = "这是一条明确标注的离线占位评语，不计入真实评分。",
                score = score.toInt(),
                metrics = ReviewMetrics(0.5f, 0.5f, 0.4f, 0.6f, 0.5f),
                isAi = true,
                reviewerTrust = 0f,
                scoreCounted = false
            )
        ),
        auditTrail = listOf(
            AuditEvent(-id, "sample", "离线官方样例；所有社交数字均为零。")
        ),
        paletteSeed = id.toInt()
    )
}
