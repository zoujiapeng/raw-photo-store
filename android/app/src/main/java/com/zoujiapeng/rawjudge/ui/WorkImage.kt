package com.zoujiapeng.rawjudge.ui

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zoujiapeng.rawjudge.domain.Work
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WorkImage(work: Work, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(work.imageUrl, work.photoUri) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(work.imageUrl, work.photoUri) { mutableStateOf(false) }

    LaunchedEffect(work.imageUrl, work.photoUri) {
        failed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    !work.photoUri.isNullOrBlank() -> context.contentResolver
                        .openInputStream(Uri.parse(work.photoUri))
                        ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    !work.imageUrl.isNullOrBlank() -> {
                        val connection = URL(work.imageUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 12_000
                        connection.readTimeout = 20_000
                        connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    }
                    else -> null
                }
            }.getOrNull()
        }
        failed = bitmap == null
    }

    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = work.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: PlaceholderArt(work.paletteSeed, Modifier.fillMaxSize())
        if (failed && work.imageUrl != null) {
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
        listOf(Color(0xFF071A2D), Color(0xFF1B4965), Color(0xFF8ECAE6)),
        listOf(Color(0xFF241C1A), Color(0xFF6B4F42), Color(0xFFDDBEA9)),
        listOf(Color(0xFF101D28), Color(0xFF365B6D), Color(0xFFB8D8D8)),
        listOf(Color(0xFF251B22), Color(0xFF744253), Color(0xFFE7C6B5))
    )
    val palette = palettes[kotlin.math.abs(seed) % palettes.size]
    Canvas(modifier) {
        drawRect(Brush.linearGradient(palette, Offset.Zero, Offset(size.width, size.height)))
        repeat(7) { index ->
            val x = ((seed * 37 + index * 83) % 100).let { if (it < 0) -it else it } / 100f
            val y = ((seed * 53 + index * 47) % 100).let { if (it < 0) -it else it } / 100f
            drawCircle(
                color = Color.White.copy(alpha = 0.08f + index % 3 * 0.04f),
                radius = size.minDimension * (0.04f + index % 4 * 0.018f),
                center = Offset(size.width * x, size.height * y)
            )
        }
    }
}
