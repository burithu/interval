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
 * Registers system alarms. Every ring, including the "if ignored" follow-up, uses
 * setAlarmClock() (D30): Doze-exempt and not throttled. Without the exact-alarm permission
 * rings fall back to inexact alarms.
 *
 * The missed label is cosmetic and inexact on purpose. On HyperOS even
 * setExactAndAllowWhileIdle() was observed 13–23 minutes late.
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

    fun scheduleMissedLabel(reminderId: Long, at: Instant) {
        val op = pending(reminderId, RingReceiver.ACTION_MISSED_LABEL, REQ_MISSED)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), op)
    }

    fun cancelRing(reminderId: Long) = am.cancel(pending(reminderId, RingReceiver.ACTION_RING, REQ_RING))
    fun cancelMissedLabel(reminderId: Long) = am.cancel(pending(reminderId, RingReceiver.ACTION_MISSED_LABEL, REQ_MISSED))

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
