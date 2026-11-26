package com.oyenter.lo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0C0A08)
val Surface1 = Color(0xFF14110E)
val Surface2 = Color(0xFF1C1814)
val Surface3 = Color(0xFF262018)
val Ink = Color(0xFFF3EBDD)
val Muted = Color(0xFFA1968A)
val Faint = Color(0xFF6B6158)
val Amber = Color(0xFFD9A05B)
val AmberDeep = Color(0xFF8E6535)
val Ember = Color(0xFFE2705A)
val Olive = Color(0xFF7E8B63)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF211505),
    primaryContainer = AmberDeep,
    onPrimaryContainer = Ink,
    secondary = Olive,
    onSecondary = Color(0xFF14180F),
    tertiary = Ember,
    background = Bg,
    onBackground = Ink,
    surface = Surface1,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    outline = Color(0xFF3A322A),
    outlineVariant = Color(0xFF2A241E),
    error = Ember,
)

private val typography = Typography().run {
    copy(
        displayLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 46.sp,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            letterSpacing = (-1).sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            letterSpacing = 1.4.sp,
        ),
    )
}

@Composable
fun LoTheme(scale: Float = 1f, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val safe = scale.coerceIn(0.8f, 1.35f)
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * safe, base.fontScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
