package io.github.burithu.interval

import android.app.Application
import android.content.Context
import io.github.burithu.interval.data.SettingsStore
import io.github.burithu.interval.data.db.IntervalDatabase
import io.github.burithu.interval.ringing.AlarmScheduler
import io.github.burithu.interval.ringing.Notifier
import io.github.burithu.interval.scheduling.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency wiring (SPEC §6). One instance per process. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = IntervalDatabase.create(context)
    val settings = SettingsStore(context)
    val alarms = AlarmScheduler(context)
    val notifier = Notifier(context)
    val engine = ReminderEngine(db, settings, alarms, notifier)
}

class IntervalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
