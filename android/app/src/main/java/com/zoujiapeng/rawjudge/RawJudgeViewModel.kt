package com.zoujiapeng.rawjudge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zoujiapeng.rawjudge.data.ApiException
import com.zoujiapeng.rawjudge.data.DemoData
import com.zoujiapeng.rawjudge.data.LocalStateStore
import com.zoujiapeng.rawjudge.data.RawJudgeApi
import com.zoujiapeng.rawjudge.data.SessionStore
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.BlindPair
import com.zoujiapeng.rawjudge.domain.LicenseType
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.ReviewDimension
import com.zoujiapeng.rawjudge.domain.UploadDraft
import com.zoujiapeng.rawjudge.domain.Work
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RawJudgeViewModel(application: Application) : AndroidViewModel(application), RawJudgeActions {
    private val store = LocalStateStore(application)
    private val sessionStore = SessionStore(application)
    private val api = RawJudgeApi(application)
    private var persistJob: Job? = null
    private val initial = (store.load() ?: AppUiState(works = DemoData.works())).copy(
        serverUrl = sessionStore.serverUrl
    )
    private val _uiState = MutableStateFlow(initial.copy(isLoading = true))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { bootstrap() }
    }

    override fun selectPartition(partition: Partition) = mutate {
        copy(activePartition = partition, selectedWorkId = null)
    }

    override fun selectWork(id: Long) = mutate { copy(selectedWorkId = id) }
    override fun toggleDarkMode() = mutate { copy(settings = settings.copy(darkMode = !settings.darkMode)) }
    override fun toggleNoText() = mutate { copy(settings = settings.copy(noTextControls = !settings.noTextControls)) }
    override fun toggleImmersive() = mutate { copy(settings = settings.copy(immersive98 = !settings.immersive98)) }
    override fun toggleHints() = mutate { copy(settings = settings.copy(hintsVisible = !settings.hintsVisible)) }
    override fun setReviewDimension(dimension: ReviewDimension) = mutate {
        copy(settings = settings.copy(reviewDimension = dimension))
    }

    override fun refresh() {
        remote("同步完成") {
            val user = api.me()
            val works = api.listWorks()
            mutate {
                copy(
                    works = works,
                    currentUser = user,
                    serverUrl = api.currentServerUrl(),
                    isOnline = true,
                    selectedWorkId = selectedWorkId?.takeIf { id -> works.any { it.id == id } }
                )
            }
        }
    }

    override fun changeServerUrl(serverUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.configureServerUrl(serverUrl)
                sessionStore.clearToken()
                mutate {
                    copy(
                        currentUser = null,
                        serverUrl = api.currentServerUrl(),
                        isOnline = false,
                        isLoading = true,
                        profileVisible = false,
                        toastMessage = "正在连接新后端…"
                    )
                }
                bootstrap()
            } catch (error: Exception) {
                mutate { copy(isLoading = false, toastMessage = friendly(error)) }
            }
        }
    }

    override fun showUpload(show: Boolean) = mutate { copy(uploadVisible = show) }
    override fun showProfile(show: Boolean) = mutate { copy(profileVisible = show) }

    override fun favorite(workId: Long) {
        val work = _uiState.value.works.firstOrNull { it.id == workId } ?: return
        remote {
            val updated = api.favorite(workId, !work.isFavorite)
            replaceWork(updated)
        }
    }

    override fun addReview(workId: Long, body: String, score: Int) {
        if (body.isBlank()) return
        remote("评语已由服务端分析并记录") {
            api.addReview(workId, body.trim(), score.coerceIn(1, 100))
            reloadWorks(workId)
        }
    }

    override fun submitUpload(draft: UploadDraft) {
        remote("上传和初审完成") {
            val uploaded = api.upload(draft)
            val works = api.listWorks()
            mutate {
                copy(
                    works = works,
                    uploadVisible = false,
                    activePartition = uploaded.partition,
                    selectedWorkId = uploaded.id,
                    isOnline = true,
                    toastMessage = uploaded.moderationSummary
                )
            }
        }
    }

    override fun appeal(workId: Long, reason: String) {
        remote("申诉已进入复核队列") {
            api.appeal(workId, reason.ifBlank { "请复核 RAW 验证和审核结果。" })
            reloadWorks(workId)
        }
    }

    override fun report(workId: Long, reason: String) {
        remote("举报已记录") {
            api.report(workId, reason.ifBlank { "请复核该作品的内容与 RAW 认证状态。" })
        }
    }

    override fun purchase(workId: Long, type: LicenseType) {
        remote("授权快照已生成") {
            api.purchase(workId, type)
            reloadWorks(workId)
        }
    }

    override fun download(workId: Long, raw: Boolean) {
        val work = _uiState.value.works.firstOrNull { it.id == workId } ?: return
        val name = if (raw) work.rawFileName ?: "rawjudge-$workId.dng"
        else work.imageFileName ?: "rawjudge-$workId.jpg"
        remote {
            val file = api.download(workId, raw, name)
            mutate { copy(toastMessage = "文件已保存到应用缓存：${file.name}") }
        }
    }

    override fun openBlind() {
        remote {
            val pair = api.blindPair(_uiState.value.blindSequence)
            mutate { copy(blindVisible = true, blindPair = pair) }
        }
    }

    override fun closeBlind() = mutate { copy(blindVisible = false, blindPair = null) }

    override fun blindVote(pair: BlindPair, winnerId: Long?) {
        remote("盲评已记录") {
            api.blindVote(pair, winnerId)
            val nextSequence = pair.sequence + 1
            val nextPair = runCatching { api.blindPair(nextSequence) }.getOrNull()
            val works = api.listWorks()
            mutate {
                copy(
                    works = works,
                    blindSequence = nextSequence,
                    blindPair = nextPair,
                    blindVisible = nextPair != null,
                    toastMessage = if (nextPair == null) "当前没有更多可盲评作品。" else toastMessage
                )
            }
        }
    }

    override fun updateProfile(displayName: String, handle: String, externalUrl: String?) {
        remote("资料已更新") {
            val user = api.updateProfile(displayName.trim(), handle.trim(), externalUrl?.trim())
            val works = api.listWorks()
            mutate { copy(currentUser = user, works = works, profileVisible = false) }
        }
    }

    override fun clearToast() = mutate { copy(toastMessage = null) }

    private suspend fun bootstrap() {
        withContext(Dispatchers.IO) {
            try {
                val existingToken = sessionStore.token
                val user = if (existingToken.isNullOrBlank()) {
                    val session = api.register()
                    sessionStore.token = session.token
                    api.token = session.token
                    session.user
                } else {
                    api.token = existingToken
                    try {
                        api.me()
                    } catch (error: ApiException) {
                        if (error.statusCode != 401) throw error
                        sessionStore.clearToken()
                        val session = api.register()
                        sessionStore.token = session.token
                        api.token = session.token
                        session.user
                    }
                }
                val works = api.listWorks()
                mutate {
                    copy(
                        works = works.ifEmpty { DemoData.works() },
                        currentUser = user,
                        serverUrl = api.currentServerUrl(),
                        isLoading = false,
                        isOnline = true,
                        toastMessage = null
                    )
                }
            } catch (error: Exception) {
                mutate {
                    copy(
                        serverUrl = api.currentServerUrl(),
                        isLoading = false,
                        isOnline = false,
                        works = works.ifEmpty { DemoData.works() },
                        toastMessage = "后端不可用，当前为离线只读：${friendly(error)}"
                    )
                }
            }
        }
    }

    private fun remote(success: String? = null, block: suspend () -> Unit) {
        if (!_uiState.value.isOnline && sessionStore.token.isNullOrBlank()) {
            mutate { copy(toastMessage = "离线状态不能执行此操作；请填写可访问的后端地址。") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            mutate { copy(isLoading = true) }
            try {
                block()
                mutate {
                    copy(
                        isLoading = false,
                        isOnline = true,
                        toastMessage = success ?: toastMessage
                    )
                }
            } catch (error: Exception) {
                mutate {
                    copy(
                        isLoading = false,
                        isOnline = error is ApiException,
                        toastMessage = friendly(error)
                    )
                }
            }
        }
    }

    private fun reloadWorks(selectedId: Long? = _uiState.value.selectedWorkId) {
        val works = api.listWorks()
        mutate {
            copy(
                works = works,
                selectedWorkId = selectedId?.takeIf { id -> works.any { it.id == id } }
            )
        }
    }

    private fun replaceWork(updated: Work) = mutate {
        copy(works = works.map { if (it.id == updated.id) updated else it })
    }

    private fun friendly(error: Exception): String = when (error) {
        is ApiException -> when (error.statusCode) {
            401 -> "会话失效，请重新连接后端。"
            402 -> "这是付费授权，但当前未配置支付服务，未生成虚假购买。"
            403 -> "当前账号没有执行该操作的权限。"
            409 -> error.message ?: "请求与当前状态冲突。"
            else -> error.message ?: "服务器请求失败。"
        }
        is IOException -> "无法连接后端：${error.message ?: "网络错误"}"
        else -> error.message ?: "操作失败"
    }

    private inline fun mutate(block: AppUiState.() -> AppUiState) {
        val next = _uiState.value.block()
        _uiState.value = next
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            store.save(next)
        }
    }
}

interface RawJudgeActions {
    fun selectPartition(partition: Partition)
    fun selectWork(id: Long)
    fun toggleDarkMode()
    fun toggleNoText()
    fun toggleImmersive()
    fun toggleHints()
    fun setReviewDimension(dimension: ReviewDimension)
    fun refresh()
    fun changeServerUrl(serverUrl: String)
    fun showUpload(show: Boolean)
    fun showProfile(show: Boolean)
    fun favorite(workId: Long)
    fun addReview(workId: Long, body: String, score: Int)
    fun submitUpload(draft: UploadDraft)
    fun appeal(workId: Long, reason: String = "")
    fun report(workId: Long, reason: String = "")
    fun purchase(workId: Long, type: LicenseType)
    fun download(workId: Long, raw: Boolean)
    fun openBlind()
    fun closeBlind()
    fun blindVote(pair: BlindPair, winnerId: Long?)
    fun updateProfile(displayName: String, handle: String, externalUrl: String?)
    fun clearToast()
}
