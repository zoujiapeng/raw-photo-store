package com.zoujiapeng.rawjudge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zoujiapeng.rawjudge.RawJudgeActions
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.ModerationStatus
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.UploadDraft
import com.zoujiapeng.rawjudge.domain.Work

private enum class ProfileTab { WORKS, FAVORITES, SETTINGS }

@Composable
internal fun UploadDialog(state: AppUiState, actions: RawJudgeActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var imageName by rememberSaveable { mutableStateOf<String?>(null) }
    var rawUri by rememberSaveable { mutableStateOf<String?>(null) }
    var rawName by rememberSaveable { mutableStateOf<String?>(null) }
    var allowPreview by rememberSaveable { mutableStateOf(true) }
    var allowRaw by rememberSaveable { mutableStateOf(false) }
    var previewPrice by rememberSaveable { mutableStateOf("0") }
    var rawPrice by rememberSaveable { mutableStateOf("0") }

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
    val canSubmit = state.isOnline && !state.isLoading &&
        title.isNotBlank() && imageUri != null && rawUri != null

    val previewWork = imageUri?.let {
        Work(
            id = -9_999,
            title = title.ifBlank { "作品预览" },
            description = description,
            authorName = state.currentUser?.displayName ?: "摄影者",
            handle = state.currentUser?.handle ?: "@raw",
            photoUri = it,
            imageFileName = imageName,
            rawFileName = rawName,
            rawVerified = rawUri != null,
            score = 0.0,
            confidence = 0.0,
            partition = Partition.REVIEW,
            moderationStatus = ModerationStatus.REVIEWING,
            moderationSummary = "等待上传后审核",
            paletteSeed = imageName.hashCode()
        )
    }

    Dialog(
        onDismissRequest = { actions.showUpload(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { actions.showUpload(false) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                    Text("发布作品", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            actions.submitUpload(
                                UploadDraft(
                                    title = title.trim(),
                                    description = description.trim(),
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
                        enabled = canSubmit,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("发布")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.82f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    imagePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewWork != null) {
                                WorkImage(previewWork, Modifier.fillMaxSize())
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.58f)
                                ) {
                                    Text(
                                        "更换成片",
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(34.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("选择展示成片", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "JPEG / PNG / WebP",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    item {
                        FileSelector(
                            icon = Icons.Outlined.InsertDriveFile,
                            title = "RAW 原始文件",
                            subtitle = rawName ?: "DNG、NEF、CR2、ARW 等；公开作品必选",
                            selected = rawUri != null,
                            onClick = {
                                rawPicker.launch(arrayOf("application/octet-stream", "image/x-adobe-dng", "*/*"))
                            }
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("作品标题") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("拍摄、后期边界与作品说明") },
                            minLines = 4,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(
                                Modifier.fillMaxWidth().padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("授权设置", fontWeight = FontWeight.SemiBold)
                                ToggleSettingRow("开放成片授权", "允许用户按设定价格下载成片", allowPreview) {
                                    allowPreview = it
                                }
                                if (allowPreview) {
                                    OutlinedTextField(
                                        value = previewPrice,
                                        onValueChange = { previewPrice = it },
                                        label = { Text("成片价格，0 表示免费") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                                ToggleSettingRow("开放 RAW 学习授权", "RAW 只按明确条款授权", allowRaw) {
                                    allowRaw = it
                                }
                                if (allowRaw) {
                                    OutlinedTextField(
                                        value = rawPrice,
                                        onValueChange = { rawPrice = it },
                                        label = { Text("RAW 价格，0 表示免费") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "发布后将先验证成片可解码、RAW 文件头和内容安全，再进入对应分区。审核失败会给出具体原因，不会伪造发布成功。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSelector(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
internal fun ProfileDialog(
    state: AppUiState,
    actions: RawJudgeActions,
    onOpenWork: (Work, List<Work>) -> Unit
) {
    val user = state.currentUser
    var tab by rememberSaveable { mutableStateOf(ProfileTab.WORKS) }
    var name by remember(user?.id) { mutableStateOf(user?.displayName.orEmpty()) }
    var handle by remember(user?.id) { mutableStateOf(user?.handle.orEmpty()) }
    var external by remember(user?.id) { mutableStateOf(user?.externalUrl.orEmpty()) }
    val ownedWorks = remember(state.works, user?.id) { state.works.filter { it.isOwner || it.ownerId == user?.id } }
    val favoriteWorks = remember(state.works) { state.works.filter { it.isFavorite } }

    Dialog(
        onDismissRequest = { actions.showProfile(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { actions.showProfile(false) }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                    Text("我的", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { tab = ProfileTab.SETTINGS }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(78.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                user?.displayName?.firstOrNull()?.uppercase() ?: "R",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(user?.displayName ?: "离线摄影者", style = MaterialTheme.typography.titleLarge)
                    Text(
                        user?.handle ?: "未连接账号",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(15.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStat(ownedWorks.size.toString(), "作品")
                        ProfileStat(favoriteWorks.size.toString(), "收藏")
                        ProfileStat("${((user?.reviewerTrust ?: 0f) * 100).toInt()}", "评审信誉")
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileTabButton("作品", Icons.Outlined.GridView, tab == ProfileTab.WORKS, Modifier.weight(1f)) {
                        tab = ProfileTab.WORKS
                    }
                    ProfileTabButton("收藏", Icons.Outlined.FavoriteBorder, tab == ProfileTab.FAVORITES, Modifier.weight(1f)) {
                        tab = ProfileTab.FAVORITES
                    }
                    ProfileTabButton("设置", Icons.Outlined.Settings, tab == ProfileTab.SETTINGS, Modifier.weight(1f)) {
                        tab = ProfileTab.SETTINGS
                    }
                }

                when (tab) {
                    ProfileTab.WORKS -> ProfileWorkGrid(ownedWorks) { onOpenWork(it, ownedWorks) }
                    ProfileTab.FAVORITES -> ProfileWorkGrid(favoriteWorks) { onOpenWork(it, favoriteWorks) }
                    ProfileTab.SETTINGS -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("显示名称") },
                                shape = RoundedCornerShape(17.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = handle,
                                onValueChange = { handle = it },
                                label = { Text("用户名") },
                                shape = RoundedCornerShape(17.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = external,
                                onValueChange = { external = it },
                                label = { Text("其他平台 HTTPS 入口") },
                                shape = RoundedCornerShape(17.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                    ToggleSettingRow("暗色模式", "跟随摄影作品的低干扰观看环境", state.settings.darkMode) {
                                        actions.toggleDarkMode()
                                    }
                                    ToggleSettingRow("无文字控件", "尽可能只保留图标和必要数字", state.settings.noTextControls) {
                                        actions.toggleNoText()
                                    }
                                    ToggleSettingRow("98% 沉浸模式", "打开作品时默认隐藏界面", state.settings.immersive98) {
                                        actions.toggleImmersive()
                                    }
                                    ToggleSettingRow("说明提示", "显示审核与授权说明", state.settings.hintsVisible) {
                                        actions.toggleHints()
                                    }
                                }
                            }
                        }
                        item {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                    Text("当前服务", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    Text(
                                        state.serverUrl.ifBlank { "未配置" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = { actions.updateProfile(name.trim(), handle.trim(), external.trim().ifBlank { null }) },
                                enabled = state.isOnline && name.isNotBlank() && handle.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(17.dp)
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("保存资料")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

@Composable
private fun ProfileTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileWorkGrid(works: List<Work>, onOpen: (Work) -> Unit) {
    if (works.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无作品", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(works, key = { it.id }) { work ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onOpen(work) }
            ) {
                WorkImage(work, Modifier.fillMaxSize())
                Text(
                    "%.1f".format(work.score),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ReviewDialog(work: Work, onDismiss: () -> Unit, submit: (String, Int) -> Unit) {
    var body by rememberSaveable(work.id) { mutableStateOf("") }
    var score by remember(work.id) { mutableFloatStateOf(work.score.toFloat().coerceIn(1f, 100f)) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("写评语", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "极端分需要具体证据；作者自评不会计权",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消")
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("作品评分", fontWeight = FontWeight.Medium)
                        Text(
                            score.toInt().toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Slider(score, { score = it }, valueRange = 1f..100f)
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("构图、色彩、技术、证据和改进建议") },
                    minLines = 5,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { submit(body.trim(), score.toInt()) },
                    enabled = body.trim().length >= 8,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text("提交评语")
                }
            }
        }
    }
}

@Composable
internal fun ReasonDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    submit: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    minLines = 4,
                    shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { submit(reason.trim()) }, enabled = reason.trim().length >= 3) {
                Text("提交")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
