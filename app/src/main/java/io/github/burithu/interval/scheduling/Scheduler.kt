package io.github.burithu.interval.scheduling

import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.TimingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Something that happened to a reminder, which decides when it rings next. */
sealed interface ScheduleEvent {
    val at: Instant

    /** Turned on, saved after an edit, or created. Schedules fresh from [at]. */
    data class Enabled(override val at: Instant) : ScheduleEvent

    /** Dismissed by button or by swiping the notification away. */
    data class Dismissed(override val at: Instant) : ScheduleEvent

    /** No response before the missed timeout or the alarm ring length ran out. */
    data class Missed(override val at: Instant) : ScheduleEvent

    data class Snoozed(override val at: Instant) : ScheduleEvent

    /** Pause ended (expired or Resume tapped). */
    data class Resumed(override val at: Instant) : ScheduleEvent

    /**
     * Recompute after reboot, app update, time or time-zone change, or backup restore.
     * [storedNext] is the previously scheduled ring, if any.
     */
    data class Rescheduled(override val at: Instant, val storedNext: Instant?) : ScheduleEvent
}

/**
 * The single source of truth for when a reminder rings next (SPEC §3).
 *
 * Pure: no Android, no clock, no storage. Times move in elapsed time, so across a
 * daylight-saving change "1 hour later" is always 60 real minutes. Window boundaries
 * are local clock times resolved in [ZoneId]; a boundary inside a DST gap shifts forward.
 */
object Scheduler {

    /** How far ahead to search for an active window. Covers any non-empty day set. */
    private const val SEARCH_DAYS = 9L

    /**
     * Returns the next ring time, or null if the reminder is off or has no active days.
     * If [pausedUntil] is later than the computed ring, the reminder resumes from the pause end.
     */
    fun nextRing(
        reminder: Reminder,
        event: ScheduleEvent,
        zone: ZoneId,
        pausedUntil: Instant? = null,
    ): Instant? {
        if (!reminder.enabled || reminder.days.isEmpty) return null
        val ring = compute(reminder, event, zone)
        if (ring != null && pausedUntil != null && ring < pausedUntil) {
            return compute(reminder, ScheduleEvent.Resumed(pausedUntil), zone)
        }
        return ring
    }

    private fun compute(r: Reminder, event: ScheduleEvent, zone: ZoneId): Instant? = when (r.timingMode) {
        TimingMode.RESTART -> when (event) {
            is ScheduleEvent.Snoozed -> event.at + r.snooze
            // A stored ring is kept only if it is still inside a window: after a time-zone change
            // it can land at, say, 01:00 local for a 09:00–18:00 reminder.
            is ScheduleEvent.Rescheduled ->
                event.storedNext?.takeIf { it > event.at && r.isInWindow(it, zone) } ?: r.restartFrom(event.at, zone)
            else -> r.restartFrom(event.at, zone)
        }
        TimingMode.FIXED -> when (event) {
            is ScheduleEvent.Snoozed -> {
                // The snooze is dropped if the next grid slot comes first.
                val snoozeRing = event.at + r.snooze
                val grid = r.nextGridSlot(event.at, zone, inclusive = false)
                if (grid != null && grid < snoozeRing) grid else snoozeRing
            }
            is ScheduleEvent.Resumed -> r.nextGridSlot(event.at, zone, inclusive = true)
            else -> r.nextGridSlot(event.at, zone, inclusive = false)
        }
    }

    /**
     * Restart mode. Inside a window: [t] + interval, if that is still inside it.
     * Otherwise the first ring of the next window: window start + interval (D23).
     */
    private fun Reminder.restartFrom(t: Instant, zone: ZoneId): Instant? {
        // Back-to-back windows (24 h every day) are merged so the timer runs straight through.
        for (w in windowsFrom(t, zone, mergeTouching = true)) {
            if (t >= w.start) {
                val candidate = t + interval
                if (candidate <= w.end) return candidate
                continue
            }
            return firstRestartRing(w)
        }
        return null
    }

    private fun Reminder.isInWindow(t: Instant, zone: ZoneId): Boolean =
        windowsFrom(t, zone, mergeTouching = true).any { t >= it.start && t <= it.end }

    /** If the interval is longer than the window, ring once at window start, as in fixed mode. */
    private fun Reminder.firstRestartRing(w: Window): Instant =
        (w.start + interval).takeIf { it <= w.end } ?: w.start

    /**
     * Fixed mode. Slots are window start + n × interval up to and including window end.
     * Each window restarts the grid at its own start.
     */
    private fun Reminder.nextGridSlot(after: Instant, zone: ZoneId, inclusive: Boolean): Instant? {
        val stepMs = interval.toMillis()
        for (w in windowsFrom(after, zone, mergeTouching = false)) {
            if (w.start > after || (inclusive && w.start == after)) return w.start
            val k = Duration.between(w.start, after).toMillis() / stepMs
            var slot = w.start + interval.multipliedBy(k)
            if (slot < after || (!inclusive && slot == after)) slot += interval
            if (slot <= w.end) return slot
        }
        return null
    }

    private data class Window(val start: Instant, val end: Instant)

    /**
     * Active windows that end at or after [t], earliest first. Starts one day before [t]
     * so an overnight or 24-hour window that began yesterday is included.
     */
    private fun Reminder.windowsFrom(t: Instant, zone: ZoneId, mergeTouching: Boolean): List<Window> {
        val firstDate = t.atZone(zone).toLocalDate().minusDays(1)
        val all = (0..SEARCH_DAYS).map { firstDate.plusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { window(it, zone) }
        val merged = if (!mergeTouching) all else all.fold(mutableListOf<Window>()) { acc, w ->
            val last = acc.lastOrNull()
            if (last != null && w.start <= last.end) acc[acc.lastIndex] = Window(last.start, maxOf(last.end, w.end))
            else acc += w
            acc
        }
        return merged.filter { it.end >= t }
    }

    private fun Reminder.window(startDate: LocalDate, zone: ZoneId): Window {
        val endDate = if (windowEnd > windowStart) startDate else startDate.plusDays(1)
        return Window(
            start = startDate.atTime(minuteOfDay(windowStart)).atZone(zone).toInstant(),
            end = endDate.atTime(minuteOfDay(windowEnd)).atZone(zone).toInstant(),
        )
    }

    private fun minuteOfDay(m: Int): LocalTime = LocalTime.of(m / 60, m % 60)

    private val Reminder.interval: Duration get() = Duration.ofMinutes(intervalMinutes.toLong())
    private val Reminder.snooze: Duration get() = Duration.ofMinutes(snoozeMinutes.toLong())
}
