package io.github.burithu.interval.scheduling

import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import io.github.burithu.interval.data.SettingsStore
import io.github.burithu.interval.data.db.EventLogEntity
import io.github.burithu.interval.data.db.EventType
import io.github.burithu.interval.data.db.IntervalDatabase
import io.github.burithu.interval.data.db.ReminderStateEntity
import io.github.burithu.interval.data.db.toDomain
import io.github.burithu.interval.data.db.toEntity
import io.github.burithu.interval.ringing.AlarmScheduler
import io.github.burithu.interval.ringing.Notifier
import io.github.burithu.interval.ringing.Ringer
import io.github.burithu.interval.ui.formatClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class RingAction { DISMISS, SWIPE, SNOOZE }

/**
 * Applies every state change: edits, rings, notification actions, reschedules.
 * Each change runs under one lock so a ring cannot interleave with an edit.
 */
class ReminderEngine(
    db: IntervalDatabase,
    private val settings: SettingsStore,
    private val alarms: AlarmScheduler,
    private val notifier: Notifier,
    private val ringer: Ringer,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val reminders = db.reminderDao()
    private val states = db.stateDao()
    private val log = db.logDao()
    private val mutex = Mutex()

    /** Creates or updates a reminder and schedules it fresh from now. Returns its id. */
    suspend fun save(reminder: Reminder): Long = mutex.withLock {
        val id = if (reminder.id == 0L) reminders.insert(reminder.toEntity())
        else reminder.id.also { reminders.update(reminder.toEntity()) }
        val saved = reminder.copy(id = id)
        clearRinging(id)
        scheduleFresh(saved, ScheduleEvent.Enabled(clock()))
        record(id, EventType.SAVED)
        id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        val r = reminders.get(id)?.toDomain()?.copy(enabled = enabled) ?: return@withLock
        reminders.update(r.toEntity())
        clearRinging(id)
        scheduleFresh(r, ScheduleEvent.Enabled(clock()))
        record(id, if (enabled) EventType.ENABLED else EventType.DISABLED)
    }

    suspend fun delete(id: Long) = mutex.withLock {
        alarms.cancelRing(id)
        clearRinging(id)
        reminders.delete(id) // state row goes with it (cascade)
        record(id, EventType.DELETED)
    }

    /** A ring alarm fired. */
    suspend fun onRing(id: Long) = mutex.withLock {
        val r = reminders.get(id)?.toDomain() ?: return@withLock
        if (!r.enabled) return@withLock
        val state = states.get(id)
        val now = clock()
        val due = state?.nextRingAt
        if (due != null && now < due - EARLY_TOLERANCE) {
            // Stale alarm (state changed after it was set). Put the real one back.
            alarms.scheduleRing(id, due, r.isAlarm)
            return@withLock
        }
        if (state?.phase == Phase.RINGING) {
            // The previous ring got no response; this is its "if ignored" follow-up.
            record(id, EventType.MISSED, detail = "no response to ring at ${state.ringingSince?.let { formatClock(it, zone()) }}")
        }
        val s = settings.current()
        val timeout = missedTimeout(r)
        // Schedule the next ring now, as if this one will be missed. Dismiss or snooze replaces it.
        // This keeps every ring on setAlarmClock; HyperOS delayed plain exact alarms by up to 23 min.
        val ifIgnored = Scheduler.nextRing(r, ScheduleEvent.Missed(now + timeout), zone(), s.pausedUntil)
        states.upsert(ReminderStateEntity(id, Phase.RINGING, ifIgnored, now))
        ringer.start(r, now)
        schedule(r, ifIgnored)
        // Alarms relabel themselves when their ring length ends; this is the backup.
        alarms.scheduleMissedLabel(id, now + timeout)
        val late = due?.let { Duration.between(it, now).seconds }
        record(id, EventType.RANG, scheduledFor = due, detail = late?.let { "late ${it}s" })
    }

    /**
     * Relabels an unanswered notification as missed (D25). Cosmetic only: scheduling never
     * waits for it, so a late delivery does no harm.
     */
    suspend fun onMissedLabel(id: Long) = mutex.withLock {
        val r = reminders.get(id)?.toDomain() ?: return@withLock
        val state = states.get(id) ?: return@withLock
        val since = state.ringingSince ?: return@withLock
        val timeout = missedTimeout(r)
        if (state.phase != Phase.RINGING || clock() + LABEL_TOLERANCE < since + timeout) return@withLock
        ringer.stop(id)
        val timeoutText = if (r.ringStyle == RingStyle.ALARM) "${r.ringSeconds} s" else "${timeout.toMinutes()} min"
        notifier.showMissed(r, state.nextRingAt, timeoutText, zone())
    }

    /** Notifications count as missed after the settings timeout; alarms after their ring length (D21). */
    private suspend fun missedTimeout(r: Reminder): Duration =
        if (r.ringStyle == RingStyle.ALARM) Duration.ofSeconds(r.ringSeconds.toLong())
        else Duration.ofMinutes(settings.current().missedTimeoutMinutes.toLong())

    /** Dismiss, swipe or snooze. Ignored unless the reminder is ringing. */
    suspend fun onAction(id: Long, action: RingAction) = mutex.withLock {
        val r = reminders.get(id)?.toDomain()
        val state = states.get(id)
        if (r == null || state?.phase != Phase.RINGING) {
            // Button on an old notification.
            ringer.stop(id)
            if (action != RingAction.SWIPE) notifier.cancel(id)
            return@withLock
        }
        ringer.stop(id)
        val now = clock()
        val event = when (action) {
            RingAction.DISMISS, RingAction.SWIPE -> ScheduleEvent.Dismissed(now)
            RingAction.SNOOZE -> ScheduleEvent.Snoozed(now)
        }
        val next = Scheduler.nextRing(r, event, zone(), settings.current().pausedUntil)
        val phase = if (action == RingAction.SNOOZE) Phase.SNOOZED else Phase.WAITING
        states.upsert(ReminderStateEntity(id, phase, next, null))
        schedule(r, next)
        alarms.cancelMissedLabel(id)
        if (action != RingAction.SWIPE) notifier.cancel(id)
        val type = when (action) {
            RingAction.DISMISS -> EventType.DISMISSED
            RingAction.SWIPE -> EventType.SWIPED
            RingAction.SNOOZE -> EventType.SNOOZED
        }
        record(id, type, scheduledFor = next)
    }

    /**
     * Recomputes every reminder. After boot, notifications are gone, so ringing reminders are
     * rescheduled too; otherwise a ringing reminder keeps its "if ignored" ring, re-registered.
     */
    suspend fun rescheduleAll(reason: String, afterBoot: Boolean) = mutex.withLock {
        val now = clock()
        val paused = settings.current().pausedUntil
        for (r in reminders.getAll().map { it.toDomain() }) {
            val state = states.get(r.id)
            if (!afterBoot && state?.phase == Phase.RINGING) {
                schedule(r, state.nextRingAt)
                continue
            }
            val next = Scheduler.nextRing(r, ScheduleEvent.Rescheduled(now, state?.nextRingAt), zone(), paused)
            states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
            schedule(r, next)
        }
        record(null, EventType.RESCHEDULED_ALL, detail = reason)
    }

    /**
     * Pause all (D10). Silences any ring in progress and moves every reminder's next ring to
     * the pause end: restart timers one interval after it, fixed schedules to the next slot.
     */
    suspend fun pauseUntil(until: Instant) = mutex.withLock {
        settings.setPausedUntil(until)
        for (r in reminders.getAll().map { it.toDomain() }) {
            clearRinging(r.id)
            val next = Scheduler.nextRing(r, ScheduleEvent.Resumed(until), zone(), until)
            states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
            schedule(r, next)
        }
        record(null, EventType.PAUSED, scheduledFor = until)
    }

    /** Resume now (Flow D): restart timers count from now; fixed schedules go to the next slot. */
    suspend fun resume() = mutex.withLock {
        settings.setPausedUntil(null)
        val now = clock()
        for (r in reminders.getAll().map { it.toDomain() }) {
            val next = Scheduler.nextRing(r, ScheduleEvent.Resumed(now), zone())
            states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
            schedule(r, next)
        }
        record(null, EventType.RESUMED)
    }

    /**
     * First start after a backup restore (D37). Stored ring times and phases came from the old
     * phone, so every reminder starts fresh from now; an expired pause is dropped. Permissions
     * and confirmed setup steps belong to the old phone too, so setup runs again.
     */
    suspend fun onRestored() = mutex.withLock {
        val now = clock()
        val paused = settings.current().pausedUntil?.takeIf { it > now }
        if (paused == null) settings.setPausedUntil(null)
        settings.resetSetup()
        for (r in reminders.getAll().map { it.toDomain() }) {
            val next = Scheduler.nextRing(r, ScheduleEvent.Enabled(now), zone(), paused)
            states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
            schedule(r, next)
        }
        record(null, EventType.RESTORED)
    }

    /**
     * Called when the app opens. Re-registers stored alarms without changing any times,
     * so it is safe even at the moment a reminder is due.
     */
    suspend fun ensureScheduled() = mutex.withLock {
        val paused = settings.current().pausedUntil
        for (r in reminders.getAll().map { it.toDomain() }) {
            val state = states.get(r.id)
            val stored = state?.nextRingAt
            if (stored != null) {
                alarms.scheduleRing(r.id, stored, r.isAlarm)
            } else if (r.enabled) {
                val next = Scheduler.nextRing(r, ScheduleEvent.Enabled(clock()), zone(), paused)
                states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
                schedule(r, next)
            }
        }
    }

    private suspend fun scheduleFresh(r: Reminder, event: ScheduleEvent) {
        val next = Scheduler.nextRing(r, event, zone(), settings.current().pausedUntil)
        states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
        schedule(r, next)
        record(r.id, EventType.SCHEDULED, scheduledFor = next)
    }

    private suspend fun schedule(r: Reminder, next: Instant?) {
        val id = r.id
        if (next == null) {
            alarms.cancelRing(id)
        } else if (!alarms.scheduleRing(id, next, r.isAlarm)) {
            record(id, EventType.NO_EXACT_ALARM, scheduledFor = next, detail = "scheduled inexactly")
        }
    }

    private fun clearRinging(id: Long) {
        alarms.cancelMissedLabel(id)
        ringer.stop(id)
        notifier.cancel(id)
    }

    private suspend fun record(id: Long?, type: EventType, scheduledFor: Instant? = null, detail: String? = null) =
        log.insert(EventLogEntity(reminderId = id, type = type, at = clock(), scheduledFor = scheduledFor, detail = detail))

    private val Reminder.isAlarm: Boolean get() = ringStyle == RingStyle.ALARM

    private companion object {
        val EARLY_TOLERANCE: Duration = Duration.ofSeconds(60)
        val LABEL_TOLERANCE: Duration = Duration.ofSeconds(2)
    }
}
