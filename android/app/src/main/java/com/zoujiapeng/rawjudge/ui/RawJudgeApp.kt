package com.zoujiapeng.rawjudge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoujiapeng.rawjudge.RawJudgeActions
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.Work
import kotlinx.coroutines.delay

@Composable
fun RawJudgeApp(state: AppUiState, actions: RawJudgeActions) {
    var viewerWorkId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewerOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var reviewTarget by remember { mutableStateOf<Work?>(null) }
    var reportTarget by remember { mutableStateOf<Work?>(null) }
    var appealTarget by remember { mutableStateOf<Work?>(null) }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(4500)
            actions.clearToast()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.blindVisible) {
            BlindReviewScreen(state, actions)
        } else {
            PhotographyFeed(
                state = state,
                actions = actions,
                onOpenWork = { work, orderedWorks ->
                    actions.selectWork(work.id)
                    viewerOrder = orderedWorks.map { it.id }
                    viewerWorkId = work.id
                }
            )
        }

        AnimatedVisibility(
            visible = state.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 14.dp
            ) {
                CircularProgressIndicator(Modifier.padding(18.dp))
            }
        }

        AnimatedVisibility(
            visible = state.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            state.toastMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, bottom = 90.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 12.dp
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }

    viewerWorkId?.let { initialId ->
        val ordered = viewerOrder.mapNotNull { id -> state.works.firstOrNull { it.id == id } }
            .ifEmpty { state.filteredWorks }
        if (ordered.isNotEmpty()) {
            ImmersiveGallery(
                works = ordered,
                initialWorkId = initialId,
                state = state,
                actions = actions,
                onDismiss = { viewerWorkId = null },
                onReview = { work ->
                    viewerWorkId = null
                    reviewTarget = work
                },
                onReport = { work ->
                    viewerWorkId = null
                    reportTarget = work
                },
                onAppeal = { work ->
                    viewerWorkId = null
                    appealTarget = work
                }
            )
        }
    }

    if (state.uploadVisible) UploadDialog(state, actions)
    if (state.profileVisible) {
        ProfileDialog(
            state = state,
            actions = actions,
            onOpenWork = { work, works ->
                actions.showProfile(false)
                actions.selectWork(work.id)
                viewerOrder = works.map { it.id }
                viewerWorkId = work.id
            }
        )
    }

    reviewTarget?.let { work ->
        ReviewDialog(work, onDismiss = { reviewTarget = null }) { body, score ->
            actions.addReview(work.id, body, score)
            reviewTarget = null
        }
    }
    reportTarget?.let { work ->
        ReasonDialog("举报作品", "请说明具体风险、侵权或违规点。", onDismiss = { reportTarget = null }) {
            actions.report(work.id, it)
            reportTarget = null
        }
    }
    appealTarget?.let { work ->
        ReasonDialog("提交申诉", "说明 RAW、生成式处理或审核结果需要复核的原因。", onDismiss = { appealTarget = null }) {
            actions.appeal(work.id, it)
            appealTarget = null
        }
    }
}
