package io.github.burithu.interval.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import io.github.burithu.interval.data.ThemeMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Spacing steps on the 4dp grid (SPEC §4). */
object Spacing {
    /** Only between a row's title and its meta line. */
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screen = 20.dp
    val xl = 24.dp
    val minTouch = 48.dp
}

/** Standard component heights (SPEC §4). */
object Sizes {
    val chip = 32.dp
    val controlSmall = 40.dp
    val control = 48.dp
    val rowSingle = 56.dp
    val rowDouble = 64.dp
    val alarmPrimary = 72.dp
    val icon = 22.dp
}

/** Stroke widths: outlines on controls, rules under heroes and inputs, the alarm progress bar. */
object Strokes {
    val control = 1.5.dp
    val rule = 2.dp
    val progress = 4.dp
}

/** Material's own text styles (dialogs, time picker), in Archivo. */
private val MaterialType = Typography().run {
    fun TextStyle.archivo() = copy(fontFamily = ArchivoRegular)
    Typography(
        displayLarge = displayLarge.archivo(), displayMedium = displayMedium.archivo(), displaySmall = displaySmall.archivo(),
        headlineLarge = headlineLarge.archivo(), headlineMedium = headlineMedium.archivo(), headlineSmall = headlineSmall.archivo(),
        titleLarge = titleLarge.archivo(), titleMedium = titleMedium.archivo(), titleSmall = titleSmall.archivo(),
        bodyLarge = bodyLarge.archivo(), bodyMedium = bodyMedium.archivo(), bodySmall = bodySmall.archivo(),
        labelLarge = labelLarge.archivo(), labelMedium = labelMedium.archivo(), labelSmall = labelSmall.archivo(),
    )
}

/** Square corners everywhere, as in the rest of Big Type. Pills and switches keep their own full rounding. */
private val Square = RoundedCornerShape(0.dp)
private val SquareShapes = Shapes(
    extraSmall = Square, small = Square, medium = Square, large = Square, extraLarge = Square,
)

/**
 * Caps the system font scale for display-size text (countdown, alarm clock, headlines), which
 * is already large and would otherwise wrap or clip at 200%. Body text still scales fully.
 */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(max)), content = content)
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
    // Material components (switches, sheets, dialogs, the time picker) take their colors from
    // the same tokens. Every container slot is set so none falls back to Material's purple tints.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accent, onPrimaryContainer = c.onAccent,
        secondary = c.accent, onSecondary = c.onAccent, secondaryContainer = c.track, onSecondaryContainer = c.fg,
        tertiary = c.accent, onTertiary = c.onAccent, tertiaryContainer = c.track, onTertiaryContainer = c.fg,
        background = c.bg, onBackground = c.fg, surface = c.bg, onSurface = c.fg,
        surfaceVariant = c.track, onSurfaceVariant = c.muted, surfaceTint = c.bg,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.bg, surfaceContainer = c.line,
        surfaceContainerHigh = c.line, surfaceContainerHighest = c.track,
        outline = c.fg, outlineVariant = c.line, error = c.danger,
    )
    CompositionLocalProvider(
        LocalIntervalColors provides c,
        LocalIntervalType provides DefaultType,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialType, shapes = SquareShapes, content = content)
    }
}
