package io.github.burithu.interval.ui.home

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.ReminderState
import io.github.burithu.interval.data.db.toDomain
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
}

@Composable
fun HomeScreen(vm: HomeViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit, onLog: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val rows by vm.rows.collectAsStateWithLifecycle()
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
                    "LOG",
                    style = t.fieldLabel,
                    color = c.muted,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onLog).padding(Spacing.m),
                )
            }
            PermissionPanel()
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

/** Temporary M2 setup panel. Replaced by onboarding screens and the warning banner in M3. */
@Composable
private fun PermissionPanel() {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val context = LocalContext.current
    var notifOk by remember { mutableStateOf(notificationsAllowed(context)) }
    var exactOk by remember { mutableStateOf(exactAlarmsAllowed(context)) }
    var fullScreenOk by remember { mutableStateOf(fullScreenAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        notifOk = notificationsAllowed(context)
        exactOk = exactAlarmsAllowed(context)
        fullScreenOk = fullScreenAllowed(context)
        onPauseOrDispose { }
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }
    if (notifOk && exactOk && fullScreenOk) return
    Column(
        Modifier.fillMaxWidth().background(c.fg).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text("Reminders may be late or silent.", style = t.reminderName, color = c.bg)
        if (!notifOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                "ALLOW NOTIFICATIONS",
                style = t.fieldLabel,
                color = c.bg,
                modifier = Modifier.clickable { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }.padding(vertical = Spacing.s),
            )
        }
        if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                "ALLOW EXACT ALARMS",
                style = t.fieldLabel,
                color = c.bg,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                    )
                }.padding(vertical = Spacing.s),
            )
        }
        if (!fullScreenOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Text(
                "ALLOW FULL-SCREEN ALARMS",
                style = t.fieldLabel,
                color = c.bg,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()),
                    )
                }.padding(vertical = Spacing.s),
            )
        }
    }
    Spacer(Modifier.height(Spacing.m))
}

private fun fullScreenAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun exactAlarmsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
