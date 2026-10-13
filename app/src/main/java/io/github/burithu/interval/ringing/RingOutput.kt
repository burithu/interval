package io.github.burithu.interval.ringing

import io.github.burithu.interval.data.AlarmMode
import io.github.burithu.interval.data.Reminder
import io.github.burithu.interval.data.RingStyle

/** The phone's ringer switch. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/** What the phone allows right now. [dnd] is any Do Not Disturb level. */
data class PhoneState(val ringer: RingerMode, val dnd: Boolean, val dndAllowsAlarms: Boolean)

/** What one ring does besides showing (D33, D34, D39). [vibrateAsAlarm] makes the vibration pass silent mode. */
data class RingOutput(val sound: Boolean, val vibration: Boolean, val vibrateAsAlarm: Boolean)

/**
 * - Ringer: silent shows only, vibrate drops the sound (D33). Vibrate on silent keeps the
 *   vibration (D34).
 * - Do Not Disturb (D39): Android reports it as silent at every level, even when alarms may
 *   ring. Alarms follow DND's own alarm setting instead, like the Clock app. Notifications
 *   count as silent.
 */
fun ringOutput(r: Reminder, phone: PhoneState): RingOutput {
    val alarm = r.ringStyle == RingStyle.ALARM
    val mode = when {
        !phone.dnd -> phone.ringer
        alarm && phone.dndAllowsAlarms -> RingerMode.NORMAL
        else -> RingerMode.SILENT
    }
    val silent = mode == RingerMode.SILENT
    val vibrates = if (alarm) r.alarmMode != AlarmMode.SCREEN else r.vibrate
    return RingOutput(
        sound = mode == RingerMode.NORMAL && (!alarm || r.alarmMode == AlarmMode.FULL),
        vibration = vibrates && (!silent || r.vibrateOnSilent),
        vibrateAsAlarm = alarm || silent,
    )
}
