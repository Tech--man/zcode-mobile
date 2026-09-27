package dev.xray.zcode.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ZCode 设计 token —— 提取自 ZCode Web 端（Tailwind neutral + sky）。
 * 浅色：neutral-50/100/800/200 + sky-500；深色：neutral-900/950/200/800 + sky-400。
 */
data class ZcPalette(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val border: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val danger: Color,
    val scrim: Color,
    val topBar: Color,
)

fun lightPalette() = ZcPalette(
    bg = Color(0xFFFAFAFA),
    surface = Color(0xFFF5F5F5),
    surfaceAlt = Color(0xFFFFFFFF),
    text = Color(0xFF262626),
    textMuted = Color(0xFF525252),
    textFaint = Color(0xFFA3A3A3),
    border = Color(0xFFE5E5E5),
    brand = Color(0xFF0EA5E9),
    onBrand = Color(0xFFFFFFFF),
    brandSoft = Color(0x1A0EA5E9),
    danger = Color(0xFFDC2626),
    scrim = Color(0x66000000),
    topBar = Color(0xEBF5F5F5),
)

fun darkPalette() = ZcPalette(
    bg = Color(0xFF171717),
    surface = Color(0xFF0A0A0A),
    surfaceAlt = Color(0xFF1F1F1F),
    text = Color(0xFFE5E5E5),
    textMuted = Color(0xFFA3A3A3),
    textFaint = Color(0xFF6B6B6B),
    border = Color(0xFF262626),
    brand = Color(0xFF38BDF8),
    onBrand = Color(0xFF06202E),
    brandSoft = Color(0x1F38BDF8),
    danger = Color(0xFFF87171),
    scrim = Color(0x99000000),
    topBar = Color(0xE60A0A0A),
)

val LocalZcPalette = staticCompositionLocalOf { lightPalette() }

@Composable
fun ZcTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) darkPalette() else lightPalette()
    CompositionLocalProvider(LocalZcPalette provides palette, content = content)
}
