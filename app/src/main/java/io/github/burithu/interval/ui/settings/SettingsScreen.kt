package io.github.burithu.interval.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.AppSettings
import io.github.burithu.interval.data.ThemeMode
import io.github.burithu.interval.onboarding.SetupChecks
import io.github.burithu.interval.ui.ChoiceDialog
import io.github.burithu.interval.ui.FieldLabel
import io.github.burithu.interval.ui.Glyph
import io.github.burithu.interval.ui.Segmented
import io.github.burithu.interval.ui.SettingRow
import io.github.burithu.interval.ui.TopBar
import io.github.burithu.interval.ui.formatInterval
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing
import kotlinx.coroutines.launch

private enum class Picker { MISSED, SNOOZE }

/** Screen 12. Kept short on purpose. */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onSetup: () -> Unit, onLog: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val missing = remember(refresh, settings.confirmedSteps) { SetupChecks.missingRequired(context, settings.confirmedSteps) }
    var picker by remember { mutableStateOf<Picker?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar("SETTINGS", Glyph.BACK, "Back", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FieldLabel("Theme")
                Segmented(
                    options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    selected = settings.themeMode,
                    onSelect = { mode -> scope.launch { container.settings.setThemeMode(mode) } },
                )
                Text(
                    if (settings.themeMode == ThemeMode.SYSTEM) "Follows the phone's dark mode setting." else "Ignores the phone's dark mode setting.",
                    style = t.meta, color = c.muted,
                )
            }
            Column {
                SettingRow(
                    label = "Reliability",
                    sub = "Permissions and battery settings",
                    value = if (missing.isEmpty()) "✓ All set" else "${missing.size} to fix",
                    onClick = onSetup,
                )
                HorizontalDivider(color = c.line)
                SettingRow(
                    label = "Missed after",
                    sub = "For unanswered notification-style rings",
                    value = formatInterval(settings.missedTimeoutMinutes),
                    onClick = { picker = Picker.MISSED },
                )
                HorizontalDivider(color = c.line)
                SettingRow(
                    label = "Default snooze",
                    sub = "For new reminders",
                    value = formatInterval(settings.defaultSnoozeMinutes),
                    onClick = { picker = Picker.SNOOZE },
                )
                HorizontalDivider(color = c.line)
                SettingRow(label = "Event log", sub = "Last 200 rings and changes", value = "View", onClick = onLog)
                HorizontalDivider(color = c.line)
                SettingRow(label = "About", sub = null, value = "Interval ${versionName(context)}", onClick = null)
            }
        }
    }

    when (picker) {
        Picker.MISSED -> ChoiceDialog(
            "Missed after", listOf(2, 5, 10, 15).map { it to formatInterval(it) }, settings.missedTimeoutMinutes,
            onDismiss = { picker = null },
        ) { m -> scope.launch { container.settings.setMissedTimeout(m) }; picker = null }
        Picker.SNOOZE -> ChoiceDialog(
            "Default snooze", listOf(5, 10, 15, 30).map { it to formatInterval(it) }, settings.defaultSnoozeMinutes,
            onDismiss = { picker = null },
        ) { m -> scope.launch { container.settings.setDefaultSnooze(m) }; picker = null }
        null -> Unit
    }
}

private fun versionName(context: Context): String = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName ?: "?"
} catch (_: Exception) {
    "?"
}
