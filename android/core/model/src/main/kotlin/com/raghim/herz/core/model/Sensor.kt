// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.model

import java.time.Instant

/**
 * A sensor that can start the cameras linked to it. The signal itself (a timer today, a node later)
 * is not stored here.
 */
data class Sensor(
    val id: String,
    val name: String,
    val area: String? = null,
    val linkedCameraIds: Set<String> = emptySet(),
    /** Blinking red border on the linked cameras when this sensor alarms. */
    val highlightOnAlarm: Boolean = true,
    val alarmStyle: AlarmStyle = AlarmStyle.Sound(AlarmSound.ALARM),
    val addedAt: Instant,
    /** Position in the Sensors list. */
    val sortOrder: Int = 0,
)

/** How the phone asks for attention. Vibration only, or one of the phone's own sounds. */
sealed interface AlarmStyle {
    data object VibrationOnly : AlarmStyle
    data class Sound(val sound: AlarmSound) : AlarmStyle
}

/** A sound that already exists on the phone. No audio files are shipped with the app. */
enum class AlarmSound { ALARM, RINGTONE, NOTIFICATION }

/** One sensor fired. The screens never know whether a timer or a real node produced it. */
data class SensorSignal(val sensorId: String)

/** The alarm the phone is ringing for. Dismissing it does not take cameras off the wall. */
data class RingingAlarm(
    val sensorId: String,
    val sensorName: String,
    val style: AlarmStyle,
)
