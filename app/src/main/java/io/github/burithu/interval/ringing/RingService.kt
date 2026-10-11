package io.github.burithu.interval.ringing

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import io.github.burithu.interval.ui.alarm.AlarmActivity
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import io.github.burithu.interval.IntervalApp
import io.github.burithu.interval.data.AlarmMode
import io.github.burithu.interval.data.RingStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Plays ring sound and vibration itself instead of relying on notification channel sound,
 * which HyperOS mutes per app by default.
 *
 * - Alarm: opens AlarmActivity for the ring length. Depending on its [AlarmMode] it also loops
 *   sound on the alarm stream and/or vibrates. The notification carries a full-screen intent
 *   as a fallback.
 * - Notification: one chime and vibration. The notification is then detached so it stays in
 *   the shade, dismissible, after the service stops.
 *
 * Both follow the ringer mode: silent means no sound or vibration, vibrate means no sound.
 */
class RingService : Service() {
    private val container get() = (application as IntervalApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audio: AudioManager
    private lateinit var vibrator: Vibrator
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var endJob: Job? = null
    private var playingId: Long? = null
    private var overlay: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        // A session removed from outside (dismiss, snooze, delete) stops its sound.
        scope.launch {
            container.ringer.sessions.collect { sessions ->
                val id = playingId
                if (id != null && id !in sessions) {
                    playingId = null
                    stopPlayback()
                    ServiceCompat.stopForeground(this@RingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                }
                if (sessions.isEmpty() && playingId == null) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1
        val session = container.ringer.sessions.value[id]
        if (intent?.action != ACTION_START || session == null) {
            if (playingId == null) stopSelf()
            return START_NOT_STICKY
        }
        val notification = container.notifier.buildRing(session.reminder)
        try {
            ServiceCompat.startForeground(
                this, id.toInt(), notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
            )
        } catch (_: Exception) {
            // Exact-alarm permission missing makes this service type unavailable. Still show and sound it.
            container.notifier.post(id, notification)
        }
        play(session)
        return START_NOT_STICKY
    }

    private fun play(session: RingSession) {
        val r = session.reminder
        playingId?.takeIf { it != r.id }?.let { previous ->
            // A second reminder rang while one was sounding. The newest takes over the speaker.
            playingId = null
            container.ringer.stop(previous)
        }
        stopPlayback()
        playingId = r.id

        val alarm = r.ringStyle == RingStyle.ALARM
        // Both styles follow the ringer mode (D33): silent shows only, vibrate drops the sound.
        // An alarm always shows its screen; its mode decides what else it does.
        val mode = audio.ringerMode
        val withSound = mode == AudioManager.RINGER_MODE_NORMAL && (!alarm || r.alarmMode == AlarmMode.FULL)
        val withVibration = mode != AudioManager.RINGER_MODE_SILENT && (if (alarm) r.alarmMode != AlarmMode.SCREEN else r.vibrate)
        val maxLength = if (alarm) Duration.ofSeconds(r.ringSeconds.toLong()) else CHIME_MAX

        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "interval:ring")
            .apply { acquire(maxLength.plusSeconds(5).toMillis()) }
        if (alarm) {
            // Backup for the alarm screen's turnScreenOn: light the display so the alarm is seen.
            @Suppress("DEPRECATION")
            power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "interval:alarm_screen",
            ).acquire(SCREEN_WAKE.toMillis())
        }
        if (withSound) startSound(r.soundUri, alarm, r.id)
        if (withVibration) vibrate(alarm)
        if (alarm) openAlarmScreen(r.id)

        endJob = scope.launch {
            if (alarm) {
                val until = session.endsAt ?: Instant.now().plus(maxLength)
                delay(Duration.between(Instant.now(), until).toMillis().coerceAtLeast(0))
                finish(r.id, missed = true)
            } else {
                delay(if (withSound) CHIME_MAX.toMillis() else VIBRATE_ONLY.toMillis())
                finish(r.id, missed = false)
            }
        }
    }

    /**
     * Opens the alarm screen from the background. Android 15+ only lets a "Display over other
     * apps" holder start activities while one of its overlay windows is visible, so a 1×1
     * transparent overlay is shown for the moment of the launch.
     */
    private fun openAlarmScreen(id: Long) {
        val canOverlay = Settings.canDrawOverlays(this)
        if (canOverlay) showLaunchOverlay()
        scope.launch {
            if (canOverlay) delay(OVERLAY_SETTLE.toMillis())
            AlarmActivity.open(this@RingService, id)
            delay(OVERLAY_HOLD.toMillis())
            removeLaunchOverlay()
        }
    }

    private fun showLaunchOverlay() {
        if (overlay != null) return
        val view = View(this)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
        } catch (_: RuntimeException) {
            // Overlay refused; the full-screen intent remains the fallback.
        }
    }

    private fun removeLaunchOverlay() {
        overlay?.let { v ->
            try { getSystemService(WindowManager::class.java).removeView(v) } catch (_: RuntimeException) { }
        }
        overlay = null
    }

    /** Natural end: the chime finished, or the alarm's ring length ran out. */
    private fun finish(id: Long, missed: Boolean) {
        if (playingId != id) return
        playingId = null
        stopPlayback()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        container.ringer.stop(id)
        if (missed) container.scope.launch { container.engine.onMissedLabel(id) }
    }

    private fun startSound(soundUri: String?, alarm: Boolean, id: Long) {
        val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        val attrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uri = soundUri?.toUri()
            ?: RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_RINGTONE_URI
        focus = AudioFocusRequest.Builder(
            if (alarm) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        ).setAudioAttributes(attrs).build().also { audio.requestAudioFocus(it) }
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@RingService, uri)
                isLooping = alarm
                setOnPreparedListener { it.start() }
                if (!alarm) setOnCompletionListener { finish(id, missed = false) }
                prepareAsync()
            }
        } catch (_: Exception) {
            null // Unplayable sound file. Vibration still runs.
        }
    }

    private fun vibrate(alarm: Boolean) {
        val effect = if (alarm) VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        else VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(if (alarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .build(),
            )
        }
    }

    private fun stopPlayback() {
        endJob?.cancel()
        endJob = null
        player?.run {
            try { stop() } catch (_: IllegalStateException) { }
            release()
        }
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        vibrator.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        removeLaunchOverlay()
        stopPlayback()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.github.burithu.interval.RING_START"
        const val EXTRA_ID = "reminder_id"
        private val CHIME_MAX: Duration = Duration.ofSeconds(10)
        private val VIBRATE_ONLY: Duration = Duration.ofMillis(1500)
        private val SCREEN_WAKE: Duration = Duration.ofSeconds(10)
        private val OVERLAY_SETTLE: Duration = Duration.ofMillis(200)
        private val OVERLAY_HOLD: Duration = Duration.ofSeconds(3)
    }
}
