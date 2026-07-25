package com.zoujiapeng.rawjudge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zoujiapeng.rawjudge.RawJudgeActions
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.LicenseType
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.Review
import com.zoujiapeng.rawjudge.domain.UploadDraft
import com.zoujiapeng.rawjudge.domain.Work
import kotlinx.coroutines.delay

@Composable
fun RawJudgeApp(state: AppUiState, actions: RawJudgeActions) {
    if (state.blindVisible) {
        BlindReviewScreen(state, actions)
        return
    }

    val selected = state.selectedWork
    var reviewTarget by remember { mutableStateOf<Work?>(null) }
    var reportTarget by remember { mutableStateOf<Work?>(null) }
    var appealTarget by remember { mutableStateOf<Work?>(null) }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(4500)
            actions.clearToast()
        }
    }

    if (state.settings.immersive98 && selected != null) {
        ImmersiveWork(selected, state, actions)
    } else {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Header(state, actions)
            PartitionRow(state, actions)
            selected?.let { work ->
                WorkHero(
                    work = work,
                    state = state,
                    actions = actions,
                    onReview = { reviewTarget = work },
                    onReport = { reportTarget = work },
                    onAppeal = { appealTarget = work }
                )
            }
            Text("作品", fontWeight = FontWeight.SemiBold)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.filteredWorks, key = { it.id }) { work ->
                    WorkRow(work, selected?.id == work.id) { actions.selectWork(work.id) }
                }
            }
        }
    }

    if (state.isLoading) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    state.toastMessage?.let { message ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp
        ) {
            Text(message, modifier = Modifier.padding(14.dp), fontSize = 12.sp)
        }
    }

    if (state.uploadVisible) UploadDialog(state, actions)
    if (state.profileVisible) ProfileDialog(state, actions)
    reviewTarget?.let { work ->
        ReviewDialog(work, onDismiss = { reviewTarget = null }) { body, score ->
            actions.addReview(work.id, body, score)
            reviewTarget = null
        }
    }
    reportTarget?.let { work ->
        ReasonDialog("举报作品", "请说明具体风险或违规点。", onDismiss = { reportTarget = null }) {
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

@Composable
private fun Header(state: AppUiState, actions: RawJudgeActions) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("RAWJudge", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (state.isOnline) "在线 · ${state.currentUser?.handle ?: "已连接"}" else "离线只读",
                color = if (state.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = actions::refresh) { Text("刷新") }
            TextButton(onClick = { actions.showProfile(true) }) { Text("我") }
            Button(
                onClick = { actions.showUpload(true) },
                enabled = state.isOnline && !state.isLoading
            ) { Text("发布") }
        }
    }
}

@Composable
private fun PartitionRow(state: AppUiState, actions: RawJudgeActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Partition.entries.forEach { partition ->
            val active = partition == state.activePartition
            Surface(
                modifier = Modifier.clickable { actions.selectPartition(partition) },
                shape = RoundedCornerShape(30.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    if (state.settings.noTextControls) partition.code else "${partition.label} ${state.works.count { it.partition == partition }}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
        OutlinedButton(
            onClick = actions::openBlind,
            enabled = state.isOnline && !state.isLoading
        ) { Text(if (state.settings.noTextControls) "◐" else "盲评") }
    }
}

@Composable
private fun WorkHero(
    work: Work,
    state: AppUiState,
    actions: RawJudgeActions,
    onReview: () -> Unit,
    onReport: () -> Unit,
    onAppeal: () -> Unit
) {
    val canWrite = state.isOnline && !state.isLoading && work.id > 0
    Surface(shape = RoundedCornerShape(26.dp), tonalElevation = 1.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                WorkImage(work, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                ) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "%.1f".format(work.score),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "C ${"%.2f".format(work.confidence)} · ${work.partition.code}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
                if (work.isOfficialSample) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("官方样例 · 无真实互动", Modifier.padding(8.dp), fontSize = 10.sp)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(work.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("${work.authorName} · ${work.handle}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(work.moderationSummary, fontSize = 11.sp, lineHeight = 16.sp)
                Text(
                    "RAW ${if (work.rawVerified) "ok" else "miss"} · fav ${work.favorites} · review ${work.ratings} · dl ${work.downloads}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { actions.favorite(work.id) },
                    enabled = canWrite && !work.isOfficialSample
                ) { Text(if (work.isFavorite) "取消收藏" else "收藏") }
                OutlinedButton(onClick = onReview, enabled = canWrite && !work.isOfficialSample) { Text("评语") }
                OutlinedButton(onClick = onReport, enabled = canWrite) { Text("举报") }
                if (work.isOwner) OutlinedButton(onClick = onAppeal, enabled = canWrite) { Text("申诉") }
                OutlinedButton(onClick = actions::toggleImmersive) { Text("98%") }
            }
            LicenseActions(work, state, actions)
            if (work.reviews.isNotEmpty()) {
                HorizontalDivider()
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("高质量评语", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    work.reviews.filterNot { it.folded }.take(3).forEach { ReviewPreview(it) }
                    val folded = work.reviews.count { it.folded }
                    if (folded > 0) Text("$folded 条低信息评语已折叠", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LicenseActions(work: Work, state: AppUiState, actions: RawJudgeActions) {
    if (work.isOfficialSample) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        work.licenses.distinctBy { it.type }.forEach { grant ->
            val label = when {
                grant.granted -> "${grant.type.label} 已获授权"
                grant.price == 0.0 -> "${grant.type.label} 免费"
                else -> "${grant.type.label} ¥${"%.2f".format(grant.price)}"
            }
            OutlinedButton(
                onClick = { actions.purchase(work.id, grant.type) },
                enabled = state.isOnline && !work.isOwner && !grant.granted
            ) { Text(label, fontSize = 10.sp) }
        }
        if (work.canDownloadOriginal) {
            OutlinedButton(onClick = { actions.download(work.id, false) }, enabled = state.isOnline) { Text("下载成片") }
        }
        if (work.canDownloadRaw) {
            OutlinedButton(onClick = { actions.download(work.id, true) }, enabled = state.isOnline) { Text("下载 RAW") }
        }
    }
}

@Composable
private fun ReviewPreview(review: Review) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${review.author} · ${review.score} · ${review.metrics.strongest.label}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                review.body,
                fontSize = (11 + review.quality * 4).sp,
                lineHeight = (15 + review.quality * 4).sp
            )
            if (!review.scoreCounted) Text("未计入质量分", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun WorkRow(work: Work, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkImage(work, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                Text(work.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${work.partition.label} · ${work.moderationStatus.label} · RAW ${if (work.rawVerified) "ok" else "miss"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("%.1f".format(work.score), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ImmersiveWork(work: Work, state: AppUiState, actions: RawJudgeActions) {
    Box(Modifier.fillMaxSize()) {
        WorkImage(work, Modifier.fillMaxSize())
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                Text("%.1f".format(work.score), fontFamily = FontFamily.Monospace, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("${work.partition.code} · C ${"%.2f".format(work.confidence)}", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
        Button(
            onClick = actions::toggleImmersive,
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) { Text(if (state.settings.noTextControls) "×" else "退出") }
    }
}

@Composable
private fun UploadDialog(state: AppUiState, actions: RawJudgeActions) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var imageName by remember { mutableStateOf<String?>(null) }
    var rawUri by remember { mutableStateOf<String?>(null) }
    var rawName by remember { mutableStateOf<String?>(null) }
    var allowPreview by remember { mutableStateOf(true) }
    var allowRaw by remember { mutableStateOf(false) }
    var previewPrice by remember { mutableStateOf("0") }
    var rawPrice by remember { mutableStateOf("0") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persist(context, it)
            imageUri = it.toString()
            imageName = displayName(context, it)
        }
    }
    val rawPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persist(context, it)
            rawUri = it.toString()
            rawName = displayName(context, it)
        }
    }

    Dialog(
        onDismissRequest = { actions.showUpload(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("发表作品", style = MaterialTheme.typography.titleLarge)
                Text("公开作品必须同时选择可解码成片和文件头有效的 RAW。", fontSize = 11.sp)
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("拍摄、后期边界和作品说明") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { imagePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                        modifier = Modifier.weight(1f)
                    ) { Text(imageName ?: "选择成片", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(
                        onClick = { rawPicker.launch(arrayOf("application/octet-stream", "image/x-adobe-dng", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text(rawName ?: "选择 RAW", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                ToggleRow("开放成片授权", allowPreview) { allowPreview = it }
                if (allowPreview) OutlinedTextField(previewPrice, { previewPrice = it }, label = { Text("成片价格，0 表示免费") })
                ToggleRow("开放 RAW 学习授权", allowRaw) { allowRaw = it }
                if (allowRaw) OutlinedTextField(rawPrice, { rawPrice = it }, label = { Text("RAW 价格，0 表示免费") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { actions.showUpload(false) }) { Text("取消") }
                    Button(
                        onClick = {
                            actions.submitUpload(
                                UploadDraft(
                                    title = title,
                                    description = description,
                                    photoUri = imageUri,
                                    imageFileName = imageName,
                                    rawUri = rawUri,
                                    rawFileName = rawName,
                                    allowPreviewDownload = allowPreview,
                                    allowRawLicense = allowRaw,
                                    previewPrice = previewPrice.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                    rawPrice = rawPrice.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                )
                            )
                        },
                        enabled = state.isOnline && imageUri != null && !state.isLoading
                    ) { Text("上传并初审") }
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(state: AppUiState, actions: RawJudgeActions) {
    val user = state.currentUser
    var name by remember(user?.id) { mutableStateOf(user?.displayName.orEmpty()) }
    var handle by remember(user?.id) { mutableStateOf(user?.handle.orEmpty()) }
    var external by remember(user?.id) { mutableStateOf(user?.externalUrl.orEmpty()) }
    AlertDialog(
        onDismissRequest = { actions.showProfile(false) },
        title = { Text("账号与界面") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (state.isOnline) "Bearer 会话已连接" else "离线只读", fontSize = 11.sp)
                OutlinedTextField(name, { name = it }, label = { Text("显示名称") })
                OutlinedTextField(handle, { handle = it }, label = { Text("用户名") })
                OutlinedTextField(external, { external = it }, label = { Text("其他平台 HTTPS 入口") })
                ToggleRow("暗色模式", state.settings.darkMode) { actions.toggleDarkMode() }
                ToggleRow("无文字控件", state.settings.noTextControls) { actions.toggleNoText() }
                ToggleRow("提示", state.settings.hintsVisible) { actions.toggleHints() }
            }
        },
        confirmButton = {
            Button(
                onClick = { actions.updateProfile(name, handle, external.ifBlank { null }) },
                enabled = state.isOnline && name.isNotBlank() && handle.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { actions.showProfile(false) }) { Text("关闭") } }
    )
}

@Composable
private fun ReviewDialog(work: Work, onDismiss: () -> Unit, submit: (String, Int) -> Unit) {
    var body by remember { mutableStateOf("") }
    var score by remember { mutableFloatStateOf(work.score.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写评语") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("评分 ${score.toInt()} · 极端分需要具体证据；作者自评不会计权。", fontSize = 11.sp)
                Slider(score, { score = it }, valueRange = 1f..100f)
                OutlinedTextField(
                    body,
                    { body = it },
                    label = { Text("构图、色彩、技术、客观证据和改进建议") },
                    minLines = 4
                )
            }
        },
        confirmButton = { Button(onClick = { submit(body, score.toInt()) }, enabled = body.isNotBlank()) { Text("提交") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ReasonDialog(title: String, hint: String, onDismiss: () -> Unit, submit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, fontSize = 11.sp)
                OutlinedTextField(reason, { reason = it }, minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { submit(reason) }, enabled = reason.trim().length >= 3) { Text("提交") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = change)
    }
}

private fun persist(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return uri.lastPathSegment ?: "selected.file"
}
