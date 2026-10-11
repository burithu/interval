package io.github.burithu.interval.ringing

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** One reminder making noise right now. [endsAt] is set for alarms (ring length). */
data class RingSession(val reminder: Reminder, val startedAt: Instant, val endsAt: Instant?)

/**
 * Which reminders are sounding. [RingService] plays them; [AlarmActivity][io.github.burithu.interval.ui.alarm.AlarmActivity]
 * shows them. Removing a session stops its sound.
 */
class Ringer(private val context: Context, private val notifier: Notifier) {
    private val _sessions = MutableStateFlow<Map<Long, RingSession>>(emptyMap())
    val sessions: StateFlow<Map<Long, RingSession>> = _sessions

    fun start(r: Reminder, now: Instant) {
        val endsAt = if (r.ringStyle == RingStyle.ALARM) now.plusSeconds(r.ringSeconds.toLong()) else null
        _sessions.update { it + (r.id to RingSession(r, now, endsAt)) }
        val intent = Intent(context, RingService::class.java)
            .setAction(RingService.ACTION_START)
            .putExtra(RingService.EXTRA_ID, r.id)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: IllegalStateException) {
            // Background start refused (should not happen from setAlarmClock). Show it silently at least.
            _sessions.update { it - r.id }
            notifier.post(r.id, notifier.buildRing(r))
        }
    }

    fun stop(id: Long) = _sessions.update { it - id }
}
