package com.zoujiapeng.rawjudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoujiapeng.rawjudge.RawJudgeActions
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.BlindPair
import com.zoujiapeng.rawjudge.domain.Work
import com.zoujiapeng.rawjudge.ui.components.WorkImage

@Composable
fun BlindReviewScreen(state: AppUiState, actions: RawJudgeActions) {
    val pair = actions.blindPair()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("盲评", style = MaterialTheme.typography.titleLarge)
        Text("只比较作品质量。作者、粉丝、点赞和历史排名均隐藏；单次选择只产生小权重。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        if (pair == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("可盲评作品不足。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BlindCard(pair.left, state.settings.noTextControls, Modifier.weight(1f)) { actions.blindVote(pair, pair.left.id) }
                BlindCard(pair.right, state.settings.noTextControls, Modifier.weight(1f)) { actions.blindVote(pair, pair.right.id) }
            }
            Button(onClick = { actions.blindVote(pair, null) }, modifier = Modifier.fillMaxWidth()) { Text("分歧太大，跳过") }
            AntiAbuseNote(pair)
        }
    }
}

@Composable private fun BlindCard(work: Work, noText: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    Surface(modifier = modifier.fillMaxSize().clickable(onClick = onSelect), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkImage(work, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)))
            Text(if (noText) "?" else "选择这张更强", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("RAW ${if (work.rawVerified) "ok" else "miss"} · C ${"%.2f".format(work.confidence)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun AntiAbuseNote(pair: BlindPair) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("反作弊", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("序列 ${pair.sequence} · 新用户权重较低；短时间批量选择、关系链互评和极端评分会继续降权并进入审计。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}
