package com.zoujiapeng.rawjudge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Dark = darkColorScheme(
    primary = Color(0xFFF4F1EA),
    onPrimary = Color(0xFF090909),
    primaryContainer = Color(0xFF252527),
    onPrimaryContainer = Color(0xFFF7F5EF),
    secondary = Color(0xFFA6AAA7),
    onSecondary = Color(0xFF111212),
    secondaryContainer = Color(0xFF202224),
    onSecondaryContainer = Color(0xFFE5E7E5),
    tertiary = Color(0xFFFF5A64),
    onTertiary = Color.White,
    background = Color(0xFF050505),
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF101011),
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF1A1A1C),
    onSurfaceVariant = Color(0xFFAAA9A6),
    outline = Color(0xFF37373A),
    outlineVariant = Color(0xFF242426),
    error = Color(0xFFFF6B72),
    scrim = Color.Black
)

private val Light = lightColorScheme(
    primary = Color(0xFF141414),
    onPrimary = Color(0xFFFFFCF7),
    primaryContainer = Color(0xFFE9E7E1),
    onPrimaryContainer = Color(0xFF141414),
    secondary = Color(0xFF666A67),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEDE9),
    onSecondaryContainer = Color(0xFF202220),
    tertiary = Color(0xFFE94B57),
    onTertiary = Color.White,
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFFFFEFB),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFECEBE7),
    onSurfaceVariant = Color(0xFF666662),
    outline = Color(0xFFCFCFCB),
    outlineVariant = Color(0xFFE2E1DC),
    error = Color(0xFFBA1A1A),
    scrim = Color.Black
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.8f).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.45f).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2f).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun RAWJudgeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
