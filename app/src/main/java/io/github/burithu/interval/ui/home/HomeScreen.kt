package io.github.burithu.interval.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.AppSettings
import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.ReminderState
import io.github.burithu.interval.data.db.toDomain
import io.github.burithu.interval.onboarding.SetupChecks
import io.github.burithu.interval.ui.BottomBar
import io.github.burithu.interval.ui.formatNext
import io.github.burithu.interval.ui.reminderMeta
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class HomeRow(val reminder: Reminder, val state: ReminderState?)

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

    fun setEnabled(id: Long, on: Boolean) {
        viewModelScope.launch { c.engine.setEnabled(id, on) }
    }

    fun ensureScheduled() {
        viewModelScope.launch { c.engine.ensureScheduled() }
    }

    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}

@Composable
fun HomeScreen(vm: HomeViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onLog: () -> Unit, onSetup: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val rows by vm.rows.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        vm.ensureScheduled()
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).statusBarsPadding().padding(horizontal = Spacing.screen),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("REMINDERS", style = t.screenTitle, color = c.fg, modifier = Modifier.weight(1f))
                Text(
                    "SETUP",
                    style = t.fieldLabel,
                    color = c.muted,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSetup).padding(Spacing.m),
                )
                Text(
                    "LOG",
                    style = t.fieldLabel,
                    color = c.muted,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onLog).padding(Spacing.m),
                )
            }
            ReliabilityBanner(settings.confirmedSteps, onSetup)
            if (rows.isEmpty()) {
                Spacer(Modifier.height(Spacing.xl))
                Text("No reminders yet", style = t.reminderName, color = c.fg)
                Text("Tap Add reminder to create one.", style = t.meta, color = c.muted)
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(rows, key = { it.reminder.id }) { row ->
                    ReminderRow(row, now, zone, onClick = { onOpen(row.reminder.id) }, onToggle = { vm.setEnabled(row.reminder.id, it) })
                    HorizontalDivider(color = c.line)
                }
            }
        }
        BottomBar("+ Add reminder", onAdd)
    }
}

@Composable
private fun ReminderRow(row: HomeRow, now: Instant, zone: ZoneId, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val r = row.reminder
    val next = when {
        !r.enabled -> "Off"
        row.state?.phase == Phase.RINGING -> "Ringing"
        row.state?.nextRingAt != null -> formatNext(row.state.nextRingAt, now, zone)
        else -> "—"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.label, style = t.reminderName, color = if (r.enabled) c.fg else c.muted)
            Text(reminderMeta(r), style = t.meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(next, style = t.nextIn, color = if (r.enabled) c.accent else c.muted)
        Switch(checked = r.enabled, onCheckedChange = onToggle)
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
    Row(
        Modifier.fillMaxWidth().background(c.fg).clickable(role = Role.Button, onClick = onFix).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Reminders may be late.", style = t.reminderName, color = c.bg)
            Text(missing.joinToString(" · ") { it.title }, style = t.meta, color = c.bg)
        }
        Text("FIX", style = t.sheetTitle, color = c.bg, textDecoration = TextDecoration.Underline)
    }
    Spacer(Modifier.height(Spacing.m))
}