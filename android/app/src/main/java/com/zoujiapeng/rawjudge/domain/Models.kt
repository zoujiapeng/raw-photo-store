package com.zoujiapeng.rawjudge.domain

import java.io.Serializable
import java.time.Instant

enum class Partition(val label: String, val code: String, val api: String) {
    GALLERY("展厅", "G", "gallery"),
    REVIEW("评审", "R", "review"),
    WORKSHOP("工坊", "W", "workshop"),
    ARCHIVE("归档", "A", "archive");

    companion object {
        fun fromApi(value: String): Partition = entries.firstOrNull { it.api == value } ?: REVIEW
    }
}

enum class ModerationStatus(val label: String, val api: String) {
    PUBLISHED("已公开", "published"),
    REVIEWING("评审中", "reviewing"),
    NEEDS_RAW("缺 RAW", "needs_raw"),
    REJECTED("已打回", "rejected"),
    APPEALING("申诉中", "appealing");

    companion object {
        fun fromApi(value: String): ModerationStatus =
            entries.firstOrNull { it.api == value } ?: REVIEWING
    }
}

enum class ReviewDimension(val label: String) {
    ALL("全部"), RELEVANCE("相关"), PROFESSIONAL("专业"), TECHNICAL("技术"),
    OBJECTIVE("客观"), CONSTRUCTIVE("建设")
}

enum class LicenseType(val label: String, val api: String) {
    PREVIEW("普通下载", "preview"),
    RAW_STUDY("RAW 学习授权", "raw_study"),
    COMMERCIAL("商业授权", "commercial");

    companion object {
        fun fromApi(value: String): LicenseType =
            entries.firstOrNull { it.api == value } ?: PREVIEW
    }
}

data class ReviewMetrics(
    val relevance: Float,
    val professional: Float,
    val technical: Float,
    val objective: Float,
    val constructive: Float
) : Serializable {
    val average: Float
        get() = (relevance + professional + technical + objective + constructive) / 5f

    fun value(dimension: ReviewDimension): Float = when (dimension) {
        ReviewDimension.ALL -> average
        ReviewDimension.RELEVANCE -> relevance
        ReviewDimension.PROFESSIONAL -> professional
        ReviewDimension.TECHNICAL -> technical
        ReviewDimension.OBJECTIVE -> objective
        ReviewDimension.CONSTRUCTIVE -> constructive
    }

    val strongest: ReviewDimension
        get() = listOf(
            ReviewDimension.RELEVANCE to relevance,
            ReviewDimension.PROFESSIONAL to professional,
            ReviewDimension.TECHNICAL to technical,
            ReviewDimension.OBJECTIVE to objective,
            ReviewDimension.CONSTRUCTIVE to constructive
        ).maxBy { it.second }.first
}

data class Review(
    val id: Long,
    val reviewerUserId: Long? = null,
    val author: String,
    val body: String,
    val score: Int,
    val metrics: ReviewMetrics,
    val createdAt: Instant = Instant.now(),
    val isAi: Boolean = false,
    val reviewerTrust: Float = 0.22f,
    val scoreCounted: Boolean = true
) : Serializable {
    val quality: Float
        get() = (metrics.average * 0.72f + reviewerTrust * 0.28f).coerceIn(0f, 1f)
    val folded: Boolean
        get() = body.trim().length < 8 || quality < 0.32f
}

data class AuditEvent(
    val id: Long,
    val kind: String,
    val message: String,
    val createdAt: Instant = Instant.now()
) : Serializable

data class LicenseGrant(
    val id: Long = 0,
    val type: LicenseType,
    val price: Double,
    val terms: String,
    val granted: Boolean = false
) : Serializable

data class Work(
    val id: Long,
    val ownerId: Long? = null,
    val title: String,
    val description: String,
    val authorName: String,
    val handle: String,
    val externalUrl: String? = null,
    val photoUri: String? = null,
    val imageUrl: String? = null,
    val imageFileName: String? = null,
    val rawFileName: String? = null,
    val rawVerified: Boolean = false,
    val score: Double,
    val confidence: Double,
    val partition: Partition,
    val moderationStatus: ModerationStatus,
    val moderationSummary: String,
    val favorites: Int = 0,
    val downloads: Int = 0,
    val followers: Int = 0,
    val ratings: Int = 0,
    val isFavorite: Boolean = false,
    val isOwner: Boolean = false,
    val isOfficialSample: Boolean = false,
    val canDownloadOriginal: Boolean = false,
    val canDownloadRaw: Boolean = false,
    val reviews: List<Review> = emptyList(),
    val auditTrail: List<AuditEvent> = emptyList(),
    val licenses: List<LicenseGrant> = emptyList(),
    val paletteSeed: Int = id.toInt()
) : Serializable

data class UploadDraft(
    val title: String,
    val description: String,
    val photoUri: String?,
    val imageFileName: String?,
    val rawUri: String?,
    val rawFileName: String?,
    val allowPreviewDownload: Boolean = true,
    val allowRawLicense: Boolean = false,
    val previewPrice: Double = 0.0,
    val rawPrice: Double = 0.0
) : Serializable

data class SessionUser(
    val id: Long,
    val displayName: String,
    val handle: String,
    val externalUrl: String? = null,
    val reviewerTrust: Float = 0.22f
) : Serializable

data class UiSettings(
    val darkMode: Boolean = true,
    val noTextControls: Boolean = false,
    val immersive98: Boolean = false,
    val hintsVisible: Boolean = true,
    val reviewDimension: ReviewDimension = ReviewDimension.ALL
) : Serializable

data class BlindPair(
    val left: Work,
    val right: Work,
    val sequence: Int
) : Serializable

data class AppUiState(
    val works: List<Work> = emptyList(),
    val activePartition: Partition = Partition.REVIEW,
    val settings: UiSettings = UiSettings(),
    val selectedWorkId: Long? = null,
    val blindSequence: Int = 0,
    val blindHistory: Set<String> = emptySet(),
    val currentUser: SessionUser? = null,
    val isLoading: Boolean = false,
    val isOnline: Boolean = false,
    val uploadVisible: Boolean = false,
    val blindVisible: Boolean = false,
    val toastMessage: String? = null
) : Serializable {
    val filteredWorks: List<Work>
        get() = works.filter { it.partition == activePartition }.ifEmpty { works }
    val selectedWork: Work?
        get() = selectedWorkId?.let { id -> works.firstOrNull { it.id == id } }
            ?: filteredWorks.firstOrNull()
}
