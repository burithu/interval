package io.github.burithu.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing

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
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), style = IntervalTheme.type.sheetTitle, color = c.bg)
    }
}

@Composable
fun FieldLabel(text: String) =
    Text(text.uppercase(), style = IntervalTheme.type.fieldLabel, color = IntervalTheme.colors.muted)

/** Two-choice segmented control; selected option is filled with fg. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = IntervalTheme.colors
    Row(Modifier.fillMaxWidth().border(1.5.dp, c.fg)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Spacing.minTouch)
                    .background(if (on) c.fg else c.bg)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = IntervalTheme.type.reminderName, color = if (on) c.bg else c.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Pill options; selected is filled with accent. */
@Composable
fun <T> PillRow(options: List<Pair<T, String>>, isSelected: (T) -> Boolean, onSelect: (T) -> Unit) {
    val c = IntervalTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.forEach { (value, label) ->
            val on = isSelected(value)
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .background(if (on) c.accent else c.bg, RoundedCornerShape(50))
                    .border(1.5.dp, if (on) c.accent else c.fg, RoundedCornerShape(50))
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = IntervalTheme.type.body, color = if (on) c.onAccent else c.fg)
            }
        }
    }
}

/** Label, optional sub-text, cobalt value. Tapping opens a picker (SettingRow in SPEC §4). */
@Composable
fun SettingRow(label: String, sub: String?, value: String, onClick: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = t.body.copy(fontSize = 14.sp), color = c.fg)
            if (sub != null) Text(sub, style = t.meta, color = c.muted)
        }
        Text(value, style = t.reminderName, color = c.accent)
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
