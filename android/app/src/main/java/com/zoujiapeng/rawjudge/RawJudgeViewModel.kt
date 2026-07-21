package com.zoujiapeng.rawjudge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zoujiapeng.rawjudge.data.DemoData
import com.zoujiapeng.rawjudge.data.LocalStateStore
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.AuditEvent
import com.zoujiapeng.rawjudge.domain.BlindPair
import com.zoujiapeng.rawjudge.domain.LicenseType
import com.zoujiapeng.rawjudge.domain.ModerationEngine
import com.zoujiapeng.rawjudge.domain.ModerationStatus
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.PartitionPolicy
import com.zoujiapeng.rawjudge.domain.Review
import com.zoujiapeng.rawjudge.domain.ReviewAnalyzer
import com.zoujiapeng.rawjudge.domain.ReviewDimension
import com.zoujiapeng.rawjudge.domain.UploadDraft
import com.zoujiapeng.rawjudge.domain.Work
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class RawJudgeViewModel(application: Application) : AndroidViewModel(application), RawJudgeActions {
    private val store = LocalStateStore(application)
    private var persistJob: Job? = null
    private val _uiState = MutableStateFlow(store.load() ?: AppUiState(works = DemoData.works()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    override fun selectPartition(partition: Partition) = mutate { copy(activePartition = partition, selectedWorkId = null) }
    override fun selectWork(id: Long) = mutate { copy(selectedWorkId = id) }

    override fun toggleDarkMode() = mutate { copy(settings = settings.copy(darkMode = !settings.darkMode)) }
    override fun toggleNoText() = mutate { copy(settings = settings.copy(noTextControls = !settings.noTextControls)) }
    override fun toggleImmersive() = mutate { copy(settings = settings.copy(immersive98 = !settings.immersive98)) }
    override fun toggleHints() = mutate { copy(settings = settings.copy(hintsVisible = !settings.hintsVisible)) }
    override fun setReviewDimension(dimension: ReviewDimension) = mutate { copy(settings = settings.copy(reviewDimension = dimension)) }

    override fun favorite(workId: Long) = updateWork(workId) { work ->
        work.copy(
            isFavorite = !work.isFavorite,
            favorites = if (work.isFavorite) max(0, work.favorites - 1) else work.favorites + 1,
            auditTrail = work.auditTrail + AuditEvent(nowId(), "favorite", "收藏状态改变；该数据不进入质量分。")
        )
    }

    override fun addReview(workId: Long, body: String, score: Int) {
        if (body.isBlank()) return
        updateWork(workId) { work ->
            val normalizedScore = score.coerceIn(1, 100)
            val metrics = ReviewAnalyzer.analyze(body)
            val extreme = normalizedScore <= 10 || normalizedScore >= 95
            val sufficientlySpecific = body.trim().length >= 40 && metrics.average >= 0.45f
            val provisional = Review(
                id = nowId(),
                author = _uiState.value.userName,
                body = body.trim(),
                score = normalizedScore,
                metrics = metrics,
                reviewerTrust = 0.28f
            )
            val review = provisional.copy(
                scoreCounted = !provisional.folded && (!extreme || sufficientlySpecific)
            )
            val newScore = PartitionPolicy.weightedScore(work, review)
            val updated = work.copy(
                score = newScore,
                confidence = if (review.scoreCounted) (work.confidence + 0.010 + review.quality * 0.020).coerceAtMost(0.98) else work.confidence,
                ratings = work.ratings + if (review.scoreCounted) 1 else 0,
                reviews = listOf(review) + work.reviews,
                auditTrail = work.auditTrail + AuditEvent(
                    nowId(),
                    "review",
                    "评语被归类为 ${review.metrics.strongest.label}，质量权重 ${"%.2f".format(review.quality)}；" +
                        if (review.scoreCounted) "评分已计入。" else "低信息或无依据极端分未计入质量分。"
                )
            )
            updated.copy(partition = PartitionPolicy.place(updated))
        }
    }

    override fun submitUpload(draft: UploadDraft): Long {
        val id = (_uiState.value.works.maxOfOrNull { it.id } ?: 0L) + 1L
        val result = ModerationEngine.moderate(draft, id)
        var work = Work(
            id = id,
            title = draft.title.ifBlank { draft.imageFileName ?: "未命名作品" },
            description = draft.description,
            authorName = _uiState.value.userName,
            handle = _uiState.value.userHandle,
            photoUri = draft.photoUri,
            imageFileName = draft.imageFileName,
            rawFileName = draft.rawFileName,
            rawVerified = result.rawVerified,
            score = result.score,
            confidence = result.confidence,
            partition = Partition.REVIEW,
            moderationStatus = result.status,
            moderationSummary = result.summary,
            reviews = listOf(
                Review(
                    id = nowId(),
                    author = "AI 初评（已标注）",
                    body = "系统已完成文件规则检查。下一步由盲评和高质量评论更新质量分与置信度。",
                    score = result.score.toInt(),
                    metrics = ReviewAnalyzer.analyze("建议评审具体讨论构图、色彩、曝光、叙事和可改进方向。"),
                    isAi = true,
                    reviewerTrust = 0.35f,
                    scoreCounted = false
                )
            ),
            auditTrail = result.audit,
            licenses = buildList {
                if (draft.allowPreviewDownload) add(com.zoujiapeng.rawjudge.domain.LicenseGrant(LicenseType.PREVIEW, draft.previewPrice, "个人欣赏；禁止商用、转售和 AI 训练。"))
                if (draft.allowRawLicense && result.rawVerified) add(com.zoujiapeng.rawjudge.domain.LicenseGrant(LicenseType.RAW_STUDY, draft.rawPrice, "仅学习研究；版权仍归作者。"))
            },
            paletteSeed = id.toInt()
        )
        work = work.copy(partition = PartitionPolicy.place(work))
        mutate {
            copy(
                works = listOf(work) + works,
                activePartition = work.partition,
                selectedWorkId = work.id,
                toastMessage = result.summary
            )
        }
        return id
    }

    override fun appeal(workId: Long) = updateWork(workId) { work ->
        val updated = work.copy(
            moderationStatus = ModerationStatus.APPEALING,
            auditTrail = work.auditTrail + AuditEvent(nowId(), "appeal", "作者提交申诉；等待高信誉评审或人工复核。")
        )
        updated.copy(partition = PartitionPolicy.place(updated))
    }

    override fun report(workId: Long) = updateWork(workId) { work ->
        work.copy(auditTrail = work.auditTrail + AuditEvent(nowId(), "report", "收到举报；已记录账号、时间和行为轨迹，等待复核。"))
    }

    override fun purchase(workId: Long, type: LicenseType) = updateWork(workId) { work ->
        val offering = work.licenses.firstOrNull { it.type == type }
        when {
            offering == null -> work
            offering.granted -> work.copy(
                auditTrail = work.auditTrail + AuditEvent(nowId(), "license", "重复授权请求被幂等忽略。")
            )
            else -> work.copy(
                downloads = work.downloads + 1,
                licenses = work.licenses.map { if (it.type == type) it.copy(granted = true) else it },
                auditTrail = work.auditTrail + AuditEvent(nowId(), "license", "生成 ${type.label} 的不可变授权快照；版权仍归作者。")
            )
        }
    }

    override fun blindPair(): BlindPair? {
        val candidates = _uiState.value.works.filter { it.rawVerified && it.moderationStatus !in setOf(ModerationStatus.REJECTED, ModerationStatus.NEEDS_RAW) }
        if (candidates.size < 2) return null
        val sequence = _uiState.value.blindSequence
        return BlindPair(candidates[sequence % candidates.size], candidates[(sequence + 1) % candidates.size], sequence)
    }

    override fun blindVote(pair: BlindPair, winnerId: Long?) = mutate {
        val key = listOf(pair.left.id, pair.right.id).sorted().joinToString(":")
        val skipped = winnerId == null
        val duplicate = !skipped && key in blindHistory
        val voterTrust = if (duplicate) 0.0 else 0.22
        val updatedWorks = works.map { work ->
            when (work.id) {
                pair.left.id -> blindUpdate(work, winnerId == pair.left.id, skipped, voterTrust, duplicate)
                pair.right.id -> blindUpdate(work, winnerId == pair.right.id, skipped, voterTrust, duplicate)
                else -> work
            }
        }
        copy(
            works = updatedWorks,
            blindSequence = blindSequence + 1,
            blindHistory = if (skipped) blindHistory else blindHistory + key,
            toastMessage = if (duplicate) "同一作品对的重复盲评权重已归零。" else toastMessage
        )
    }

    override fun clearToast() = mutate { copy(toastMessage = null) }

    private fun blindUpdate(work: Work, won: Boolean, skipped: Boolean, trust: Double, duplicate: Boolean): Work {
        if (skipped) return work
        if (duplicate) return work.copy(
            auditTrail = work.auditTrail + AuditEvent(nowId(), "anti_abuse", "同一账号重复比较同一作品对，评分权重归零。")
        )
        val delta = if (won) 1.25 * trust else -0.72 * trust
        val updated = work.copy(
            score = (work.score + delta).coerceIn(1.0, 99.9),
            confidence = (work.confidence + 0.010).coerceAtMost(0.98),
            ratings = work.ratings + 1,
            auditTrail = work.auditTrail + AuditEvent(nowId(), "blind_vote", "完成一次盲评；作者、粉丝和收藏数未展示。")
        )
        return updated.copy(partition = PartitionPolicy.place(updated))
    }

    private fun updateWork(id: Long, transform: (Work) -> Work) = mutate {
        copy(works = works.map { if (it.id == id) transform(it) else it })
    }

    private inline fun mutate(block: AppUiState.() -> AppUiState) {
        val next = _uiState.value.block()
        _uiState.value = next
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(120)
            store.save(next)
        }
    }

    private fun nowId(): Long = System.nanoTime()
}

interface RawJudgeActions {
    fun selectPartition(partition: Partition)
    fun selectWork(id: Long)
    fun toggleDarkMode()
    fun toggleNoText()
    fun toggleImmersive()
    fun toggleHints()
    fun setReviewDimension(dimension: ReviewDimension)
    fun favorite(workId: Long)
    fun addReview(workId: Long, body: String, score: Int)
    fun submitUpload(draft: UploadDraft): Long
    fun appeal(workId: Long)
    fun report(workId: Long)
    fun purchase(workId: Long, type: LicenseType)
    fun blindPair(): BlindPair?
    fun blindVote(pair: BlindPair, winnerId: Long?)
    fun clearToast()
}
