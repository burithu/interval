package io.github.burithu.interval.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import io.github.burithu.interval.data.ThemeMode
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

/**
 * Applies the user's theme choice (D31): SYSTEM follows the phone (D16). Also flips the
 * status and navigation bar icons, which otherwise follow the phone setting.
 */
@Composable
fun IntervalTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(dark, activity) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    IntervalTheme(dark = dark, content = content)
}

/** Fixed brand colors, no dynamic color (D26). */
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
