package com.reeltracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Roles beyond Material's: the chart and tiles are written against these, not raw hex. */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val cardRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val hairline: Color,
    val accent: Color,       // validated: >= 3:1 on card, inside the lightness band for its mode
    val accentSoft: Color,   // de-emphasis step of the same hue
    val good: Color,
)

private val Light = Palette(
    background = Color(0xFFF6F5F3),
    card = Color(0xFFFFFFFF),
    cardRaised = Color(0xFFF0EFEC),
    textPrimary = Color(0xFF0B0B0B),
    textSecondary = Color(0xFF52514E),
    textMuted = Color(0xFF8A8984),
    hairline = Color(0xFFE6E5E1),
    accent = Color(0xFFE5483C),
    accentSoft = Color(0xFFF3B3AD),
    good = Color(0xFF1F8A4C),
)

private val Dark = Palette(
    background = Color(0xFF0F0F10),
    card = Color(0xFF1A1A19),
    cardRaised = Color(0xFF242423),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFC3C2B7),
    textMuted = Color(0xFF8C8B84),
    hairline = Color(0xFF2E2E2C),
    accent = Color(0xFFF4533F),
    accentSoft = Color(0xFF6B2E28),
    good = Color(0xFF4CC27E),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun ReelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = p.accent, background = p.background, surface = p.card, onSurface = p.textPrimary)
    } else {
        lightColorScheme(primary = p.accent, background = p.background, surface = p.card, onSurface = p.textPrimary)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
