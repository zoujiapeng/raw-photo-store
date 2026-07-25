package com.zoujiapeng.rawjudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zoujiapeng.rawjudge.domain.Partition

@Composable
fun RawJudgeApp(state: AppUiState, actions: RawJudgeActions) {
    val selected = state.selectedWork
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RAWJudge", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions::toggleDarkMode) { Text(if (state.settings.darkMode) "亮" else "暗") }
                    Button(onClick = actions::toggleNoText) { Text("极简") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Partition.entries.forEach { p ->
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (p == state.activePartition) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.surface).clickable { actions.selectPartition(p) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(if (state.settings.noTextControls) p.code else p.label, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (selected != null) {
                Box(Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.1f".format(selected.score), fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(selected.title, fontSize = 18.sp)
                        Text("${selected.authorName} · RAW ${if (selected.rawVerified) "OK" else "缺失"}", fontSize = 12.sp)
                        Text(selected.moderationSummary, modifier = Modifier.padding(18.dp), fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.favorite(selected.id) }) { Text(if (selected.isFavorite) "已收藏" else "收藏") }
                    Button(onClick = { actions.appeal(selected.id) }) { Text("申诉") }
                    Button(onClick = { actions.report(selected.id) }) { Text("举报") }
                }
            }
            Text("作品", fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(state.filteredWorks, key = { it.id }) { work ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable { actions.selectWork(work.id) }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(work.title); Text("${work.partition.label} · ${work.moderationStatus.label}", fontSize = 11.sp) }
                        Text("%.1f".format(work.score), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
            state.toastMessage?.let { Text(it, fontSize = 12.sp); Spacer(Modifier.height(2.dp)) }
        }
    }
}
