package com.zoujiapeng.rawjudge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(primary = Color(0xFFF4F1EA), onPrimary = Color(0xFF080808), background = Color(0xFF050505), onBackground = Color(0xFFF4F1EA), surface = Color(0xFF111113), onSurface = Color(0xFFF4F1EA))
private val Light = lightColorScheme(primary = Color(0xFF171717), onPrimary = Color(0xFFFFFCF6), background = Color(0xFFF6F3EC), onBackground = Color(0xFF151515), surface = Color(0xFFFFFCF6), onSurface = Color(0xFF151515))

@Composable
fun RAWJudgeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
