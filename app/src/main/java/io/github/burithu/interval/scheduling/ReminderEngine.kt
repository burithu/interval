package io.github.burithu.interval.scheduling

import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.SettingsStore
import io.github.burithu.interval.data.db.EventLogEntity
import io.github.burithu.interval.data.db.EventType
import io.github.burithu.interval.data.db.IntervalDatabase
import io.github.burithu.interval.data.db.ReminderStateEntity
import io.github.burithu.interval.data.db.toDomain
import io.github.burithu.interval.data.db.toEntity
import io.github.burithu.interval.ringing.AlarmScheduler
import io.github.burithu.interval.ringing.Notifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class RingAction { DISMISS, SWIPE, SNOOZE, MISSED }

/**
 * Applies every state change: edits, rings, notification actions, reschedules.
 * Each change runs under one lock so a ring cannot interleave with an edit.
 */
class ReminderEngine(
    db: IntervalDatabase,
    private val settings: SettingsStore,
    private val alarms: AlarmScheduler,
    private val notifier: Notifier,
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
        notifier.ensureChannel(saved)
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
        notifier.deleteChannels(id)
        reminders.delete(id) // state row goes with it (cascade)
        record(id, EventType.DELETED)
    }

    /** A ring alarm fired. */
    suspend fun onRing(id: Long) = mutex.withLock {
        val r = reminders.get(id)?.toDomain() ?: return@withLock
        if (!r.enabled) return@withLock
        val state = states.get(id)
        if (state?.phase == Phase.RINGING) return@withLock
        val now = clock()
        val due = state?.nextRingAt
        if (due != null && now < due - EARLY_TOLERANCE) {
            // Stale alarm (state changed after it was set). Put the real one back.
            alarms.scheduleRing(id, due)
            return@withLock
        }
        states.upsert(ReminderStateEntity(id, Phase.RINGING, due, now))
        notifier.showRing(r)
        alarms.scheduleMissedCheck(id, now + Duration.ofMinutes(settings.current().missedTimeoutMinutes.toLong()))
        val late = due?.let { Duration.between(it, now).seconds }
        record(id, EventType.RANG, scheduledFor = due, detail = late?.let { "late ${it}s" })
    }

    /** Dismiss, swipe, snooze, or missed timeout. Ignored unless the reminder is ringing. */
    suspend fun onAction(id: Long, action: RingAction) = mutex.withLock {
        val r = reminders.get(id)?.toDomain()
        val state = states.get(id)
        if (r == null || state?.phase != Phase.RINGING) {
            // Button on an old notification, such as one left after a missed ring.
            if (action == RingAction.DISMISS || action == RingAction.SNOOZE) notifier.cancel(id)
            return@withLock
        }
        val now = clock()
        val event = when (action) {
            RingAction.DISMISS, RingAction.SWIPE -> ScheduleEvent.Dismissed(now)
            RingAction.SNOOZE -> ScheduleEvent.Snoozed(now)
            RingAction.MISSED -> ScheduleEvent.Missed(now)
        }
        val s = settings.current()
        val next = Scheduler.nextRing(r, event, zone(), s.pausedUntil)
        val phase = if (action == RingAction.SNOOZE) Phase.SNOOZED else Phase.WAITING
        states.upsert(ReminderStateEntity(id, phase, next, null))
        schedule(id, next)
        when (action) {
            RingAction.DISMISS, RingAction.SNOOZE -> {
                alarms.cancelMissedCheck(id)
                notifier.cancel(id)
            }
            RingAction.SWIPE -> alarms.cancelMissedCheck(id)
            RingAction.MISSED -> notifier.showMissed(r, next, s.missedTimeoutMinutes, zone())
        }
        val type = when (action) {
            RingAction.DISMISS -> EventType.DISMISSED
            RingAction.SWIPE -> EventType.SWIPED
            RingAction.SNOOZE -> EventType.SNOOZED
            RingAction.MISSED -> EventType.MISSED
        }
        record(id, type, scheduledFor = next)
    }

    /**
     * Recomputes every reminder. After boot, notifications are gone, so ringing reminders are
     * rescheduled too; otherwise a ringing reminder is left for its missed check to finish.
     */
    suspend fun rescheduleAll(reason: String, afterBoot: Boolean) = mutex.withLock {
        val now = clock()
        val paused = settings.current().pausedUntil
        for (r in reminders.getAll().map { it.toDomain() }) {
            val state = states.get(r.id)
            if (!afterBoot && state?.phase == Phase.RINGING) continue
            val next = Scheduler.nextRing(r, ScheduleEvent.Rescheduled(now, state?.nextRingAt), zone(), paused)
            states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
            schedule(r.id, next)
        }
        record(null, EventType.RESCHEDULED_ALL, detail = reason)
    }

    /**
     * Called when the app opens. Re-registers stored alarms without changing any times,
     * so it is safe even at the moment a reminder is due.
     */
    suspend fun ensureScheduled() = mutex.withLock {
        val paused = settings.current().pausedUntil
        for (r in reminders.getAll().map { it.toDomain() }) {
            val state = states.get(r.id)
            if (state?.phase == Phase.RINGING) continue
            val stored = state?.nextRingAt
            if (stored != null) {
                alarms.scheduleRing(r.id, stored)
            } else if (r.enabled) {
                val next = Scheduler.nextRing(r, ScheduleEvent.Enabled(clock()), zone(), paused)
                states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
                schedule(r.id, next)
            }
        }
    }

    private suspend fun scheduleFresh(r: Reminder, event: ScheduleEvent) {
        val next = Scheduler.nextRing(r, event, zone(), settings.current().pausedUntil)
        states.upsert(ReminderStateEntity(r.id, Phase.WAITING, next, null))
        schedule(r.id, next)
        record(r.id, EventType.SCHEDULED, scheduledFor = next)
    }

    private suspend fun schedule(id: Long, next: Instant?) {
        if (next == null) {
            alarms.cancelRing(id)
        } else if (!alarms.scheduleRing(id, next)) {
            record(id, EventType.NO_EXACT_ALARM, scheduledFor = next, detail = "scheduled inexactly")
        }
    }

    private fun clearRinging(id: Long) {
        alarms.cancelMissedCheck(id)
        notifier.cancel(id)
    }

    private suspend fun record(id: Long?, type: EventType, scheduledFor: Instant? = null, detail: String? = null) =
        log.insert(EventLogEntity(reminderId = id, type = type, at = clock(), scheduledFor = scheduledFor, detail = detail))

    private companion object {
        val EARLY_TOLERANCE: Duration = Duration.ofSeconds(60)
    }
}
