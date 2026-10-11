package io.github.burithu.interval.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Big Type color tokens. Values from docs/SPEC.md §4. */
@Immutable
data class IntervalColors(
    val bg: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color,
    val danger: Color,
)

val LightColors = IntervalColors(
    bg = Color(0xFFFBFBFA),
    fg = Color(0xFF0C0C0D),
    muted = Color(0xFF707076),
    line = Color(0xFFE2E2E5),
    accent = Color(0xFF2F4BFF),
    onAccent = Color(0xFFFFFFFF),
    track = Color(0xFFDCDCE0),
    danger = Color(0xFFD22D2D),
)

val DarkColors = IntervalColors(
    bg = Color(0xFF0B0B0C),
    fg = Color(0xFFF3F3F1),
    muted = Color(0xFF8E8E95),
    line = Color(0xFF232327),
    accent = Color(0xFF7489FF),
    onAccent = Color(0xFF0B0B0C),
    track = Color(0xFF2D2D33),
    danger = Color(0xFFFF6B6B),
)

val LocalIntervalColors = staticCompositionLocalOf { LightColors }
