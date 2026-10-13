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
 * - `seed [--ez r2 false] [--ei r1Minutes n]`: replaces every reminder with the reliability
 *   checklist's R1 and R2 (docs/CHECKLIST.md). Without R2 no alarm wakes the screen. Android
 *   won't enter deep Doze with an alarm clock under 1 h away, so a deep-Doze run needs R1 > 60 min.
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
                    "seed" -> {
                        // CHECKLIST.md setup: R1 and R2, missed after 2 min.
                        clear(app)
                        c.settings.setMissedTimeout(2)
                        c.settings.setPausedUntil(null)
                        val base = Reminder(label = "R1", intervalMinutes = intent.getIntExtra("r1Minutes", 1), windowStart = 0, windowEnd = 0, days = Days.EVERY_DAY, createdAt = Instant.now())
                        val r1 = c.engine.save(base)
                        val r2 = if (!intent.getBooleanExtra("r2", true)) null else c.engine.save(
                            base.copy(label = "R2", intervalMinutes = 2, ringStyle = RingStyle.ALARM, alarmMode = AlarmMode.FULL, ringSeconds = 30, snoozeMinutes = 5),
                        )
                        Log.i(TAG, "seeded R1=$r1 R2=$r2")
                    }
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
