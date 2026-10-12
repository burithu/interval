package io.github.burithu.interval.ui

import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.TimingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** "45 min", "1 h", "1 h 30 min" */
fun formatInterval(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

fun formatMinuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

fun formatClock(t: Instant, zone: ZoneId): String = clockFormat.format(t.atZone(zone))

/** "14:18" today, "Fri 10:00" within a week, "12 Oct 10:00" beyond. */
fun formatNext(t: Instant, now: Instant, zone: ZoneId): String {
    val date = t.atZone(zone).toLocalDate()
    val today: LocalDate = now.atZone(zone).toLocalDate()
    return when {
        date == today -> formatClock(t, zone)
        date < today.plusDays(7) -> "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${formatClock(t, zone)}"
        else -> "${DateTimeFormatter.ofPattern("d MMM").format(date)} ${formatClock(t, zone)}"
    }
}

fun formatWindow(start: Int, end: Int): String = when {
    start == end -> "All day"
    end < start -> "${formatMinuteOfDay(start)}–${formatMinuteOfDay(end)} +1"
    else -> "${formatMinuteOfDay(start)}–${formatMinuteOfDay(end)}"
}

fun formatDays(days: Days): String {
    val all = DayOfWeek.entries
    val on = all.filter { it in days }
    fun short(d: DayOfWeek) = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return when {
        on.size == 7 -> "Every day"
        on == all.take(5) -> "Mon–Fri"
        on == all.drop(5) -> "Weekends"
        on.isEmpty() -> "No days"
        else -> on.joinToString(", ") { short(it) }
    }
}

/**
 * "Every 1 h · 09:00–18:00 · Mon–Fri · Restart". When it wraps, it breaks only after a "· ":
 * spaces inside an item and before each dot don't break.
 */
fun reminderMeta(r: Reminder, withTiming: Boolean = true): String = listOfNotNull(
    "Every ${formatInterval(r.intervalMinutes)}",
    formatWindow(r.windowStart, r.windowEnd),
    formatDays(r.days),
    if (!withTiming) null else if (r.timingMode == TimingMode.RESTART) "Restart" else "Fixed",
).joinToString(" · ") { it.replace(' ', ' ').replace("–", "⁠–⁠") }
