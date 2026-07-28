package com.zoujiapeng.rawjudge.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zoujiapeng.rawjudge.domain.Work
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object WorkImageMemoryCache {
    private val cache = LruCache<String, ImageBitmap>(24)

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }
}

@Composable
fun WorkImage(
    work: Work,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showErrorLabel: Boolean = true,
    backgroundColor: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val imageKey = work.photoUri ?: work.imageUrl ?: "placeholder:${work.paletteSeed}"
    var bitmap by remember(imageKey) { mutableStateOf(WorkImageMemoryCache.get(imageKey)) }
    var failed by remember(imageKey) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (bitmap == null) 0f else 1f,
        label = "work-image-alpha"
    )
    val resolvedBackground = if (backgroundColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        backgroundColor
    }

    LaunchedEffect(imageKey) {
        WorkImageMemoryCache.get(imageKey)?.let {
            bitmap = it
            failed = false
            return@LaunchedEffect
        }
        failed = false
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    !work.photoUri.isNullOrBlank() -> context.contentResolver
                        .openInputStream(Uri.parse(work.photoUri))
                        ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }

                    !work.imageUrl.isNullOrBlank() -> {
                        val connection = URL(work.imageUrl).openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 12_000
                            connection.readTimeout = 20_000
                            connection.instanceFollowRedirects = true
                            connection.connect()
                            if (connection.responseCode !in 200..299) return@runCatching null
                            connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        } finally {
                            connection.disconnect()
                        }
                    }

                    else -> null
                }
            }.getOrNull()
        }
        bitmap = loaded
        failed = loaded == null && (work.imageUrl != null || work.photoUri != null)
        loaded?.let { WorkImageMemoryCache.put(imageKey, it) }
    }

    Box(
        modifier = modifier.background(resolvedBackground),
        contentAlignment = Alignment.Center
    ) {
        PlaceholderArt(work.paletteSeed, Modifier.fillMaxSize())
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = work.title,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = imageAlpha),
                contentScale = contentScale
            )
        }
        if (showErrorLabel && failed) {
            Text(
                "预览暂不可用",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PlaceholderArt(seed: Int, modifier: Modifier) {
    val palettes = listOf(
        listOf(Color(0xFF07131F), Color(0xFF1B4965), Color(0xFF9FC6D4)),
        listOf(Color(0xFF211A19), Color(0xFF735347), Color(0xFFE1BFA6)),
        listOf(Color(0xFF0D1B22), Color(0xFF315865), Color(0xFFB8D8D8)),
        listOf(Color(0xFF241820), Color(0xFF744253), Color(0xFFE8C7B8)),
        listOf(Color(0xFF111716), Color(0xFF52645B), Color(0xFFD8D0A8))
    )
    val safeSeed = if (seed == Int.MIN_VALUE) 0 else kotlin.math.abs(seed)
    val palette = palettes[safeSeed % palettes.size]
    Canvas(modifier) {
        drawRect(Brush.linearGradient(palette, Offset.Zero, Offset(size.width, size.height)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.24f),
                radius = size.minDimension * 0.45f
            ),
            radius = size.minDimension * 0.45f,
            center = Offset(size.width * 0.72f, size.height * 0.24f)
        )
        repeat(6) { index ->
            val x = ((safeSeed * 37 + index * 83) % 100) / 100f
            val y = ((safeSeed * 53 + index * 47) % 100) / 100f
            drawCircle(
                color = Color.White.copy(alpha = 0.035f + index % 3 * 0.025f),
                radius = size.minDimension * (0.06f + index % 4 * 0.025f),
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}
