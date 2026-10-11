package io.github.burithu.interval.ringing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import io.github.burithu.interval.MainActivity
import java.time.Instant

/**
 * Registers system alarms. Rings use setAlarmClock() (D30): Doze-exempt and not throttled.
 * Missed checks use a plain exact alarm, since a few minutes of delay there is harmless.
 * Without the exact-alarm permission both fall back to inexact alarms.
 */
class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /** Returns false if the ring had to be scheduled inexactly. */
    fun scheduleRing(reminderId: Long, at: Instant): Boolean {
        val op = pending(reminderId, RingReceiver.ACTION_RING, REQ_RING)
        return if (canScheduleExact()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), op)
            true
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), op)
            false
        }
    }

    fun scheduleMissedCheck(reminderId: Long, at: Instant) {
        val op = pending(reminderId, RingReceiver.ACTION_MISSED_CHECK, REQ_MISSED)
        if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), op)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), op)
    }

    fun cancelRing(reminderId: Long) = am.cancel(pending(reminderId, RingReceiver.ACTION_RING, REQ_RING))
    fun cancelMissedCheck(reminderId: Long) = am.cancel(pending(reminderId, RingReceiver.ACTION_MISSED_CHECK, REQ_MISSED))

    private fun pending(reminderId: Long, action: String, req: Int): PendingIntent {
        val intent = Intent(context, RingReceiver::class.java)
            .setAction(action)
            .setData("interval://reminder/$reminderId".toUri())
            .putExtra(RingReceiver.EXTRA_ID, reminderId)
        return PendingIntent.getBroadcast(
            context, req, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQ_RING = 1
        const val REQ_MISSED = 2
    }
}
