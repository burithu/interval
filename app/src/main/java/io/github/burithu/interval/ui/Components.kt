package io.github.burithu.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Sizes
import io.github.burithu.interval.ui.theme.Spacing
import io.github.burithu.interval.ui.theme.Strokes

/** Full-width bar pinned to the bottom (AddBar in SPEC §4). */
@Composable
fun BottomBar(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = IntervalTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(c.fg)
            .clickable(role = Role.Button, onClick = onClick)
            .navigationBarsPadding()
            .heightIn(min = Sizes.rowSingle),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), style = IntervalTheme.type.sheetTitle, color = c.bg)
    }
}

/** Sub-screen header: back or close on the left, title, optional action on the right. */
@Composable
fun TopBar(
    title: String,
    nav: Glyph,
    navLabel: String,
    onNav: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.rowSingle).padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(nav, navLabel, onNav, color = if (nav == Glyph.CLOSE) IntervalTheme.colors.muted else IntervalTheme.colors.fg)
        Text(title, style = IntervalTheme.type.sheetTitle, color = IntervalTheme.colors.fg, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun FieldLabel(text: String) =
    Text(text.uppercase(), style = IntervalTheme.type.fieldLabel, color = IntervalTheme.colors.muted)

/** Segmented control; selected option is filled with fg. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = IntervalTheme.colors
    Row(Modifier.fillMaxWidth().border(Strokes.control, c.fg)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.control)
                    .background(if (on) c.fg else c.bg)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = IntervalTheme.type.reminderName, color = if (on) c.bg else c.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Pill options; selected is filled with accent. 40dp to look at, 48dp to touch. */
@Composable
fun <T> PillRow(options: List<Pair<T, String>>, isSelected: (T) -> Boolean, onSelect: (T) -> Unit) {
    val c = IntervalTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.forEach { (value, label) ->
            val on = isSelected(value)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = Sizes.controlSmall)
                    .background(if (on) c.accent else c.bg, RoundedCornerShape(50))
                    .border(Strokes.control, if (on) c.accent else c.fg, RoundedCornerShape(50))
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = Spacing.l),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = IntervalTheme.type.body, color = if (on) c.onAccent else c.fg)
            }
        }
    }
}

/** Outlined square button for small inline actions ("+ ADD", setup steps). 40dp to look at, 48dp to touch. */
@Composable
fun SmallButton(label: String, onClick: () -> Unit, color: Color = IntervalTheme.colors.fg) {
    Box(
        Modifier.minimumInteractiveComponentSize()
            .heightIn(min = Sizes.controlSmall)
            .border(Strokes.control, color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m),
        contentAlignment = Alignment.Center,
    ) { Text(label.uppercase(), style = IntervalTheme.type.fieldLabel, color = color) }
}

/**
 * The one list row: 56dp for a single line, 64dp for two. Taller content (a wrapped meta line)
 * grows it. Not clickable when [onClick] is null.
 */
@Composable
fun ListRow(
    twoLine: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = if (twoLine) Sizes.rowDouble else Sizes.rowSingle)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        content = content,
    )
}

/** Label, optional sub-text, cobalt value. Tapping opens a picker (SettingRow in SPEC §4). */
@Composable
fun SettingRow(label: String, sub: String?, value: String, onClick: (() -> Unit)?) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    ListRow(twoLine = sub != null, onClick = onClick) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(label, style = t.body, color = c.fg)
            if (sub != null) Text(sub, style = t.meta, color = c.muted)
        }
        Text(value, style = t.reminderName, color = if (onClick != null) c.accent else c.muted)
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<Pair<Int, String>>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            PillRow(options = options, isSelected = { it == selected }, onSelect = onPick)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
