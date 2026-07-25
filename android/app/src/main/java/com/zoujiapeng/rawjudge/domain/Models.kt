package com.zoujiapeng.rawjudge.domain

import java.io.Serializable
import java.time.Instant

enum class Partition(val label: String, val code: String) { GALLERY("展厅", "G"), REVIEW("评审", "R"), WORKSHOP("工坊", "W"), ARCHIVE("归档", "A") }
enum class ModerationStatus(val label: String) { PUBLISHED("已公开"), REVIEWING("评审中"), NEEDS_RAW("缺 RAW"), REJECTED("已打回"), APPEALING("申诉中") }
enum class ReviewDimension(val label: String) { ALL("全部"), RELEVANCE("相关"), PROFESSIONAL("专业"), TECHNICAL("技术"), OBJECTIVE("客观"), CONSTRUCTIVE("建设") }
enum class LicenseType(val label: String) { PREVIEW("普通下载"), RAW_STUDY("RAW 学习授权"), COMMERCIAL("商业授权") }

data class ReviewMetrics(val relevance: Float, val professional: Float, val technical: Float, val objective: Float, val constructive: Float) : Serializable {
    val average: Float get() = (relevance + professional + technical + objective + constructive) / 5f
    fun value(dimension: ReviewDimension): Float = when (dimension) { ReviewDimension.ALL -> average; ReviewDimension.RELEVANCE -> relevance; ReviewDimension.PROFESSIONAL -> professional; ReviewDimension.TECHNICAL -> technical; ReviewDimension.OBJECTIVE -> objective; ReviewDimension.CONSTRUCTIVE -> constructive }
    val strongest: ReviewDimension get() = listOf(ReviewDimension.RELEVANCE to relevance, ReviewDimension.PROFESSIONAL to professional, ReviewDimension.TECHNICAL to technical, ReviewDimension.OBJECTIVE to objective, ReviewDimension.CONSTRUCTIVE to constructive).maxBy { it.second }.first
}
data class Review(val id: Long, val author: String, val body: String, val score: Int, val metrics: ReviewMetrics, val createdAt: Instant = Instant.now(), val isAi: Boolean = false, val reviewerTrust: Float = 0.35f, val scoreCounted: Boolean = true) : Serializable { val quality: Float get() = (metrics.average * 0.72f + reviewerTrust * 0.28f).coerceIn(0f, 1f); val folded: Boolean get() = body.trim().length < 8 || quality < 0.32f }
data class AuditEvent(val id: Long, val kind: String, val message: String, val createdAt: Instant = Instant.now()) : Serializable
data class LicenseGrant(val type: LicenseType, val price: Double, val terms: String, val granted: Boolean = false) : Serializable
data class Work(val id: Long, val title: String, val description: String, val authorName: String, val handle: String, val externalUrl: String? = null, val photoUri: String? = null, val imageFileName: String? = null, val rawFileName: String? = null, val rawVerified: Boolean = false, val score: Double, val confidence: Double, val partition: Partition, val moderationStatus: ModerationStatus, val moderationSummary: String, val favorites: Int = 0, val downloads: Int = 0, val followers: Int = 0, val ratings: Int = 0, val isFavorite: Boolean = false, val isOfficialSample: Boolean = false, val reviews: List<Review> = emptyList(), val auditTrail: List<AuditEvent> = emptyList(), val licenses: List<LicenseGrant> = defaultLicenses(rawAvailable = rawVerified), val paletteSeed: Int = id.toInt()) : Serializable
data class UploadDraft(val title: String, val description: String, val photoUri: String?, val imageFileName: String?, val rawFileName: String?, val allowPreviewDownload: Boolean = true, val allowRawLicense: Boolean = false, val previewPrice: Double = 0.8, val rawPrice: Double = 1.8) : Serializable
data class ModerationResult(val status: ModerationStatus, val rawVerified: Boolean, val score: Double, val confidence: Double, val summary: String, val riskLabels: Set<String>, val audit: List<AuditEvent>) : Serializable
data class UiSettings(val darkMode: Boolean = true, val noTextControls: Boolean = false, val immersive98: Boolean = false, val hintsVisible: Boolean = true, val reviewDimension: ReviewDimension = ReviewDimension.ALL) : Serializable
data class BlindPair(val left: Work, val right: Work, val sequence: Int) : Serializable
data class AppUiState(val works: List<Work> = emptyList(), val activePartition: Partition = Partition.REVIEW, val settings: UiSettings = UiSettings(), val selectedWorkId: Long? = null, val blindSequence: Int = 0, val blindHistory: Set<String> = emptySet(), val userName: String = "本地测试用户", val userHandle: String = "@local-photographer", val externalProfiles: List<String> = listOf("小红书：未绑定", "Instagram：未绑定"), val toastMessage: String? = null) : Serializable { val filteredWorks: List<Work> get() = works.filter { it.partition == activePartition }.ifEmpty { works }; val selectedWork: Work? get() = selectedWorkId?.let { id -> works.firstOrNull { it.id == id } } ?: filteredWorks.firstOrNull() }
fun defaultLicenses(rawAvailable: Boolean): List<LicenseGrant> = listOf(LicenseGrant(LicenseType.PREVIEW, 0.8, "个人欣赏与收藏；禁止商用、转售和 AI 训练。"), LicenseGrant(LicenseType.RAW_STUDY, 1.8, "仅学习研究；版权仍归作者；禁止二次传播。", granted = false)).filter { it.type != LicenseType.RAW_STUDY || rawAvailable }
