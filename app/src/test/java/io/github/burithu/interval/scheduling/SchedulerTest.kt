package io.github.burithu.interval.scheduling

import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.TimingMode
import io.github.burithu.interval.scheduling.ScheduleEvent.Dismissed
import io.github.burithu.interval.scheduling.ScheduleEvent.Enabled
import io.github.burithu.interval.scheduling.ScheduleEvent.Missed
import io.github.burithu.interval.scheduling.ScheduleEvent.Rescheduled
import io.github.burithu.interval.scheduling.ScheduleEvent.Resumed
import io.github.burithu.interval.scheduling.ScheduleEvent.Snoozed
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Rules from docs/SPEC.md §3. Dates: 2026-10-08 is a Thursday.
 * Europe/London is used so daylight-saving changes can be tested.
 */
class SchedulerTest {

    private val london: ZoneId = ZoneId.of("Europe/London")
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(london).toInstant()
    private fun hm(h: Int, m: Int = 0) = h * 60 + m
    private fun next(r: Reminder, e: ScheduleEvent, paused: Instant? = null) = Scheduler.nextRing(r, e, london, paused)

    // 09:00–18:00 Mon–Fri, every 1 h
    private val standUp = Reminder(label = "Stand up", intervalMinutes = 60, windowStart = hm(9), windowEnd = hm(18))
    private val standUpFixed = standUp.copy(timingMode = TimingMode.FIXED)

    // 08:00–22:00 every day, every 45 min, fixed
    private val water = Reminder(
        label = "Water", intervalMinutes = 45, windowStart = hm(8), windowEnd = hm(22),
        days = Days.EVERY_DAY, timingMode = TimingMode.FIXED,
    )

    // 20:00–02:00 (+1 day) Mon–Fri, every 2 h
    private val nightFixed = Reminder(
        label = "Stretch", intervalMinutes = 120, windowStart = hm(20), windowEnd = hm(2),
        timingMode = TimingMode.FIXED,
    )

    // ---------- Restart mode ----------

    @Test fun `restart - enabling inside window rings one interval from now`() =
        assertEquals(at("2026-10-08T14:18"), next(standUp, Enabled(at("2026-10-08T13:18"))))

    @Test fun `restart - enabling before window rings at window start plus interval`() =
        assertEquals(at("2026-10-08T10:00"), next(standUp, Enabled(at("2026-10-08T07:00"))))

    @Test fun `restart - dismiss restarts the timer from dismiss time`() =
        assertEquals(at("2026-10-08T15:07"), next(standUp, Dismissed(at("2026-10-08T14:07"))))

    @Test fun `restart - missed restarts the timer like a dismiss`() =
        assertEquals(at("2026-10-08T15:23"), next(standUp, Missed(at("2026-10-08T14:23"))))

    @Test fun `restart - ring past window end moves to next day first ring`() =
        assertEquals(at("2026-10-09T10:00"), next(standUp, Dismissed(at("2026-10-08T17:30"))))

    @Test fun `restart - ring exactly at window end is allowed`() =
        assertEquals(at("2026-10-08T18:00"), next(standUp, Dismissed(at("2026-10-08T17:00"))))

    @Test fun `restart - missed after window end goes to next window`() =
        assertEquals(at("2026-10-09T10:00"), next(standUp, Missed(at("2026-10-08T18:05"))))

    @Test fun `restart - friday evening skips the weekend`() =
        assertEquals(at("2026-10-12T10:00"), next(standUp, Dismissed(at("2026-10-09T17:30"))))

    @Test fun `restart - snooze rings after snooze length`() =
        assertEquals(at("2026-10-08T14:10"), next(standUp, Snoozed(at("2026-10-08T14:00"))))

    @Test fun `restart - dismissing the snoozed ring restarts from that dismiss`() =
        assertEquals(at("2026-10-08T15:11"), next(standUp, Dismissed(at("2026-10-08T14:11"))))

    @Test fun `restart - interval longer than window rings once at window start`() {
        val r = standUp.copy(windowStart = hm(9), windowEnd = hm(10), intervalMinutes = 120)
        assertEquals(at("2026-10-08T09:00"), next(r, Enabled(at("2026-10-08T07:00"))))
        assertEquals(at("2026-10-09T09:00"), next(r, Dismissed(at("2026-10-08T09:01"))))
    }

    // ---------- Fixed mode ----------

    @Test fun `fixed - enabling picks the next grid slot`() =
        // 08:00 + 7 × 45 = 13:15, next slot 14:00
        assertEquals(at("2026-10-08T14:00"), next(water, Enabled(at("2026-10-08T13:18"))))

    @Test fun `fixed - first ring is at window start`() =
        assertEquals(at("2026-10-08T09:00"), next(standUpFixed, Enabled(at("2026-10-08T07:00"))))

    @Test fun `fixed - late dismiss does not shift the grid`() =
        assertEquals(at("2026-10-08T14:45"), next(water, Dismissed(at("2026-10-08T14:07"))))

    @Test fun `fixed - missed does not shift the grid`() =
        assertEquals(at("2026-10-08T14:45"), next(water, Missed(at("2026-10-08T14:05"))))

    @Test fun `fixed - last slot of the day then next day window start`() {
        // 08:00 + 18 × 45 = 21:30 is the last slot before 22:00
        assertEquals(at("2026-10-08T21:30"), next(water, Dismissed(at("2026-10-08T21:00"))))
        assertEquals(at("2026-10-09T08:00"), next(water, Dismissed(at("2026-10-08T21:30"))))
    }

    @Test fun `fixed - slot exactly at window end rings`() =
        assertEquals(at("2026-10-08T18:00"), next(standUpFixed, Dismissed(at("2026-10-08T17:00"))))

    @Test fun `fixed - interval longer than window rings once at window start`() {
        val r = standUpFixed.copy(windowStart = hm(9), windowEnd = hm(10), intervalMinutes = 120)
        assertEquals(at("2026-10-09T09:00"), next(r, Dismissed(at("2026-10-08T09:00"))))
    }

    @Test fun `fixed - snooze before next slot is kept`() =
        assertEquals(at("2026-10-08T14:11"), next(water, Snoozed(at("2026-10-08T14:01"))))

    @Test fun `fixed - snooze is dropped when the next slot comes first`() =
        assertEquals(at("2026-10-08T14:45"), next(water, Snoozed(at("2026-10-08T14:40"))))

    // ---------- Overnight windows (D24) ----------

    @Test fun `overnight - fixed grid continues past midnight`() =
        assertEquals(at("2026-10-09T00:00"), next(nightFixed, Dismissed(at("2026-10-08T23:00"))))

    @Test fun `overnight - friday window covers saturday early hours`() =
        assertEquals(at("2026-10-10T02:00"), next(nightFixed, Dismissed(at("2026-10-10T01:00"))))

    @Test fun `overnight - after saturday 02 00 next ring is monday 20 00`() =
        assertEquals(at("2026-10-12T20:00"), next(nightFixed, Dismissed(at("2026-10-10T02:00"))))

    @Test fun `overnight - restart past window end skips to next start day`() {
        val r = nightFixed.copy(timingMode = TimingMode.RESTART, intervalMinutes = 60)
        assertEquals(at("2026-10-12T21:00"), next(r, Dismissed(at("2026-10-10T01:30"))))
    }

    @Test fun `overnight - only start days count`() {
        // Monday only: the Sunday-night window does not exist, so Monday 01:00 is outside.
        val r = nightFixed.copy(days = Days.of(MONDAY))
        assertEquals(at("2026-10-12T20:00"), next(r, Enabled(at("2026-10-12T01:00"))))
    }

    // ---------- 24-hour windows (D27) ----------

    private val allDay = Reminder(
        label = "Water", intervalMinutes = 60, windowStart = hm(9), windowEnd = hm(9), days = Days.EVERY_DAY,
    )

    @Test fun `24h - restart runs straight through the night`() =
        assertEquals(at("2026-10-09T04:00"), next(allDay, Dismissed(at("2026-10-09T03:00"))))

    @Test fun `24h - restart runs straight through the day boundary`() =
        assertEquals(at("2026-10-09T09:30"), next(allDay, Dismissed(at("2026-10-09T08:30"))))

    @Test fun `24h - fixed grid restarts at window start each day`() {
        val r = allDay.copy(timingMode = TimingMode.FIXED, intervalMinutes = 7 * 60)
        // slots 09:00, 16:00, 23:00, 06:00(+1); then the next day's grid starts at 09:00
        assertEquals(at("2026-10-09T06:00"), next(r, Dismissed(at("2026-10-08T23:00"))))
        assertEquals(at("2026-10-09T09:00"), next(r, Dismissed(at("2026-10-09T06:00"))))
    }

    // ---------- Off, no days ----------

    @Test fun `disabled reminder never rings`() =
        assertNull(next(standUp.copy(enabled = false), Enabled(at("2026-10-08T13:18"))))

    @Test fun `reminder with no days never rings`() =
        assertNull(next(standUp.copy(days = Days(0)), Enabled(at("2026-10-08T13:18"))))

    @Test fun `single day reminder finds next week`() =
        assertEquals(at("2026-10-16T10:00"), next(standUp.copy(days = Days.of(FRIDAY)), Dismissed(at("2026-10-09T17:30"))))

    // ---------- Pause (D10) ----------

    @Test fun `pause - restart resumes one interval after pause end`() =
        assertEquals(at("2026-10-08T16:00"), next(standUp, Dismissed(at("2026-10-08T13:18")), paused = at("2026-10-08T15:00")))

    @Test fun `pause - fixed resumes at next slot including one exactly at pause end`() =
        assertEquals(at("2026-10-08T15:00"), next(standUpFixed, Dismissed(at("2026-10-08T13:00")), paused = at("2026-10-08T15:00")))

    @Test fun `pause - ring after pause end is unaffected`() =
        assertEquals(at("2026-10-08T14:18"), next(standUp, Dismissed(at("2026-10-08T13:18")), paused = at("2026-10-08T14:00")))

    @Test fun `pause - until tomorrow midnight resumes at next window first ring`() {
        val midnight = at("2026-10-09T00:00")
        assertEquals(at("2026-10-09T10:00"), next(standUp, Dismissed(at("2026-10-08T15:00")), paused = midnight))
        assertEquals(at("2026-10-09T09:00"), next(standUpFixed, Dismissed(at("2026-10-08T15:00")), paused = midnight))
    }

    @Test fun `resumed event - restart`() =
        assertEquals(at("2026-10-08T16:00"), next(standUp, Resumed(at("2026-10-08T15:00"))))

    // ---------- Reboot, time change, restore ----------

    @Test fun `reschedule - restart keeps a future stored ring`() =
        assertEquals(at("2026-10-08T14:18"), next(standUp, Rescheduled(at("2026-10-08T13:50"), storedNext = at("2026-10-08T14:18"))))

    @Test fun `reschedule - restart ring missed while off goes to now plus interval`() =
        assertEquals(at("2026-10-08T15:30"), next(standUp, Rescheduled(at("2026-10-08T14:30"), storedNext = at("2026-10-08T14:18"))))

    @Test fun `reschedule - restart with nothing stored schedules fresh`() =
        assertEquals(at("2026-10-08T15:30"), next(standUp, Rescheduled(at("2026-10-08T14:30"), storedNext = null)))

    @Test fun `reschedule - fixed recomputes the grid in the new time zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = LocalDateTime.parse("2026-10-08T13:18").atZone(tokyo).toInstant()
        val expected = LocalDateTime.parse("2026-10-08T14:00").atZone(tokyo).toInstant()
        assertEquals(expected, Scheduler.nextRing(standUpFixed, Rescheduled(now, storedNext = null), tokyo))
    }

    // ---------- Daylight saving (Europe/London) ----------

    @Test fun `dst spring forward - restart interval is real elapsed time`() {
        // 2026-03-29 01:00 GMT jumps to 02:00 BST
        val r = allDay.copy()
        val dismiss = at("2026-03-29T00:30")
        val ring = next(r, Dismissed(dismiss))!!
        assertEquals(Duration.ofHours(1), Duration.between(dismiss, ring))
        assertEquals(LocalDateTime.parse("2026-03-29T02:30"), LocalDateTime.ofInstant(ring, london))
    }

    @Test fun `dst spring forward - window start inside the gap shifts forward`() {
        val r = Reminder(
            label = "x", intervalMinutes = 60, windowStart = hm(1, 30), windowEnd = hm(5),
            days = Days.EVERY_DAY, timingMode = TimingMode.FIXED,
        )
        val ring = next(r, Enabled(at("2026-03-29T00:00")))!!
        assertEquals(LocalDateTime.parse("2026-03-29T02:30"), LocalDateTime.ofInstant(ring, london))
    }

    @Test fun `dst fall back - restart interval is real elapsed time`() {
        // 2026-10-25 02:00 BST falls back to 01:00 GMT; 01:30 happens twice
        val dismiss = at("2026-10-25T01:30") // first 01:30 (BST)
        val ring = next(allDay, Dismissed(dismiss))!!
        assertEquals(Duration.ofHours(1), Duration.between(dismiss, ring))
    }

    @Test fun `dst fall back - fixed grid keeps real intervals across the change`() {
        val r = allDay.copy(timingMode = TimingMode.FIXED, windowStart = hm(0), windowEnd = hm(0), intervalMinutes = 60)
        // Slots are every 60 real minutes from 00:00 BST, so the repeated hour gets its own ring.
        val first = next(r, Dismissed(at("2026-10-25T01:00")))!!
        val second = next(r, Dismissed(first))!!
        assertEquals(Duration.ofHours(1), Duration.between(first, second))
    }
}
