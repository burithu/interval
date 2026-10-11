package io.github.burithu.interval.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import io.github.burithu.interval.data.TimingMode
import io.github.burithu.interval.data.db.toDomain
import io.github.burithu.interval.ui.FieldLabel
import io.github.burithu.interval.ui.PillRow
import io.github.burithu.interval.ui.Segmented
import io.github.burithu.interval.ui.formatInterval
import io.github.burithu.interval.ui.formatMinuteOfDay
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant

class EditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: 0L
    val isNew = id == 0L

    var label by mutableStateOf("")
    var intervalMinutes by mutableIntStateOf(60)
    var windowStart by mutableIntStateOf(9 * 60)
    var windowEnd by mutableIntStateOf(18 * 60)
    var days by mutableStateOf(Days.WEEKDAYS)
    var timingMode by mutableStateOf(TimingMode.RESTART)
    var ringStyle by mutableStateOf(RingStyle.NOTIFICATION)
    var snoozeMinutes by mutableIntStateOf(10)
    var ringSeconds by mutableIntStateOf(60)
    var vibrate by mutableStateOf(true)
    private var original: Reminder? = null

    init {
        viewModelScope.launch {
            if (isNew) {
                snoozeMinutes = c.settings.current().defaultSnoozeMinutes
            } else {
                c.db.reminderDao().get(id)?.toDomain()?.let { r ->
                    original = r
                    label = r.label; intervalMinutes = r.intervalMinutes; windowStart = r.windowStart
                    windowEnd = r.windowEnd; days = r.days; timingMode = r.timingMode; ringStyle = r.ringStyle
                    snoozeMinutes = r.snoozeMinutes; ringSeconds = r.ringSeconds; vibrate = r.vibrate
                }
            }
        }
    }

    val canSave: Boolean get() = label.isNotBlank() && !days.isEmpty && intervalMinutes in 1..1440

    fun toggleDay(d: DayOfWeek) {
        val bit = Days.of(d).mask
        days = Days(days.mask xor bit)
    }

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        val base = original ?: Reminder(label = "", createdAt = Instant.now())
        val r = base.copy(
            label = label.trim().take(40), intervalMinutes = intervalMinutes, windowStart = windowStart,
            windowEnd = windowEnd, days = days, timingMode = timingMode, ringStyle = ringStyle,
            snoozeMinutes = snoozeMinutes, ringSeconds = ringSeconds, vibrate = vibrate, enabled = true,
        )
        viewModelScope.launch {
            c.engine.save(r)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            c.engine.delete(id)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var customText by remember { mutableStateOf("") }
    val presets = listOf(20, 45, 60, 120)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✕", style = t.screenTitle, color = c.muted, modifier = Modifier.clickable(role = Role.Button, onClick = onClose).padding(Spacing.m))
            Text(if (vm.isNew) "NEW REMINDER" else "EDIT REMINDER", style = t.sheetTitle, color = c.fg, modifier = Modifier.weight(1f))
            Text(
                "SAVE",
                style = t.sheetTitle,
                color = if (vm.canSave) c.accent else c.muted,
                modifier = Modifier.clickable(enabled = vm.canSave, role = Role.Button) { vm.save(onClose) }.padding(Spacing.m),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Section("Label") {
                OutlinedTextField(
                    value = vm.label, onValueChange = { vm.label = it.take(40) }, singleLine = true,
                    placeholder = { Text("Drink water") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            Section("Repeat every") {
                PillRow(
                    options = presets.map { it to formatInterval(it) },
                    isSelected = { it == vm.intervalMinutes },
                    onSelect = { vm.intervalMinutes = it; customText = "" },
                )
                OutlinedTextField(
                    value = customText,
                    onValueChange = { s ->
                        customText = s.filter(Char::isDigit).take(4)
                        customText.toIntOrNull()?.takeIf { it in 1..1440 }?.let { vm.intervalMinutes = it }
                    },
                    label = { Text("Custom (minutes, 1–1440)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Section("Active between") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    TimeBox(formatMinuteOfDay(vm.windowStart), Modifier.weight(1f)) { picking = "start" }
                    Text("→", style = t.screenTitle, color = c.fg)
                    val suffix = when {
                        vm.windowEnd == vm.windowStart -> " (24 h)"
                        vm.windowEnd < vm.windowStart -> " +1"
                        else -> ""
                    }
                    TimeBox(formatMinuteOfDay(vm.windowEnd) + suffix, Modifier.weight(1f)) { picking = "end" }
                }
                val hint = when {
                    vm.windowEnd == vm.windowStart -> "Same start and end: active all day."
                    vm.windowEnd < vm.windowStart -> "Ends the next day. Days below are the days it starts."
                    else -> null
                }
                hint?.let { Text(it, style = t.meta, color = c.accent) }
            }
            Section("Days") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in vm.days
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(if (on) c.accent else c.bg)
                                .border(1.5.dp, if (on) c.accent else c.fg)
                                .clickable(role = Role.Checkbox) { vm.toggleDay(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.name.take(1), style = t.reminderName, color = if (on) c.onAccent else c.fg)
                        }
                    }
                }
                if (vm.days.isEmpty) Text("Pick at least one day.", style = t.meta, color = c.danger)
            }
            Section("After you dismiss") {
                Segmented(
                    options = listOf(TimingMode.RESTART to "Restart timer", TimingMode.FIXED to "Keep schedule"),
                    selected = vm.timingMode,
                    onSelect = { vm.timingMode = it },
                )
                val every = formatInterval(vm.intervalMinutes)
                Text(
                    if (vm.timingMode == TimingMode.RESTART) "Next ring $every after you dismiss."
                    else "Rings every $every from ${formatMinuteOfDay(vm.windowStart)}, no matter when you dismiss.",
                    style = t.meta, color = c.muted,
                )
            }
            Section("Ring as") {
                Segmented(
                    options = listOf(RingStyle.NOTIFICATION to "Notification", RingStyle.ALARM to "Alarm"),
                    selected = vm.ringStyle,
                    onSelect = { vm.ringStyle = it },
                )
                Text(
                    if (vm.ringStyle == RingStyle.ALARM) "Full-screen alarm. Rings even when the phone is on silent."
                    else "A chime and vibration. Follows the phone's silent and vibrate modes.",
                    style = t.meta, color = c.muted,
                )
                if (vm.ringStyle == RingStyle.ALARM) {
                    FieldLabel("Ring for")
                    PillRow(
                        options = listOf(30, 60, 120, 300).map { it to if (it < 60) "$it s" else "${it / 60} min" },
                        isSelected = { it == vm.ringSeconds },
                        onSelect = { vm.ringSeconds = it },
                    )
                    Text("Then it stops and counts as missed.", style = t.meta, color = c.muted)
                }
            }
            Section("Snooze") {
                PillRow(
                    options = listOf(5, 10, 15, 30).map { it to formatInterval(it) },
                    isSelected = { it == vm.snoozeMinutes },
                    onSelect = { vm.snoozeMinutes = it },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate", style = t.body, color = c.fg, modifier = Modifier.weight(1f))
                Switch(checked = vm.vibrate, onCheckedChange = { vm.vibrate = it })
            }
            if (!vm.isNew) {
                Text(
                    "Delete reminder",
                    style = t.reminderName,
                    color = c.danger,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.delete(onClose) }.padding(vertical = Spacing.m),
                )
            }
        }
    }

    picking?.let { which ->
        val initial = if (which == "start") vm.windowStart else vm.windowEnd
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (which == "start") vm.windowStart = m else vm.windowEnd = m
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(label)
        content()
    }
}

@Composable
private fun TimeBox(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = IntervalTheme.colors
    Column(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Text(text, style = IntervalTheme.type.countdown.copy(fontSize = IntervalTheme.type.screenTitle.fontSize * 1.5f), color = c.fg)
        Box(Modifier.fillMaxWidth().heightIn(min = 2.dp, max = 2.dp).background(c.fg))
    }
}
