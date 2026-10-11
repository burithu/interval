package io.github.burithu.interval.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import io.github.burithu.interval.ui.ListRow
import io.github.burithu.interval.ui.formatNext
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Sizes
import io.github.burithu.interval.ui.theme.Spacing
import io.github.burithu.interval.ui.theme.Strokes
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Header chip: outlined "Pause all" at rest, filled "Paused" while a pause is active, so the unusual state is the loud one. */
@Composable
internal fun PauseChip(paused: Boolean, onClick: () -> Unit) {
    val c = IntervalTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.heightIn(min = Spacing.minTouch).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(Sizes.chip)
                .then(if (paused) Modifier.background(c.fg, shape) else Modifier.border(Strokes.control, c.fg, shape))
                .padding(horizontal = Spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (paused) "PAUSED" else "PAUSE ALL", style = IntervalTheme.type.fieldLabel, color = if (paused) c.bg else c.fg)
        }
    }
}

/** Screen 06 hero. */
@Composable
internal fun PausedHero(until: Instant, now: Instant, zone: ZoneId, onResume: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Text("ALL REMINDERS", style = t.fieldLabel, color = c.muted)
        Text("Paused", style = t.countdown, color = c.muted)
        Text("Until ${formatNext(until, now, zone)}", style = t.title, color = c.fg)
        Box(
            Modifier.heightIn(min = Spacing.minTouch).clickable(role = Role.Button, onClick = onResume),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Resume now", style = t.reminderName, color = c.accent, textDecoration = TextDecoration.Underline)
        }
        HeroRule()
    }
}

/**
 * Screen 08. Each option shows when it ends. "Until tomorrow" pauses until midnight, after
 * which each reminder starts its day as normal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PauseSheet(
    now: Instant,
    zone: ZoneId,
    pausedUntil: Instant?,
    onPause: (Instant) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val tomorrow = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    val options = listOf(30L, 60L, 120L, 240L).map { m ->
        (if (m < 60) "$m min" else "${m / 60} h") to now.plus(m, ChronoUnit.MINUTES)
    } + ("Until tomorrow" to tomorrow)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = c.bg,
        contentColor = c.fg,
        dragHandle = null,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(Strokes.rule).background(c.fg))
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l)) {
                Text(
                    if (pausedUntil != null) "PAUSED UNTIL ${formatNext(pausedUntil, now, zone).uppercase()}" else "PAUSE ALL REMINDERS",
                    style = t.sheetTitle, color = c.fg,
                )
                Spacer(Modifier.height(Spacing.s))
                if (pausedUntil != null) {
                    SheetRow("Resume now", "Restart timers count from now", accent = true, onClick = onResume)
                    HorizontalDivider(color = c.line)
                }
                options.forEachIndexed { i, (label, end) ->
                    val sub = if (end == tomorrow) "Each reminder starts its day as normal" else "Until ${formatNext(end, now, zone)}"
                    SheetRow(label, sub, accent = false) { onPause(end) }
                    if (i < options.lastIndex) HorizontalDivider(color = c.line)
                }
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, sub: String, accent: Boolean, onClick: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    ListRow(twoLine = false, onClick = onClick) {
        Text(label, style = t.reminderName, color = if (accent) c.accent else c.fg, modifier = Modifier.weight(1f))
        Text(sub, style = t.meta, color = c.muted)
    }
}
