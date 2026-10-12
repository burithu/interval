package io.github.burithu.interval.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.AppSettings
import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.ReminderState
import io.github.burithu.interval.data.TimingMode
import io.github.burithu.interval.data.db.toDomain
import io.github.burithu.interval.onboarding.SetupChecks
import io.github.burithu.interval.ui.BottomBar
import io.github.burithu.interval.ui.Glyph
import io.github.burithu.interval.ui.IconButton
import io.github.burithu.interval.ui.ListRow
import io.github.burithu.interval.ui.SmallButton
import io.github.burithu.interval.ui.formatInterval
import io.github.burithu.interval.ui.formatNext
import io.github.burithu.interval.ui.formatWindow
import io.github.burithu.interval.ui.formatDays
import io.github.burithu.interval.ui.reminderMeta
import io.github.burithu.interval.ui.theme.CappedFontScale
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Sizes
import io.github.burithu.interval.ui.theme.Spacing
import io.github.burithu.interval.ui.theme.Strokes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class HomeRow(val reminder: Reminder, val state: ReminderState?)

/** One-tap starters for the empty state (screen 04). */
private val STARTERS = listOf(
    Reminder(label = "Drink water", intervalMinutes = 45, windowStart = 8 * 60, windowEnd = 22 * 60, days = Days.EVERY_DAY),
    Reminder(label = "Stand up & walk", intervalMinutes = 60, windowStart = 9 * 60, windowEnd = 18 * 60, days = Days.WEEKDAYS),
    Reminder(label = "Stretch", intervalMinutes = 120, windowStart = 9 * 60, windowEnd = 18 * 60, days = Days.WEEKDAYS),
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    /** Soonest ring first, switched-off reminders last (D29). */
    val rows: StateFlow<List<HomeRow>> = combine(
        c.db.reminderDao().observeAll(),
        c.db.stateDao().observeAll(),
    ) { reminders, states ->
        val byId = states.associateBy { it.reminderId }
        reminders.map { HomeRow(it.toDomain(), byId[it.id]?.toDomain()) }
            .sortedWith(compareBy({ !it.reminder.enabled }, { it.state?.nextRingAt ?: Instant.MAX }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setEnabled(id: Long, on: Boolean) {
        viewModelScope.launch { c.engine.setEnabled(id, on) }
    }

    fun addStarter(r: Reminder) {
        viewModelScope.launch {
            c.engine.save(r.copy(createdAt = Instant.now(), snoozeMinutes = c.settings.current().defaultSnoozeMinutes))
        }
    }

    fun ensureScheduled() {
        viewModelScope.launch { c.engine.ensureScheduled() }
    }

    fun pause(until: Instant) {
        viewModelScope.launch { c.engine.pauseUntil(until) }
    }

    fun resume() {
        viewModelScope.launch { c.engine.resume() }
    }
}

@Composable
fun HomeScreen(vm: HomeViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit, onSetup: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val rows by vm.rows.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        vm.ensureScheduled()
        while (true) {
            now = Instant.now()
            delay(TICK_MS)
        }
    }

    val pausedUntil = settings.pausedUntil?.takeIf { it > now }
    var showPause by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            Modifier.weight(1f).statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        ) {
            item(key = "header") {
                // Title, chip and gear share one line; capped so large fonts can't split "REMINDERS".
                CappedFontScale {
                    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.rowSingle), verticalAlignment = Alignment.CenterVertically) {
                        Text("REMINDERS", style = t.screenTitle, color = c.fg, modifier = Modifier.weight(1f), softWrap = false)
                        if (rows.isNotEmpty()) PauseChip(paused = pausedUntil != null) { showPause = true }
                        IconButton(Glyph.GEAR, "Settings", onSettings)
                    }
                }
            }
            item(key = "banner") { ReliabilityBanner(settings.confirmedSteps, onSetup) }
            item(key = "hero") {
                if (pausedUntil != null) PausedHero(pausedUntil, now, zone, onResume = vm::resume)
                else CountdownHero(rows, now, zone)
            }
            if (rows.isEmpty()) {
                items(STARTERS, key = { "starter-${it.label}" }) { starter ->
                    StarterRow(starter, onAdd = { vm.addStarter(starter) })
                    HorizontalDivider(color = c.line)
                }
            } else {
                items(rows, key = { it.reminder.id }) { row ->
                    ReminderRow(row, now, zone, paused = pausedUntil != null, onClick = { onOpen(row.reminder.id) }, onToggle = { vm.setEnabled(row.reminder.id, it) })
                    HorizontalDivider(color = c.line)
                }
            }
        }
        BottomBar("+ Add reminder", onAdd)
    }

    if (showPause) {
        PauseSheet(
            now = now,
            zone = zone,
            pausedUntil = pausedUntil,
            onPause = { vm.pause(it); showPause = false },
            onResume = { vm.resume(); showPause = false },
            onDismiss = { showPause = false },
        )
    }
}

/** Screen 05 hero: soonest ring across all reminders, or what to do when there is none. */
@Composable
private fun CountdownHero(rows: List<HomeRow>, now: Instant, zone: ZoneId) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val active = rows.filter { it.reminder.enabled }
    val ringing = active.firstOrNull { it.state?.phase == Phase.RINGING }
    val next = active
        .filter { it.state?.phase != Phase.RINGING && it.state?.nextRingAt != null }
        .minByOrNull { it.state!!.nextRingAt!! }

    val label: String
    val big: AnnotatedString
    val name: String
    val detail: String
    when {
        ringing != null -> {
            label = "RINGING NOW"
            big = AnnotatedString("Now")
            name = ringing.reminder.label
            detail = "Dismiss or snooze it from the notification."
        }
        next != null -> {
            val at = next.state!!.nextRingAt!!
            val r = next.reminder
            label = "NEXT UP"
            big = countdownText(Duration.between(now, at), c.fg)
            name = r.label
            detail = "Rings at ${formatNext(at, now, zone)} · " +
                if (r.timingMode == TimingMode.FIXED) "then every ${formatInterval(r.intervalMinutes)}"
                else "restarts when dismissed"
        }
        rows.isEmpty() -> {
            label = "NEXT UP"
            big = AnnotatedString("—")
            name = "No reminders yet"
            detail = "Start with one of these, or add your own."
        }
        active.isEmpty() -> {
            label = "NEXT UP"
            big = AnnotatedString("—")
            name = "All reminders are off"
            detail = "Switch one on below."
        }
        else -> {
            label = "NEXT UP"
            big = AnnotatedString("—")
            name = "Nothing left to ring"
            detail = "No active window ahead on the selected days."
        }
    }
    val showMuted = ringing == null && next == null

    Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Text(label, style = t.fieldLabel, color = c.muted)
        AnimatedContent(
            targetState = big,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "countdown",
        ) { text ->
            CappedFontScale {
                Text(
                    text,
                    style = t.countdown,
                    color = if (showMuted) c.muted else c.accent,
                    modifier = Modifier.semantics { contentDescription = "$label: ${text.text}" },
                )
            }
        }
        Text(name, style = t.title, color = c.fg)
        Spacer(Modifier.height(Spacing.xs))
        Text(detail, style = t.meta, color = c.muted)
        HeroRule()
    }
}

/** The 2dp rule that closes both heroes, at the same distance so pausing doesn't shift the list. */
@Composable
internal fun HeroRule() {
    Spacer(Modifier.height(Spacing.l))
    Box(Modifier.fillMaxWidth().height(Strokes.rule).background(IntervalTheme.colors.fg))
}

/** "12 min", "1 h 42 min", "< 1 min". Numbers stay big; units drop to body size. */
@Composable
private fun countdownText(left: Duration, unitColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    val minutes = (left.seconds + 59) / 60
    val unit = IntervalTheme.type.countdownUnit.toSpanStyle().copy(color = unitColor)
    return buildAnnotatedString {
        when {
            minutes < 1 -> { append("<1"); withStyle(unit) { append(" min") } }
            minutes < 60 -> { append("$minutes"); withStyle(unit) { append(" min") } }
            else -> {
                append("${minutes / 60}"); withStyle(unit) { append(" h ") }
                if (minutes % 60 != 0L) { append("${minutes % 60}"); withStyle(unit) { append(" min") } }
            }
        }
    }
}

@Composable
private fun ReminderRow(row: HomeRow, now: Instant, zone: ZoneId, paused: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val r = row.reminder
    val next = when {
        !r.enabled -> "Off"
        paused -> "Paused"
        row.state?.phase == Phase.RINGING -> "Ringing"
        row.state?.nextRingAt != null -> formatNext(row.state.nextRingAt, now, zone)
        else -> "—"
    }
    ListRow(twoLine = true, onClick = onClick) {
        // The meta line wraps rather than cutting off the schedule: two lines normally, more at large font sizes.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(r.label, style = t.reminderName, color = if (r.enabled) c.fg else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(reminderMeta(r), style = t.meta, color = c.muted)
        }
        Text(next, style = t.nextIn, color = if (r.enabled && !paused) c.accent else c.muted)
        // Named so TalkBack says which reminder the switch turns on or off.
        Box(
            Modifier.toggleable(value = r.enabled, role = Role.Switch, onValueChange = onToggle)
                .clearAndSetSemantics { contentDescription = r.label },
        ) { Switch(checked = r.enabled, onCheckedChange = null) }
    }
}

@Composable
private fun StarterRow(r: Reminder, onAdd: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    ListRow(twoLine = true, onClick = null) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(r.label, style = t.reminderName, color = c.fg)
            Text(reminderMeta(r, withTiming = false), style = t.meta, color = c.muted)
        }
        SmallButton("+ Add", onAdd)
    }
}

/** Screen 07: shown while a required setup step is detected as off. */
@Composable
private fun ReliabilityBanner(confirmed: Set<String>, onFix: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val missing = remember(refresh, confirmed) { SetupChecks.missingRequired(context, confirmed) }
    if (missing.isEmpty()) return
    Column {
        Row(
            Modifier.fillMaxWidth().background(c.fg).clickable(role = Role.Button, onClick = onFix).padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text("Reminders may be late.", style = t.reminderName, color = c.bg)
                Text(missing.joinToString(" · ") { it.title }, style = t.meta, color = c.bg)
            }
            Text("FIX", style = t.sheetTitle, color = c.bg, textDecoration = TextDecoration.Underline)
        }
        Spacer(Modifier.height(Spacing.m))
    }
}

private const val TICK_MS = 5_000L
