package io.github.burithu.interval.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** D31: in-app theme choice. SYSTEM keeps D16 (follow the phone). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val missedTimeoutMinutes: Int = 5,
    val defaultSnoozeMinutes: Int = 10,
    val pausedUntil: Instant? = null,
    val onboardingDone: Boolean = false,
    /** Setup steps the phone cannot report, confirmed by the user (M3). */
    val confirmedSteps: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Global options (SPEC §5). Stored in DataStore so Auto Backup includes them. */
class SettingsStore(context: Context) {
    private val store = context.dataStore

    private object Keys {
        val missedTimeout = intPreferencesKey("missed_timeout_minutes")
        val defaultSnooze = intPreferencesKey("default_snooze_minutes")
        val pausedUntil = longPreferencesKey("paused_until")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val confirmedSteps = stringSetPreferencesKey("confirmed_setup_steps")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            missedTimeoutMinutes = p[Keys.missedTimeout] ?: 5,
            defaultSnoozeMinutes = p[Keys.defaultSnooze] ?: 10,
            pausedUntil = p[Keys.pausedUntil]?.let(Instant::ofEpochMilli),
            onboardingDone = p[Keys.onboardingDone] ?: false,
            confirmedSteps = p[Keys.confirmedSteps] ?: emptySet(),
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.themeMode] = mode.name }

    suspend fun setStepConfirmed(id: String, confirmed: Boolean) = store.edit { p ->
        val current = p[Keys.confirmedSteps] ?: emptySet()
        p[Keys.confirmedSteps] = if (confirmed) current + id else current - id
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setPausedUntil(until: Instant?) = store.edit { p ->
        if (until == null) p.remove(Keys.pausedUntil) else p[Keys.pausedUntil] = until.toEpochMilli()
    }

    suspend fun setMissedTimeout(minutes: Int) = store.edit { it[Keys.missedTimeout] = minutes }
    suspend fun setDefaultSnooze(minutes: Int) = store.edit { it[Keys.defaultSnooze] = minutes }
    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[Keys.onboardingDone] = done }

    /** After a restore: setup is per phone, so it runs again and hand-confirmed steps are cleared. */
    suspend fun resetSetup() = store.edit { p ->
        p[Keys.onboardingDone] = false
        p.remove(Keys.confirmedSteps)
    }
}
