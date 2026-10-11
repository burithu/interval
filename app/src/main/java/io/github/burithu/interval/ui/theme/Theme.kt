package io.github.burithu.interval.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/** Spacing steps on the 4dp grid (SPEC §4). */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screen = 20.dp
    val xl = 24.dp
    val minTouch = 48.dp
}

object IntervalTheme {
    val colors: IntervalColors
        @Composable @ReadOnlyComposable get() = LocalIntervalColors.current
    val type: IntervalType
        @Composable @ReadOnlyComposable get() = LocalIntervalType.current
}

/** Theme follows the phone (D16). Fixed brand colors, no dynamic color (D26). */
@Composable
fun IntervalTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (dark) DarkColors else LightColors
    // Material components (switches, sheets) take their colors from the same tokens.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.fg,
            surface = c.bg, onSurface = c.fg, onSurfaceVariant = c.muted, outline = c.fg,
            outlineVariant = c.line, surfaceContainerHighest = c.track, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.fg,
            surface = c.bg, onSurface = c.fg, onSurfaceVariant = c.muted, outline = c.fg,
            outlineVariant = c.line, surfaceContainerHighest = c.track, error = c.danger,
        )
    }
    CompositionLocalProvider(
        LocalIntervalColors provides c,
        LocalIntervalType provides DefaultType,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
