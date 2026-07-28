package com.zoujiapeng.rawjudge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoujiapeng.rawjudge.RawJudgeActions
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.domain.Partition
import com.zoujiapeng.rawjudge.domain.Work
import kotlin.math.abs

private enum class FeedScope { DISCOVER, FAVORITES }
private enum class FeedFilter { RECOMMENDED, TOP, VERIFIED }

@Composable
internal fun PhotographyFeed(
    state: AppUiState,
    actions: RawJudgeActions,
    onOpenWork: (Work, List<Work>) -> Unit
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var feedScope by rememberSaveable { mutableStateOf(FeedScope.DISCOVER) }
    var feedFilter by rememberSaveable { mutableStateOf(FeedFilter.RECOMMENDED) }

    val visibleWorks = remember(
        state.works,
        state.activePartition,
        feedScope,
        feedFilter,
        searchQuery
    ) {
        val base = when (feedScope) {
            FeedScope.DISCOVER -> state.filteredWorks
            FeedScope.FAVORITES -> state.works.filter { it.isFavorite }
        }
        val searched = searchQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
            base.filter { work ->
                work.title.contains(query, ignoreCase = true) ||
                    work.authorName.contains(query, ignoreCase = true) ||
                    work.handle.contains(query, ignoreCase = true) ||
                    work.description.contains(query, ignoreCase = true)
            }
        } ?: base
        when (feedFilter) {
            FeedFilter.RECOMMENDED -> searched
            FeedFilter.TOP -> searched.sortedWith(
                compareByDescending<Work> { it.score }.thenByDescending { it.confidence }
            )
            FeedFilter.VERIFIED -> searched.filter { it.rawVerified }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = if (searchVisible) 174.dp else 112.dp,
                bottom = 104.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 14.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                FeedIntro(
                    state = state,
                    scope = feedScope,
                    filter = feedFilter,
                    count = visibleWorks.size,
                    onFilter = { feedFilter = it }
                )
            }
            if (visibleWorks.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyFeed(feedScope, searchQuery)
                }
            } else {
                items(visibleWorks, key = { it.id }) { work ->
                    MasonryWorkCard(
                        work = work,
                        state = state,
                        onOpen = { onOpenWork(work, visibleWorks) },
                        onFavorite = { actions.favorite(work.id) }
                    )
                }
            }
        }

        FeedTopChrome(
            state = state,
            searchVisible = searchVisible,
            searchQuery = searchQuery,
            feedScope = feedScope,
            onSearchVisible = { searchVisible = it },
            onSearchQuery = { searchQuery = it },
            onPartition = {
                feedScope = FeedScope.DISCOVER
                actions.selectPartition(it)
            },
            onProfile = { actions.showProfile(true) }
        )

        BottomDock(
            state = state,
            scope = feedScope,
            modifier = Modifier.align(Alignment.BottomCenter),
            onDiscover = { feedScope = FeedScope.DISCOVER },
            onBlind = actions::openBlind,
            onPublish = { actions.showUpload(true) },
            onFavorites = { feedScope = FeedScope.FAVORITES },
            onProfile = { actions.showProfile(true) }
        )
    }
}

@Composable
private fun FeedTopChrome(
    state: AppUiState,
    searchVisible: Boolean,
    searchQuery: String,
    feedScope: FeedScope,
    onSearchVisible: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
    onPartition: (Partition) -> Unit,
    onProfile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.965f),
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .animateContentSize()
                .padding(top = 6.dp, bottom = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    ) {
                        Text(
                            "R",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.background,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Column(Modifier.padding(start = 9.dp)) {
                        Text(
                            if (feedScope == FeedScope.FAVORITES) "收藏" else "RAWJudge",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.2f).sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.isOnline) Color(0xFF4BB978)
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Text(
                                if (state.isOnline) " 在线" else " 离线样例",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSearchVisible(!searchVisible) }) {
                        Icon(
                            if (searchVisible) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searchVisible) "关闭搜索" else "搜索"
                        )
                    }
                    Surface(
                        modifier = Modifier.size(35.dp).clickable(onClick = onProfile),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                state.currentUser?.displayName?.firstOrNull()?.uppercase() ?: "我",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            PartitionTabs(
                active = state.activePartition,
                onSelect = onPartition
            )

            AnimatedVisibility(
                visible = searchVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                        .height(48.dp),
                    placeholder = { Text("搜索作品、摄影师或说明", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "清空", modifier = Modifier.size(17.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
private fun PartitionTabs(active: Partition, onSelect: (Partition) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Partition.entries.forEach { partition ->
            val selected = partition == active
            Column(
                modifier = Modifier.clickable { onSelect(partition) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    partition.label,
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(if (selected) 20.dp else 0.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            }
        }
    }
}

@Composable
private fun FeedIntro(
    state: AppUiState,
    scope: FeedScope,
    filter: FeedFilter,
    count: Int,
    onFilter: (FeedFilter) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (scope == FeedScope.FAVORITES) "已收藏的作品" else state.activePartition.label,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "$count 件 · 质量与 RAW 证据优先",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                if (state.settings.immersive98) "98% ON" else "点击作品沉浸查看",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedFilterPill(
                label = if (state.settings.noTextControls) "◌" else "推荐",
                selected = filter == FeedFilter.RECOMMENDED,
                onClick = { onFilter(FeedFilter.RECOMMENDED) }
            )
            FeedFilterPill(
                label = if (state.settings.noTextControls) "↑" else "高分",
                selected = filter == FeedFilter.TOP,
                onClick = { onFilter(FeedFilter.TOP) }
            )
            FeedFilterPill(
                label = if (state.settings.noTextControls) "R" else "RAW 已验证",
                selected = filter == FeedFilter.VERIFIED,
                onClick = { onFilter(FeedFilter.VERIFIED) }
            )
        }
    }
}

@Composable
private fun FeedFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (selected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun MasonryWorkCard(
    work: Work,
    state: AppUiState,
    onOpen: () -> Unit,
    onFavorite: () -> Unit
) {
    val imageRatio = workAspectRatio(work)
    val canFavorite = state.isOnline && !state.isLoading && !work.isOfficialSample && work.id > 0
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(imageRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen)
        ) {
            WorkImage(work, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f))
                        )
                    )
            )
            if (work.rawVerified) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Text(
                        "RAW",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.52f)
            ) {
                Text(
                    "%.1f".format(work.score),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (work.isOfficialSample) {
                Text(
                    "官方样例",
                    modifier = Modifier.align(Alignment.BottomStart).padding(9.dp),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 8.sp
                )
            }
        }

        Text(
            work.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onOpen)
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = avatarColor(work.paletteSeed),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        work.authorName.firstOrNull()?.uppercase() ?: "R",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                work.authorName,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp
            )
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = canFavorite, onClick = onFavorite)
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (work.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (work.isFavorite) "取消收藏" else "收藏",
                    modifier = Modifier.size(14.dp),
                    tint = if (work.isFavorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    compactCount(work.favorites),
                    modifier = Modifier.padding(start = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun BottomDock(
    state: AppUiState,
    scope: FeedScope,
    modifier: Modifier = Modifier,
    onDiscover: () -> Unit,
    onBlind: () -> Unit,
    onPublish: () -> Unit,
    onFavorites: () -> Unit,
    onProfile: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            shadowElevation = 18.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockButton(Icons.Outlined.Home, "发现", scope == FeedScope.DISCOVER, onDiscover)
                DockButton(
                    Icons.Outlined.CompareArrows,
                    "盲评",
                    false,
                    onBlind,
                    enabled = state.isOnline && !state.isLoading
                )
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(50.dp)
                        .clickable(
                            enabled = state.isOnline && !state.isLoading,
                            onClick = onPublish
                        ),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "发布作品",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                DockButton(Icons.Outlined.FavoriteBorder, "收藏", scope == FeedScope.FAVORITES, onFavorites)
                DockButton(Icons.Outlined.Person, "我的", false, onProfile)
            }
        }
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(45.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

@Composable
private fun EmptyFeed(scope: FeedScope, query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (scope == FeedScope.FAVORITES) Icons.Outlined.FavoriteBorder else Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            when {
                query.isNotBlank() -> "没有匹配的作品"
                scope == FeedScope.FAVORITES -> "还没有收藏作品"
                else -> "当前分区暂无作品"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun workAspectRatio(work: Work): Float {
    val ratios = floatArrayOf(0.72f, 0.82f, 0.92f, 1.08f, 1.28f)
    val safe = if (work.paletteSeed == Int.MIN_VALUE) 0 else abs(work.paletteSeed)
    return ratios[safe % ratios.size]
}

private fun compactCount(value: Int): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}

private fun avatarColor(seed: Int): Color {
    val colors = listOf(
        Color(0xFF456B76),
        Color(0xFF76594C),
        Color(0xFF6B5876),
        Color(0xFF4E6A55),
        Color(0xFF6F5F47)
    )
    val safe = if (seed == Int.MIN_VALUE) 0 else abs(seed)
    return colors[safe % colors.size]
}
