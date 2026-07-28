package com.zoujiapeng.rawjudge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("盲评", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "只比较作品，不展示作者与历史数据",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = actions::closeBlind, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭盲评")
                    }
                }
            }

            if (pair == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Shuffle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("当前没有足够的真实公开作品可比较")
                    }
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val landscape = maxWidth > maxHeight
                    if (landscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BlindCard(pair.left, "A", Modifier.weight(1f)) {
                                actions.blindVote(pair, pair.left.id)
                            }
                            BlindCard(pair.right, "B", Modifier.weight(1f)) {
                                actions.blindVote(pair, pair.right.id)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BlindCard(pair.left, "A", Modifier.weight(1f)) {
                                actions.blindVote(pair, pair.left.id)
                            }
                            BlindCard(pair.right, "B", Modifier.weight(1f)) {
                                actions.blindVote(pair, pair.right.id)
                            }
                        }
                    }
                }

                Button(
                    onClick = { actions.blindVote(pair, null) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (state.settings.noTextControls) "—" else "暂时无法判断 · 跳过")
                }
                AntiAbuseNote(pair)
            }
        }
    }
}

@Composable
private fun BlindCard(
    work: Work,
    mark: String,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize().clickable(onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Box(Modifier.fillMaxSize()) {
            WorkImage(
                work = work,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                backgroundColor = Color.Black
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
            ) {
                Text(
                    mark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "选择这张作品",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    if (work.rawVerified) "RAW 已验证" else "RAW 状态待核验",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AntiAbuseNote(pair: BlindPair) {
    Text(
        "序列 ${pair.sequence} · 作者、标题、收藏、分数均已隐藏；重复与高速投票由服务端降权。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        lineHeight = 13.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
