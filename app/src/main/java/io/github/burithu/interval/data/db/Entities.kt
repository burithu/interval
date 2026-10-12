package io.github.burithu.interval.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.burithu.interval.data.AlarmMode
import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.ReminderState
import io.github.burithu.interval.data.RingStyle
import io.github.burithu.interval.data.TimingMode
import java.time.Instant

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val label: String,
    val intervalMinutes: Int,
    val windowStart: Int,
    val windowEnd: Int,
    val daysMask: Int,
    val timingMode: TimingMode,
    val ringStyle: RingStyle,
    @ColumnInfo(defaultValue = "FULL") val alarmMode: AlarmMode,
    val ringSeconds: Int,
    val snoozeMinutes: Int,
    val soundUri: String?,
    val vibrate: Boolean,
    @ColumnInfo(defaultValue = "0") val vibrateOnSilent: Boolean,
    val enabled: Boolean,
    val createdAt: Instant,
)

@Entity(
    tableName = "reminder_state",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReminderStateEntity(
    @PrimaryKey val reminderId: Long,
    val phase: Phase,
    val nextRingAt: Instant?,
    val ringingSince: Instant?,
)

enum class EventType { SAVED, DELETED, ENABLED, DISABLED, SCHEDULED, RANG, DISMISSED, SWIPED, SNOOZED, MISSED, RESCHEDULED_ALL, NO_EXACT_ALARM, PAUSED, RESUMED, RESTORED }

/** Debug event log, capped at [EventLogDao.MAX_ROWS] rows (D28). */
@Entity(tableName = "event_log", indices = [Index("at")])
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long?,
    val type: EventType,
    val at: Instant,
    val scheduledFor: Instant?,
    val detail: String?,
)

fun ReminderEntity.toDomain() = Reminder(
    id = id, label = label, intervalMinutes = intervalMinutes, windowStart = windowStart, windowEnd = windowEnd,
    days = Days(daysMask), timingMode = timingMode, ringStyle = ringStyle, alarmMode = alarmMode, ringSeconds = ringSeconds,
    snoozeMinutes = snoozeMinutes, soundUri = soundUri, vibrate = vibrate, vibrateOnSilent = vibrateOnSilent,
    enabled = enabled, createdAt = createdAt,
)

fun Reminder.toEntity() = ReminderEntity(
    id = id, label = label, intervalMinutes = intervalMinutes, windowStart = windowStart, windowEnd = windowEnd,
    daysMask = days.mask, timingMode = timingMode, ringStyle = ringStyle, alarmMode = alarmMode, ringSeconds = ringSeconds,
    snoozeMinutes = snoozeMinutes, soundUri = soundUri, vibrate = vibrate, vibrateOnSilent = vibrateOnSilent,
    enabled = enabled, createdAt = createdAt,
)

fun ReminderStateEntity.toDomain() = ReminderState(reminderId, phase, nextRingAt, ringingSince)
