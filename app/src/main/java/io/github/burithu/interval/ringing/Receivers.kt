package io.github.burithu.interval.ringing

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.burithu.interval.IntervalApp
import io.github.burithu.interval.scheduling.RingAction
import io.github.burithu.interval.ui.alarm.AlarmActivity
import kotlinx.coroutines.launch

/** Runs suspend work from a receiver without letting the process die mid-way. */
private fun BroadcastReceiver.launchAsync(context: Context, block: suspend (IntervalApp) -> Unit) {
    val app = context.applicationContext as IntervalApp
    val pending = goAsync()
    app.container.scope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

/** System alarms: a reminder is due, or its notification should be relabeled as missed. */
class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: return
        if (intent.action == ACTION_RING && intent.getBooleanExtra(EXTRA_ALARM, false)) {
            // HyperOS SystemUI ignores full-screen intents from third-party apps. An alarm-clock
            // broadcast may start an activity directly, so open the alarm screen from here.
            AlarmActivity.open(context, id)
        }
        launchAsync(context) { app ->
            when (intent.action) {
                ACTION_RING -> app.container.engine.onRing(id)
                ACTION_MISSED_LABEL -> app.container.engine.onMissedLabel(id)
            }
        }
    }

    companion object {
        const val ACTION_RING = "io.github.burithu.interval.RING"
        const val ACTION_MISSED_LABEL = "io.github.burithu.interval.MISSED_LABEL"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_ALARM = "alarm_style"
    }
}

/** Notification buttons and swipe-away. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: return
        val action = when (intent.action) {
            ACTION_DISMISS -> RingAction.DISMISS
            ACTION_SNOOZE -> RingAction.SNOOZE
            ACTION_SWIPE -> RingAction.SWIPE
            else -> return
        }
        launchAsync(context) { app -> app.container.engine.onAction(id, action) }
    }

    companion object {
        const val ACTION_DISMISS = "io.github.burithu.interval.DISMISS"
        const val ACTION_SNOOZE = "io.github.burithu.interval.SNOOZE"
        const val ACTION_SWIPE = "io.github.burithu.interval.SWIPE"
        const val EXTRA_ID = "reminder_id"
    }
}

/** Recomputes every reminder after reboot, app update, clock or time-zone change, or permission change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app updated"
            Intent.ACTION_TIME_CHANGED -> "clock changed"
            Intent.ACTION_TIMEZONE_CHANGED -> "time zone changed"
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> "exact alarm permission changed"
            else -> return
        }
        launchAsync(context) { app ->
            app.container.engine.rescheduleAll(reason, afterBoot = intent.action == Intent.ACTION_BOOT_COMPLETED)
        }
    }
}
