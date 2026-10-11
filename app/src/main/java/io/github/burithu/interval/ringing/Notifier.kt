package io.github.burithu.interval.ringing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import io.github.burithu.interval.MainActivity
import io.github.burithu.interval.R
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.TimingMode
import io.github.burithu.interval.ui.formatClock
import io.github.burithu.interval.ui.formatInterval
import java.time.Instant
import java.time.ZoneId

/**
 * Posts ring notifications. Each reminder has its own channel because Android ties the
 * sound to the channel (D13). A sound or vibration change gets a new channel id.
 */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    private fun channelPrefix(id: Long) = "reminder_${id}_"
    private fun channelId(r: Reminder): String =
        channelPrefix(r.id) + (r.soundUri to r.vibrate).hashCode().toUInt().toString(16)

    fun ensureChannel(r: Reminder) {
        val id = channelId(r)
        nm.notificationChannels
            .filter { it.id.startsWith(channelPrefix(r.id)) && it.id != id }
            .forEach { nm.deleteNotificationChannel(it.id) }
        val channel = NotificationChannel(id, r.label, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings for \"${r.label}\""
            setSound(
                r.soundUri?.toUri() ?: Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(r.vibrate)
            if (r.vibrate) vibrationPattern = longArrayOf(0, 400, 200, 400)
        }
        nm.createNotificationChannel(channel)
    }

    fun deleteChannels(reminderId: Long) {
        nm.notificationChannels
            .filter { it.id.startsWith(channelPrefix(reminderId)) }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    fun showRing(r: Reminder) {
        ensureChannel(r)
        val text = when (r.timingMode) {
            TimingMode.RESTART -> "Every ${formatInterval(r.intervalMinutes)} · dismiss to restart the timer"
            TimingMode.FIXED -> "Every ${formatInterval(r.intervalMinutes)}"
        }
        val n = base(r)
            .setContentText(text)
            .setDeleteIntent(action(r.id, ActionReceiver.ACTION_SWIPE, 3))
            .addAction(0, "Dismiss", action(r.id, ActionReceiver.ACTION_DISMISS, 1))
            .addAction(0, "Snooze ${formatInterval(r.snoozeMinutes)}", action(r.id, ActionReceiver.ACTION_SNOOZE, 2))
            .build()
        post(r.id, n)
    }

    /** Missed notifications stay until the next ring replaces them (D25), without sounding again. */
    fun showMissed(r: Reminder, next: Instant?, timeoutMinutes: Int, zone: ZoneId) {
        val nextText = next?.let { "Next at ${formatClock(it, zone)}." } ?: "No more rings scheduled."
        val n = base(r)
            .setContentTitle("Missed · ${r.label}")
            .setContentText("No response in $timeoutMinutes min. $nextText")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        post(r.id, n)
    }

    fun cancel(reminderId: Long) = nm.cancel(reminderId.toInt())

    private fun base(r: Reminder) = NotificationCompat.Builder(context, channelId(r))
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(r.label)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setShowWhen(true)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    private fun post(id: Long, n: android.app.Notification) {
        try {
            nm.notify(id.toInt(), n)
        } catch (_: SecurityException) {
            // Notification permission revoked. The ring is still logged; the Home banner (M3) reports it.
        }
    }

    private fun action(reminderId: Long, action: String, req: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .setData("interval://reminder/$reminderId".toUri())
            .putExtra(ActionReceiver.EXTRA_ID, reminderId)
        return PendingIntent.getBroadcast(
            context, req, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
