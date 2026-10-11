package io.github.burithu.interval.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import io.github.burithu.interval.MainActivity
import io.github.burithu.interval.R
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import io.github.burithu.interval.data.TimingMode
import io.github.burithu.interval.ui.alarm.AlarmActivity
import io.github.burithu.interval.ui.formatClock
import io.github.burithu.interval.ui.formatInterval
import java.time.Instant
import java.time.ZoneId

/**
 * Builds and posts ring notifications. Channels are silent: [RingService] plays sound and
 * vibration itself, so HyperOS's per-app notification sound switch cannot mute a ring.
 */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        // Per-reminder channels from before 0.1.0-M4 are no longer used.
        nm.notificationChannels.filter { it.id.startsWith("reminder_") }.forEach { nm.deleteNotificationChannel(it.id) }
        listOf(
            NotificationChannel(CH_RING, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notification-style rings. Interval plays the sound itself."
            },
            NotificationChannel(CH_ALARM, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm-style rings, shown full screen."
            },
            NotificationChannel(CH_MISSED, "Missed reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Rings that got no response."
            },
        ).forEach {
            it.setSound(null, null)
            it.enableVibration(false)
            nm.createNotificationChannel(it)
        }
    }

    fun buildRing(r: Reminder): Notification {
        val alarm = r.ringStyle == RingStyle.ALARM
        val text = when (r.timingMode) {
            TimingMode.RESTART -> "Every ${formatInterval(r.intervalMinutes)} · dismiss to restart the timer"
            TimingMode.FIXED -> "Every ${formatInterval(r.intervalMinutes)}"
        }
        val b = NotificationCompat.Builder(context, if (alarm) CH_ALARM else CH_RING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.label)
            .setContentText(text)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(if (alarm) alarmScreen(r.id) else openApp())
            .setDeleteIntent(action(r.id, ActionReceiver.ACTION_SWIPE, 3))
            .addAction(0, "Dismiss", action(r.id, ActionReceiver.ACTION_DISMISS, 1))
            .addAction(0, "Snooze ${formatInterval(r.snoozeMinutes)}", action(r.id, ActionReceiver.ACTION_SNOOZE, 2))
        if (alarm) b.setFullScreenIntent(alarmScreen(r.id), true).setOngoing(true)
        return b.build()
    }

    /** Missed notifications stay until the next ring replaces them (D25). */
    fun showMissed(r: Reminder, next: Instant?, timeoutText: String, zone: ZoneId) {
        val nextText = next?.let { "Next at ${formatClock(it, zone)}." } ?: "No more rings scheduled."
        val n = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Missed · ${r.label}")
            .setContentText("No response in $timeoutText. $nextText")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .build()
        post(r.id, n)
    }

    fun post(id: Long, n: Notification) {
        try {
            nm.notify(id.toInt(), n)
        } catch (_: SecurityException) {
            // Notification permission revoked. The ring is still logged; Home shows the warning.
        }
    }

    fun cancel(reminderId: Long) = nm.cancel(reminderId.toInt())

    private fun openApp() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmScreen(reminderId: Long) = PendingIntent.getActivity(
        context,
        reminderId.toInt(),
        AlarmActivity.intent(context, reminderId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(reminderId: Long, action: String, req: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .setData("interval://reminder/$reminderId".toUri())
            .putExtra(ActionReceiver.EXTRA_ID, reminderId)
        return PendingIntent.getBroadcast(
            context, req, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CH_RING = "ring"
        const val CH_ALARM = "alarm"
        const val CH_MISSED = "missed"
    }
}
