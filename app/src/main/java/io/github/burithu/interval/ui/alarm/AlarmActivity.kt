package io.github.burithu.interval.ui.alarm

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.burithu.interval.AppContainer
import io.github.burithu.interval.IntervalApp
import io.github.burithu.interval.data.ThemeMode
import io.github.burithu.interval.scheduling.RingAction
import io.github.burithu.interval.ui.formatClock
import io.github.burithu.interval.ui.formatInterval
import io.github.burithu.interval.ui.theme.CappedFontScale
import io.github.burithu.interval.ui.theme.IntervalTheme
import io.github.burithu.interval.ui.theme.Sizes
import io.github.burithu.interval.ui.theme.Spacing
import io.github.burithu.interval.ui.theme.Strokes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private const val SESSION_WAIT_MS = 5_000L

/** Full-screen alarm over the lock screen (screen 10). Closes when the ring stops for any reason. */
class AlarmActivity : ComponentActivity() {
    private var reminderId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        reminderId = intent.getLongExtra(EXTRA_ID, -1)
        val container = (application as IntervalApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            IntervalTheme(mode = settings?.themeMode ?: ThemeMode.SYSTEM) {
                AlarmScreen(container, reminderId, onDone = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderId = intent.getLongExtra(EXTRA_ID, -1)
    }

    companion object {
        const val EXTRA_ID = "reminder_id"

        fun intent(context: Context, reminderId: Long): Intent =
            Intent(context, AlarmActivity::class.java)
                .setData("interval://reminder/$reminderId".toUri())
                .putExtra(EXTRA_ID, reminderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        /** Best effort: the notification's full-screen intent remains the fallback. */
        fun open(context: Context, reminderId: Long) {
            try {
                context.startActivity(intent(context, reminderId))
            } catch (_: RuntimeException) {
                // Background start refused by the system.
            }
        }
    }
}

@Composable
private fun AlarmScreen(container: AppContainer, id: Long, onDone: () -> Unit) {
    val c = IntervalTheme.colors
    val t = IntervalTheme.type
    val sessions by container.ringer.sessions.collectAsStateWithLifecycle()
    val session = sessions[id]
    // Opened from the alarm broadcast, the screen can appear just before the ring session exists.
    var seen by remember(id) { mutableStateOf(false) }
    if (session != null) seen = true
    LaunchedEffect(id, session == null) {
        if (session == null) {
            if (!seen) delay(SESSION_WAIT_MS)
            onDone()
        }
    }
    if (session == null) return

    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(250)
        }
    }
    val r = session.reminder
    val total = Duration.between(session.startedAt, session.endsAt ?: session.startedAt).toMillis().coerceAtLeast(1)
    val left = Duration.between(now, session.endsAt ?: now).toMillis().coerceAtLeast(0)
    val act: (RingAction) -> Unit = { action ->
        container.scope.launch { container.engine.onAction(id, action) }
        onDone()
    }

    // The alarm screen doesn't scroll, so its text scales only so far: Dismiss must stay on screen.
    CappedFontScale { Column(
        Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("INTERVAL · ALARM", style = t.fieldLabel, color = c.muted)
            Text(formatClock(now, zone), style = t.time, color = c.fg)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(r.label.uppercase(), style = t.alarmLabel, color = c.fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("Every ${formatInterval(r.intervalMinutes)}", style = t.body, color = c.muted)
            Box(Modifier.fillMaxWidth().height(Strokes.progress).background(c.track)) {
                Box(Modifier.fillMaxWidth(left.toFloat() / total).height(Strokes.progress).background(c.accent))
            }
            val s = (left + 999) / 1000
            Text("Stops in %d:%02d and counts as missed".format(s / 60, s % 60), style = t.meta, color = c.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = Sizes.alarmPrimary).background(c.accent)
                    .clickable(role = Role.Button) { act(RingAction.DISMISS) },
                contentAlignment = Alignment.Center,
            ) { Text("DISMISS", style = t.screenTitle, color = c.onAccent) }
            Box(
                Modifier.fillMaxWidth().heightIn(min = Sizes.rowSingle).border(Strokes.control, c.fg)
                    .clickable(role = Role.Button) { act(RingAction.SNOOZE) },
                contentAlignment = Alignment.Center,
            ) { Text("SNOOZE ${formatInterval(r.snoozeMinutes).uppercase()}", style = t.sheetTitle, color = c.fg) }
        }
    } }
}
