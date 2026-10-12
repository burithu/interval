package io.github.burithu.interval

import android.app.Application
import android.content.Context
import io.github.burithu.interval.data.RestoreAgent
import io.github.burithu.interval.data.SettingsStore
import io.github.burithu.interval.data.db.IntervalDatabase
import io.github.burithu.interval.ringing.AlarmScheduler
import io.github.burithu.interval.ringing.Notifier
import io.github.burithu.interval.ringing.Ringer
import io.github.burithu.interval.scheduling.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File

/** Manual dependency wiring (SPEC §6). One instance per process. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = IntervalDatabase.create(context)
    val settings = SettingsStore(context)
    val alarms = AlarmScheduler(context)
    val notifier = Notifier(context)
    val ringer = Ringer(context, notifier)
    val engine = ReminderEngine(db, settings, alarms, notifier, ringer)
}

class IntervalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()
        val restored = File(noBackupFilesDir, RestoreAgent.MARKER)
        if (restored.exists()) {
            // Once per restore, before any screen reads the settings: setup must run again on this
            // phone, so Home can't show first with permissions it doesn't have.
            runBlocking { container.engine.onRestored() }
            restored.delete()
        }
    }
}
