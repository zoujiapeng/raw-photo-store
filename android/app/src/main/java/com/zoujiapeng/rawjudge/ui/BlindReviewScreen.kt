package com.zoujiapeng.rawjudge.ui

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
import androidx.compose.material3.OutlinedButton
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

@Composable
fun BlindReviewScreen(state: AppUiState, actions: RawJudgeActions) {
    val pair = state.blindPair
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("盲评", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = actions::closeBlind) { Text("关闭") }
        }
        Text(
            "只比较作品本身。作者、标题、粉丝、收藏和历史分数均由服务端隐藏；同一作品对重复投票权重归零。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        if (pair == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前没有足够的真实公开作品可盲评。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BlindCard(pair.left, state.settings.noTextControls, Modifier.weight(1f)) {
                    actions.blindVote(pair, pair.left.id)
                }
                BlindCard(pair.right, state.settings.noTextControls, Modifier.weight(1f)) {
                    actions.blindVote(pair, pair.right.id)
                }
            }
            Button(
                onClick = { actions.blindVote(pair, null) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("无法判断，跳过")
            }
            AntiAbuseNote(pair)
        }
    }
}

@Composable
private fun BlindCard(
    work: Work,
    noText: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize().clickable(onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkImage(
                work,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
            )
            Text(
                if (noText) "?" else "选择这张更强",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(
                "RAW ${if (work.rawVerified) "ok" else "miss"} · C ${"%.2f".format(work.confidence)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AntiAbuseNote(pair: BlindPair) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("反作弊", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                "序列 ${pair.sequence} · 新用户权重较低；重复比较、快速批量选择和自评由服务端拦截或降权。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }
}
