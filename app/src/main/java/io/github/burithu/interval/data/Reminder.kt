package io.github.burithu.interval.data

import java.time.DayOfWeek
import java.time.Instant

enum class TimingMode {
    /** Next ring = dismiss (or missed) time + interval. */
    RESTART,

    /** Rings on a grid anchored at window start, regardless of dismiss time. */
    FIXED,
}

enum class RingStyle { NOTIFICATION, ALARM }

/**
 * What an alarm-style ring does besides showing the alarm screen (D33). Silent mode reduces any
 * of these to the screen only; vibrate mode drops the sound.
 */
enum class AlarmMode { SCREEN, VIBRATE, FULL }

enum class Phase { WAITING, RINGING, SNOOZED, DONE_TODAY }

/** Day-of-week set stored as a bitmask. Bit 0 = Monday … bit 6 = Sunday. */
@JvmInline
value class Days(val mask: Int) {
    operator fun contains(day: DayOfWeek): Boolean = mask and bit(day) != 0
    val isEmpty: Boolean get() = mask and ALL_MASK == 0

    companion object {
        private const val ALL_MASK = 0b111_1111
        private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)

        val WEEKDAYS = Days(0b001_1111)
        val EVERY_DAY = Days(ALL_MASK)
        fun of(vararg days: DayOfWeek) = Days(days.fold(0) { acc, d -> acc or bit(d) })
    }
}

/**
 * What the user set for one reminder (SPEC §5).
 *
 * Windows are local clock times in minutes of day. `windowEnd < windowStart` means the
 * window ends the next day; `windowEnd == windowStart` means a 24-hour window.
 * [days] are the days on which the window starts.
 */
data class Reminder(
    val id: Long = 0,
    val label: String,
    val intervalMinutes: Int = 60,
    val windowStart: Int = 9 * 60,
    val windowEnd: Int = 18 * 60,
    val days: Days = Days.WEEKDAYS,
    val timingMode: TimingMode = TimingMode.RESTART,
    val ringStyle: RingStyle = RingStyle.NOTIFICATION,
    /** Only used when [ringStyle] is ALARM. */
    val alarmMode: AlarmMode = AlarmMode.FULL,
    val ringSeconds: Int = 60,
    val snoozeMinutes: Int = 10,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.EPOCH,
) {
    init {
        require(intervalMinutes in 1..1440) { "intervalMinutes out of range: $intervalMinutes" }
        require(windowStart in 0..1439 && windowEnd in 0..1439) { "window out of range" }
        require(snoozeMinutes in 1..60) { "snoozeMinutes out of range: $snoozeMinutes" }
        require(ringSeconds in 15..300) { "ringSeconds out of range: $ringSeconds" }
    }
}

/** What is happening with a reminder right now (SPEC §5). */
data class ReminderState(
    val reminderId: Long,
    val phase: Phase = Phase.WAITING,
    val nextRingAt: Instant? = null,
    val ringingSince: Instant? = null,
)
