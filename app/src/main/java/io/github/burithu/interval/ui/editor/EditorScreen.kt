package io.github.burithu.interval.ui.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.AlarmMode
import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import io.github.burithu.interval.data.TimingMode
import io.github.burithu.interval.data.db.toDomain
import io.github.burithu.interval.ui.FieldLabel
import io.github.burithu.interval.ui.ChoiceDialog
import io.github.burithu.interval.ui.Glyph
import io.github.burithu.interval.ui.GlyphIcon
import io.github.burithu.interval.ui.ListRow
import io.github.burithu.interval.ui.PillRow
import io.github.burithu.interval.ui.TopBar
import io.github.burithu.interval.ui.SettingRow
import io.github.burithu.interval.ui.Segmented
import io.github.burithu.interval.ui.formatInterval
import io.github.burithu.interval.ui.formatMinuteOfDay
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing
import io.github.burithu.interval.ui.theme.Strokes
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
    var alarmMode by mutableStateOf(AlarmMode.FULL)
    var snoozeMinutes by mutableIntStateOf(10)
    var ringSeconds by mutableIntStateOf(60)
    var soundUri by mutableStateOf<String?>(null)
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
                    windowEnd = r.windowEnd; days = r.days; timingMode = r.timingMode; ringStyle = r.ringStyle; alarmMode = r.alarmMode
                    snoozeMinutes = r.snoozeMinutes; ringSeconds = r.ringSeconds; soundUri = r.soundUri; vibrate = r.vibrate
                }
            }
        }
    }

    val canSave: Boolean get() = label.isNotBlank() && !days.isEmpty && intervalMinutes in 1..1440

    fun toggleDay(d: DayOfWeek) {
        days = Days(days.mask xor Days.of(d).mask)
    }

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        val base = original ?: Reminder(label = "", createdAt = Instant.now())
        val r = base.copy(
            label = label.trim().take(40), intervalMinutes = intervalMinutes, windowStart = windowStart,
            windowEnd = windowEnd, days = days, timingMode = timingMode, ringStyle = ringStyle, alarmMode = alarmMode,
            snoozeMinutes = snoozeMinutes, ringSeconds = ringSeconds, soundUri = soundUri, vibrate = vibrate, enabled = true,
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

private val PRESETS = listOf(20, 45, 60, 120)
private const val CUSTOM = -1

private sealed interface Picker {
    data class Time(val start: Boolean) : Picker
    data object Interval : Picker
    data object Snooze : Picker
    data object RingFor : Picker
    data object Delete : Picker
}

/** Screen 09. One scrolling form for new and existing reminders. */
@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val context = LocalContext.current
    var picker by remember { mutableStateOf<Picker?>(null) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.parcelableUri(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            vm.soundUri = uri?.takeUnless { it == RingtoneManager.getDefaultUri(ringtoneType(vm.ringStyle)) }?.toString()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(
            title = if (vm.isNew) "NEW REMINDER" else "EDIT REMINDER",
            nav = Glyph.CLOSE, navLabel = "Close without saving", onNav = onClose,
        ) { SaveAction(enabled = vm.canSave) { vm.save(onClose) } }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Section("Label") {
                BasicTextField(
                    value = vm.label,
                    onValueChange = { vm.label = it.take(40) },
                    singleLine = true,
                    textStyle = t.title.copy(color = c.fg),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Column {
                            Box(Modifier.padding(vertical = Spacing.s)) {
                                if (vm.label.isEmpty()) Text("Drink water", style = t.title, color = c.muted)
                                inner()
                            }
                            Box(Modifier.fillMaxWidth().height(Strokes.rule).background(c.fg))
                        }
                    },
                )
            }
            Section("Repeat every") {
                val isPreset = vm.intervalMinutes in PRESETS
                PillRow(
                    options = PRESETS.map { it to formatInterval(it) } +
                        (CUSTOM to if (isPreset) "Custom" else "Custom · ${formatInterval(vm.intervalMinutes)}"),
                    isSelected = { if (it == CUSTOM) !isPreset else it == vm.intervalMinutes },
                    onSelect = { if (it == CUSTOM) picker = Picker.Interval else vm.intervalMinutes = it },
                )
            }
            Section("Active between") {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    TimeBox(formatMinuteOfDay(vm.windowStart), null, Modifier.weight(1f)) { picker = Picker.Time(start = true) }
                    GlyphIcon(Glyph.ARROW_RIGHT, modifier = Modifier.padding(bottom = Spacing.s))
                    val badge = when {
                        vm.windowEnd == vm.windowStart -> "24 H"
                        vm.windowEnd < vm.windowStart -> "+1 DAY"
                        else -> null
                    }
                    TimeBox(formatMinuteOfDay(vm.windowEnd), badge, Modifier.weight(1f)) { picker = Picker.Time(start = false) }
                }
                when {
                    vm.windowEnd == vm.windowStart -> Hint("Same start and end: active all day.", accent = true)
                    vm.windowEnd < vm.windowStart -> Hint("Ends the next day. Days below are the days it starts.", accent = true)
                }
            }
            Section("Days") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in vm.days
                        // At least 48dp tall: on narrow phones the boxes can't be 48dp wide as well.
                        Box(
                            Modifier.weight(1f).heightIn(min = Spacing.minTouch)
                                .background(if (on) c.accent else c.bg)
                                .border(Strokes.control, if (on) c.accent else c.fg)
                                .clickable(role = Role.Checkbox) { vm.toggleDay(d) },
                            contentAlignment = Alignment.Center,
                        ) { Text(d.name.take(1), style = t.reminderName, color = if (on) c.onAccent else c.fg) }
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
                Hint(
                    if (vm.timingMode == TimingMode.RESTART) "Next ring ${formatInterval(vm.intervalMinutes)} after you dismiss."
                    else "Rings at ${fixedSlots(vm.windowStart, vm.windowEnd, vm.intervalMinutes)} no matter when you dismiss.",
                )
            }
            Section("Ring as") {
                Segmented(
                    options = listOf(RingStyle.NOTIFICATION to "Notification", RingStyle.ALARM to "Alarm"),
                    selected = vm.ringStyle,
                    onSelect = { vm.ringStyle = it },
                )
                if (vm.ringStyle == RingStyle.ALARM) {
                    Segmented(
                        options = listOf(AlarmMode.SCREEN to "Screen only", AlarmMode.VIBRATE to "Vibrate", AlarmMode.FULL to "Full alarm"),
                        selected = vm.alarmMode,
                        onSelect = { vm.alarmMode = it },
                    )
                }
                Hint(
                    when {
                        vm.ringStyle == RingStyle.NOTIFICATION -> "A chime and vibration."
                        vm.alarmMode == AlarmMode.SCREEN -> "Full-screen alarm, no sound or vibration."
                        vm.alarmMode == AlarmMode.VIBRATE -> "Full-screen alarm that vibrates."
                        else -> "Full-screen alarm with sound and vibration."
                    } + " On silent it only shows; on vibrate it makes no sound.",
                )
            }
            Column {
                if (vm.ringStyle == RingStyle.ALARM) {
                    SettingRow("Ring for", "Then it stops and counts as missed.", ringLength(vm.ringSeconds)) { picker = Picker.RingFor }
                    HorizontalDivider(color = c.line)
                }
                SettingRow("Snooze", null, formatInterval(vm.snoozeMinutes)) { picker = Picker.Snooze }
                // Sound and Vibrate only where they apply: an alarm's mode already says whether it
                // makes a sound or vibrates.
                val alarm = vm.ringStyle == RingStyle.ALARM
                if (!alarm || vm.alarmMode == AlarmMode.FULL) {
                    HorizontalDivider(color = c.line)
                    SettingRow("Sound", null, soundTitle(context, vm.soundUri)) {
                        soundPicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType(vm.ringStyle))
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    vm.soundUri?.toUri() ?: RingtoneManager.getDefaultUri(ringtoneType(vm.ringStyle)),
                                ),
                        )
                    }
                }
                if (!alarm) {
                    HorizontalDivider(color = c.line)
                    ListRow(twoLine = false, onClick = { vm.vibrate = !vm.vibrate }, role = Role.Switch) {
                        Text("Vibrate", style = t.body, color = c.fg, modifier = Modifier.weight(1f))
                        Switch(checked = vm.vibrate, onCheckedChange = { vm.vibrate = it })
                    }
                }
            }
            if (!vm.isNew) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).clickable(role = Role.Button) { picker = Picker.Delete },
                    contentAlignment = Alignment.Center,
                ) { Text("Delete reminder", style = t.reminderName, color = c.danger) }
            }
        }
    }

    when (val p = picker) {
        is Picker.Time -> TimeDialog(
            initial = if (p.start) vm.windowStart else vm.windowEnd,
            onDismiss = { picker = null },
            onPick = { m -> if (p.start) vm.windowStart = m else vm.windowEnd = m; picker = null },
        )
        Picker.Interval -> IntervalDialog(vm.intervalMinutes, onDismiss = { picker = null }) { vm.intervalMinutes = it; picker = null }
        Picker.Snooze -> ChoiceDialog("Snooze", listOf(5, 10, 15, 30).map { it to formatInterval(it) }, vm.snoozeMinutes,
            onDismiss = { picker = null }) { vm.snoozeMinutes = it; picker = null }
        Picker.RingFor -> ChoiceDialog("Ring for", listOf(30, 60, 120, 300).map { it to ringLength(it) }, vm.ringSeconds,
            onDismiss = { picker = null }) { vm.ringSeconds = it; picker = null }
        Picker.Delete -> AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Delete \"${vm.label}\"?") },
            text = { Text("It stops ringing and can't be restored.") },
            confirmButton = { TextButton(onClick = { picker = null; vm.delete(onClose) }) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun SaveAction(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = Spacing.minTouch).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m),
        contentAlignment = Alignment.Center,
    ) { Text("SAVE", style = IntervalTheme.type.sheetTitle, color = if (enabled) IntervalTheme.colors.accent else IntervalTheme.colors.muted) }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FieldLabel(label)
        content()
    }
}

@Composable
private fun Hint(text: String, accent: Boolean = false) {
    Text(
        text,
        style = IntervalTheme.type.meta,
        color = if (accent) IntervalTheme.colors.accent else IntervalTheme.colors.muted,
    )
}

@Composable
private fun TimeBox(time: String, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    // The badge sits beside the time, top-aligned, so it never changes the row height:
    // both TimeBoxes keep the same baseline and their underlines line up.
    Column(modifier.heightIn(min = Spacing.minTouch).clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(time, style = t.time, color = c.fg)
            if (badge != null) {
                Text(badge, style = t.fieldLabel, color = c.accent, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
        Box(Modifier.fillMaxWidth().height(Strokes.rule).background(c.fg))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun IntervalDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current in PRESETS) "" else current.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in 1..1440
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat every") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { s -> text = s.filter(Char::isDigit).take(4) },
                label = { Text("Minutes (1–1440)") },
                supportingText = { if (valid) Text("Every ${formatInterval(value!!)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onPick(value!!) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ringLength(seconds: Int) = if (seconds < 60) "$seconds s" else "${seconds / 60} min"

private fun ringtoneType(style: RingStyle) =
    if (style == RingStyle.ALARM) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION

private fun soundTitle(context: Context, uri: String?): String {
    if (uri == null) return "Default"
    return try {
        RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context) ?: "Custom"
    } catch (_: Exception) {
        "Custom"
    }
}

private fun Intent.parcelableUri(key: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, Uri::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)

/** "09:00, 10:00, 11:00, 12:00 …" for the fixed-schedule hint. */
private fun fixedSlots(start: Int, end: Int, interval: Int): String {
    val length = when {
        end > start -> end - start
        end < start -> end + 1440 - start
        else -> 1440
    }
    val all = generateSequence(0) { it + interval }.takeWhile { it <= length }.take(5).toList()
    val shown = all.take(4).joinToString(", ") { formatMinuteOfDay((start + it) % 1440) }
    return if (all.size > 4) "$shown …" else shown
}
