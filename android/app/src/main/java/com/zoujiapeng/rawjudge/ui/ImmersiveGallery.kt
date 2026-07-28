package com.zoujiapeng.rawjudge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zoujiapeng.rawjudge.RawJudgeActions
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.Review
import com.zoujiapeng.rawjudge.domain.Work

private enum class ViewerPanel { NONE, COMMENTS, DETAILS }

@Composable
internal fun ImmersiveGallery(
    works: List<Work>,
    initialWorkId: Long,
    state: AppUiState,
    actions: RawJudgeActions,
    onDismiss: () -> Unit,
    onReview: (Work) -> Unit,
    onReport: (Work) -> Unit,
    onAppeal: (Work) -> Unit
) {
    val initialIndex = works.indexOfFirst { it.id == initialWorkId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { works.size })
    var chromeVisible by remember(initialWorkId) { mutableStateOf(!state.settings.immersive98) }
    var panel by remember(initialWorkId) { mutableStateOf(ViewerPanel.NONE) }

    LaunchedEffect(pagerState.currentPage, works) {
        works.getOrNull(pagerState.currentPage)?.let { actions.selectWork(it.id) }
        panel = ViewerPanel.NONE
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { works[it].id }
                ) { page ->
                    ZoomableWorkImage(
                        work = works[page],
                        onSingleTap = {
                            if (panel == ViewerPanel.NONE) chromeVisible = !chromeVisible
                        }
                    )
                }

                val current = works[pagerState.currentPage]
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ViewerTopBar(
                            work = current,
                            index = pagerState.currentPage,
                            count = works.size,
                            onDismiss = onDismiss
                        )
                        ViewerBottomChrome(
                            work = current,
                            state = state,
                            actions = actions,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onComments = {
                                panel = if (panel == ViewerPanel.COMMENTS) ViewerPanel.NONE else ViewerPanel.COMMENTS
                            },
                            onReview = { onReview(current) },
                            onDetails = {
                                panel = if (panel == ViewerPanel.DETAILS) ViewerPanel.NONE else ViewerPanel.DETAILS
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = panel != ViewerPanel.NONE,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    when (panel) {
                        ViewerPanel.COMMENTS -> ReviewPanel(
                            work = current,
                            state = state,
                            onClose = { panel = ViewerPanel.NONE },
                            onWrite = { onReview(current) }
                        )

                        ViewerPanel.DETAILS -> DetailPanel(
                            work = current,
                            state = state,
                            actions = actions,
                            onClose = { panel = ViewerPanel.NONE },
                            onReport = { onReport(current) },
                            onAppeal = { onAppeal(current) }
                        )

                        ViewerPanel.NONE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableWorkImage(work: Work, onSingleTap: () -> Unit) {
    var scale by remember(work.id) { mutableFloatStateOf(1f) }
    var offset by remember(work.id) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(work.id) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.35f
                            }
                        }
                    )
                }
                .pointerInput(work.id, widthPx, heightPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                val maxX = widthPx * (newScale - 1f) / 2f
                                val maxY = heightPx * (newScale - 1f) / 2f
                                val next = offset + event.calculatePan()
                                scale = newScale
                                offset = if (newScale <= 1.01f) {
                                    Offset.Zero
                                } else {
                                    Offset(
                                        x = next.x.coerceIn(-maxX, maxX),
                                        y = next.y.coerceIn(-maxY, maxY)
                                    )
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            WorkImage(
                work = work,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
                showErrorLabel = true,
                backgroundColor = Color.Black
            )
        }
    }
}

@Composable
private fun ViewerTopBar(work: Work, index: Int, count: Int, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerCircleButton(Icons.Outlined.ArrowBack, "返回", onDismiss)
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Text(
                "${index + 1}/$count",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (work.rawVerified) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "RAW",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ViewerCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }
}

@Composable
private fun ViewerBottomChrome(
    work: Work,
    state: AppUiState,
    actions: RawJudgeActions,
    modifier: Modifier = Modifier,
    onComments: () -> Unit,
    onReview: () -> Unit,
    onDetails: () -> Unit
) {
    val canWrite = state.isOnline && !state.isLoading && !work.isOfficialSample && work.id > 0
    Box(
        modifier
            .fillMaxWidth()
            .height(310.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.93f))
                )
            )
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            work.authorName.firstOrNull()?.uppercase() ?: "R",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(work.authorName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(work.handle, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
                }
                Text(
                    "%.1f".format(work.score),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                work.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (work.description.isNotBlank()) {
                Text(
                    work.description,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${work.partition.label} · C ${"%.2f".format(work.confidence)} · ${work.ratings} 次有效评分",
                color = Color.White.copy(alpha = 0.52f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerAction(
                    icon = if (work.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = viewerCompactCount(work.favorites),
                    selected = work.isFavorite,
                    enabled = canWrite,
                    onClick = { actions.favorite(work.id) }
                )
                ViewerAction(
                    icon = Icons.Outlined.Comment,
                    label = work.reviews.size.toString(),
                    onClick = onComments
                )
                ViewerAction(
                    icon = Icons.Outlined.StarOutline,
                    label = if (state.settings.noTextControls) "＋" else "评语",
                    enabled = canWrite,
                    onClick = onReview
                )
                ViewerAction(
                    icon = Icons.Outlined.Info,
                    label = if (state.settings.noTextControls) "···" else "详情",
                    onClick = onDetails
                )
            }
        }
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> Color.White.copy(alpha = 0.28f)
                selected -> Color(0xFFFF5A64)
                else -> Color.White
            },
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            color = if (enabled) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.28f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun ReviewPanel(
    work: Work,
    state: AppUiState,
    onClose: () -> Unit,
    onWrite: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 610.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 24.dp
    ) {
        Column(Modifier.navigationBarsPadding()) {
            PanelHeader("评语 ${work.reviews.size}", onClose)
            if (work.reviews.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("还没有有效评语", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(work.reviews, key = { it.id }) { review -> ReviewItem(review) }
                }
            }
            Button(
                onClick = onWrite,
                enabled = state.isOnline && !state.isLoading && !work.isOfficialSample,
                modifier = Modifier.fillMaxWidth().padding(14.dp).height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("写一条有证据的评语")
            }
        }
    }
}

@Composable
private fun ReviewItem(review: Review) {
    Surface(
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (review.folded) 0.45f else 0.82f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    review.author,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Text(
                    "${review.score} · ${review.metrics.strongest.label}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Text(
                review.body,
                fontSize = (11 + review.quality * 3).sp,
                lineHeight = (16 + review.quality * 3).sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (review.folded) 0.55f else 1f)
            )
            if (!review.scoreCounted) {
                Text("未计入质量分", color = MaterialTheme.colorScheme.error, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DetailPanel(
    work: Work,
    state: AppUiState,
    actions: RawJudgeActions,
    onClose: () -> Unit,
    onReport: () -> Unit,
    onAppeal: () -> Unit
) {
    val canWrite = state.isOnline && !state.isLoading && work.id > 0
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 330.dp, max = 650.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 24.dp
    ) {
        Column(Modifier.navigationBarsPadding()) {
            PanelHeader("作品详情", onClose)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("审核说明", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(
                            work.moderationSummary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DetailLine("分区", work.partition.label)
                            DetailLine("审核状态", work.moderationStatus.label)
                            DetailLine("RAW", if (work.rawVerified) "已验证 · ${work.rawFileName ?: "原始文件"}" else "未通过验证")
                            DetailLine("质量置信度", "${"%.2f".format(work.confidence)}")
                            DetailLine("下载", work.downloads.toString())
                        }
                    }
                }
                if (work.licenses.isNotEmpty() || work.canDownloadOriginal || work.canDownloadRaw) {
                    item {
                        Text("授权与下载", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            work.licenses.distinctBy { it.type }.forEach { grant ->
                                val label = when {
                                    grant.granted -> "${grant.type.label} 已授权"
                                    grant.price == 0.0 -> "${grant.type.label} 免费"
                                    else -> "${grant.type.label} ¥${"%.2f".format(grant.price)}"
                                }
                                OutlinedButton(
                                    onClick = { actions.purchase(work.id, grant.type) },
                                    enabled = canWrite && !work.isOwner && !grant.granted
                                ) { Text(label, fontSize = 10.sp) }
                            }
                            if (work.canDownloadOriginal) {
                                OutlinedButton(onClick = { actions.download(work.id, false) }, enabled = state.isOnline) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("成片")
                                }
                            }
                            if (work.canDownloadRaw) {
                                OutlinedButton(onClick = { actions.download(work.id, true) }, enabled = state.isOnline) {
                                    Text("RAW")
                                }
                            }
                        }
                    }
                }
                if (work.auditTrail.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("审核轨迹", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            work.auditTrail.takeLast(3).forEach { event ->
                                Text(
                                    "${event.kind} · ${event.message}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReport, enabled = canWrite) {
                            Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("举报")
                        }
                        if (work.isOwner) {
                            OutlinedButton(onClick = onAppeal, enabled = canWrite) { Text("申诉") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭")
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(
            value,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun viewerCompactCount(value: Int): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}
