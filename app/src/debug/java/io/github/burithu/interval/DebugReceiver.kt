package io.github.burithu.interval

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import io.github.burithu.interval.data.AlarmMode
import io.github.burithu.interval.data.Days
import io.github.burithu.interval.data.Phase
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import io.github.burithu.interval.data.db.ReminderStateEntity
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Debug builds only: drives the ring-mode test matrix from adb.
 *
 * - `ringer --ei mode 0|1|2`: sets the ringer mode (silent, vibrate, normal). Silent needs
 *   `cmd notification allow_dnd <pkg>`.
 * - `ring --es style NOTIFICATION|ALARM --es alarmMode SCREEN|VIBRATE|FULL --ez vibrate b --ez vos b`:
 *   replaces every reminder with one test reminder and books its ring 5 s from now through the
 *   real setAlarmClock path.
 * - `clear`: deletes every reminder, which stops any ring.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as IntervalApp
        val c = app.container
        val pending = goAsync()
        c.scope.launch {
            try {
                when (intent.action) {
                    "ringer" -> {
                        val audio = context.getSystemService(AudioManager::class.java)
                        audio.ringerMode = intent.getIntExtra("mode", AudioManager.RINGER_MODE_NORMAL)
                        Log.i(TAG, "ringer set to ${audio.ringerMode}")
                    }
                    "clear" -> clear(app)
                    "ring" -> {
                        clear(app)
                        val r = Reminder(
                            label = "Test",
                            intervalMinutes = 1440,
                            windowStart = 0,
                            windowEnd = 0,
                            days = Days.EVERY_DAY,
                            ringStyle = RingStyle.valueOf(intent.getStringExtra("style") ?: "NOTIFICATION"),
                            alarmMode = AlarmMode.valueOf(intent.getStringExtra("alarmMode") ?: "FULL"),
                            ringSeconds = 20,
                            vibrate = intent.getBooleanExtra("vibrate", true),
                            vibrateOnSilent = intent.getBooleanExtra("vos", false),
                            createdAt = Instant.now(),
                        )
                        val id = c.engine.save(r)
                        val at = Instant.now().plusSeconds(5)
                        c.db.stateDao().upsert(ReminderStateEntity(id, Phase.WAITING, at, null))
                        c.alarms.scheduleRing(id, at, r.ringStyle == RingStyle.ALARM)
                        Log.i(TAG, "ring booked id=$id at=$at $r")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "debug action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun clear(app: IntervalApp) {
        app.container.db.reminderDao().getAll().forEach { app.container.engine.delete(it.id) }
    }

    private companion object {
        const val TAG = "IntervalDebug"
    }
}
