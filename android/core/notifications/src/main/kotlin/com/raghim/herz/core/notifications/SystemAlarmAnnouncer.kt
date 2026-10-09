// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.raghim.herz.core.domain.sensor.AlarmAnnouncer
import com.raghim.herz.core.domain.sensor.SensorLimits
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.RingingAlarm
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rings, vibrates, and posts one alarm notification. [stop] and the next [start] release all of it.
 * The ringtone still plays when notification permission is denied and the app is in front.
 */
@Singleton
class SystemAlarmAnnouncer @Inject constructor(
    @ApplicationContext context: Context,
) : AlarmAnnouncer {
    private val main = Handler(Looper.getMainLooper())
    private val output = AndroidAlarmOutput(context)
    private val signal = AlarmSignal(output)

    init {
        output.cancelNotification()
    }

    override fun start(alarm: RingingAlarm) {
        main.post { signal.start(alarm) }
    }

    override fun stop() {
        main.post { signal.stop() }
    }
}

private class AndroidAlarmOutput(private val context: Context) : AlarmOutput {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun play(sound: AlarmSound) {
        val uri = RingtoneManager.getDefaultUri(sound.ringtoneType())
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        val tone = RingtoneManager.getRingtone(context, uri) ?: return
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
        tone.play()
        ringtone = tone
    }

    override fun vibrate() {
        val vibrator = deviceVibrator()
        this.vibrator = vibrator
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, VIBRATE_ON_MS, VIBRATE_OFF_MS), 0))
    }

    override fun show(alarm: RingingAlarm) {
        wakeScreen()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(alarm.sensorName)
            .setContentText(context.getString(R.string.alarm_notification_body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(launchIntent())
            .setFullScreenIntent(launchIntent(), true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission denied. The in-process ringtone and vibration still run.
        }
    }

    override fun release() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        cancelNotification()
    }

    fun cancelNotification() {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun deviceVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val power = context.getSystemService(android.os.PowerManager::class.java) ?: return
        val lock = wakeLock ?: power.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "herz:sensor-alarm",
        ).also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        if (!lock.isHeld) lock.acquire(SensorLimits.SCREEN_WAKE.inWholeMilliseconds)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun launchIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "herz_sensor_alarm"
        const val NOTIFICATION_ID = 41
        /** Repeating pattern: on, off. Cancelled in [release]. */
        const val VIBRATE_ON_MS = 500L
        const val VIBRATE_OFF_MS = 400L
    }
}

private fun AlarmSound.ringtoneType(): Int = when (this) {
    AlarmSound.ALARM -> RingtoneManager.TYPE_ALARM
    AlarmSound.RINGTONE -> RingtoneManager.TYPE_RINGTONE
    AlarmSound.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
}
