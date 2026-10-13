package io.github.burithu.interval.ringing

import io.github.burithu.interval.data.AlarmMode
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every ring type under normal, vibrate, silent and Do Not Disturb (D33, D34, D39). */
class RingOutputTest {
    private val notif = Reminder(label = "n")
    private val notifNoVibrate = notif.copy(vibrate = false)
    private val notifVos = notif.copy(vibrateOnSilent = true)
    private fun alarm(mode: AlarmMode, vos: Boolean = false) =
        Reminder(label = "a", ringStyle = RingStyle.ALARM, alarmMode = mode, vibrateOnSilent = vos)

    private val normal = PhoneState(RingerMode.NORMAL, dnd = false, dndAllowsAlarms = false)
    private val vibrate = PhoneState(RingerMode.VIBRATE, dnd = false, dndAllowsAlarms = false)
    private val silent = PhoneState(RingerMode.SILENT, dnd = false, dndAllowsAlarms = false)
    // Android reports the ringer as silent at every DND level.
    private val dnd = PhoneState(RingerMode.SILENT, dnd = true, dndAllowsAlarms = true)
    private val dndTotal = PhoneState(RingerMode.SILENT, dnd = true, dndAllowsAlarms = false)

    private fun out(sound: Boolean, vibration: Boolean, asAlarm: Boolean) = RingOutput(sound, vibration, asAlarm)
    private val nothing = out(sound = false, vibration = false, asAlarm = true)

    @Test fun normal() {
        assertEquals(out(sound = true, vibration = true, asAlarm = false), ringOutput(notif, normal))
        assertEquals(out(sound = true, vibration = false, asAlarm = false), ringOutput(notifNoVibrate, normal))
        assertEquals(out(sound = false, vibration = false, asAlarm = true), ringOutput(alarm(AlarmMode.SCREEN), normal))
        assertEquals(out(sound = false, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.VIBRATE), normal))
        assertEquals(out(sound = true, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.FULL), normal))
    }

    @Test fun vibrateModeDropsSound() {
        assertEquals(out(sound = false, vibration = true, asAlarm = false), ringOutput(notif, vibrate))
        assertEquals(out(sound = false, vibration = false, asAlarm = false), ringOutput(notifNoVibrate, vibrate))
        assertEquals(out(sound = false, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.FULL), vibrate))
    }

    @Test fun silentShowsOnly() {
        assertEquals(nothing, ringOutput(notif, silent))
        assertEquals(nothing, ringOutput(alarm(AlarmMode.VIBRATE), silent))
        assertEquals(nothing, ringOutput(alarm(AlarmMode.FULL), silent))
    }

    @Test fun vibrateOnSilentKeepsVibrationOnly() {
        assertEquals(out(sound = false, vibration = true, asAlarm = true), ringOutput(notifVos, silent))
        assertEquals(out(sound = false, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.FULL, vos = true), silent))
        // Screen-only alarms never vibrate.
        assertEquals(nothing, ringOutput(alarm(AlarmMode.SCREEN, vos = true), silent))
    }

    @Test fun dndLetsAlarmsRingWhenItAllowsAlarms() {
        assertEquals(out(sound = true, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.FULL), dnd))
        assertEquals(out(sound = false, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.VIBRATE), dnd))
        assertEquals(nothing, ringOutput(alarm(AlarmMode.SCREEN), dnd))
    }

    @Test fun dndSilencesNotifications() {
        assertEquals(nothing, ringOutput(notif, dnd))
        assertEquals(out(sound = false, vibration = true, asAlarm = true), ringOutput(notifVos, dnd))
    }

    @Test fun dndTotalSilenceShowsOnly() {
        assertEquals(nothing, ringOutput(alarm(AlarmMode.FULL), dndTotal))
        assertEquals(nothing, ringOutput(notif, dndTotal))
    }

    @Test fun dndIgnoresAReportedNormalRinger() {
        // Some phones keep the ringer normal while DND is on; DND still decides.
        val dndNormal = dnd.copy(ringer = RingerMode.NORMAL)
        assertEquals(nothing, ringOutput(notif, dndNormal))
        assertEquals(out(sound = true, vibration = true, asAlarm = true), ringOutput(alarm(AlarmMode.FULL), dndNormal))
    }
}
