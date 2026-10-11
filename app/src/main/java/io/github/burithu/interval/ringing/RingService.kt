package io.github.burithu.interval.ringing

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
 * - Alarm: alarm audio usage, loops for the ring length, rings through silent mode.
 *   The notification carries a full-screen intent to AlarmActivity.
 * - Notification: one chime and vibration, following the ringer mode. The notification is
 *   then detached so it stays in the shade, dismissible, after the service stops.
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
        val mode = audio.ringerMode
        val withSound = alarm || mode == AudioManager.RINGER_MODE_NORMAL
        val withVibration = r.vibrate && (alarm || mode != AudioManager.RINGER_MODE_SILENT)
        val maxLength = if (alarm) Duration.ofSeconds(r.ringSeconds.toLong()) else CHIME_MAX

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "interval:ring")
            .apply { acquire(maxLength.plusSeconds(5).toMillis()) }
        if (withSound) startSound(r.soundUri, alarm, r.id)
        if (withVibration) vibrate(alarm)

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
        stopPlayback()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.github.burithu.interval.RING_START"
        const val EXTRA_ID = "reminder_id"
        private val CHIME_MAX: Duration = Duration.ofSeconds(10)
        private val VIBRATE_ONLY: Duration = Duration.ofMillis(1500)
    }
}
