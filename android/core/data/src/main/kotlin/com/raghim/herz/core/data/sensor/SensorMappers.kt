// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import com.raghim.herz.core.database.model.SensorEntity
import com.raghim.herz.core.domain.sensor.SensorLimits
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.Sensor
import java.time.Instant

internal fun SensorEntity.toModel(): Sensor = Sensor(
    id = id,
    name = name,
    area = area,
    linkedCameraIds = linkedCameraIds.decodeCameraIds(),
    highlightOnAlarm = highlightOnAlarm,
    alarmStyle = alarmStyle.toStyle(soundId),
    addedAt = Instant.ofEpochMilli(addedAtEpochMs),
    sortOrder = sortOrder,
)

internal fun Set<String>.encodeCameraIds(): String =
    filter { it.isNotBlank() && ',' !in it }
        .distinct()
        .sorted()
        .take(SensorLimits.MAX_LINKED_CAMERAS)
        .joinToString(",")

internal fun String.decodeCameraIds(): Set<String> =
    split(',').filter { it.isNotEmpty() }.toSet()

internal fun AlarmStyle.toColumns(): Pair<String, String> = when (this) {
    AlarmStyle.VibrationOnly -> STYLE_VIBRATION to AlarmSound.ALARM.name
    is AlarmStyle.Sound -> STYLE_SOUND to sound.name
}

internal fun String.toStyle(soundId: String): AlarmStyle = when (this) {
    STYLE_VIBRATION -> AlarmStyle.VibrationOnly
    else -> AlarmStyle.Sound(AlarmSound.entries.firstOrNull { it.name == soundId } ?: AlarmSound.ALARM)
}

internal const val STYLE_VIBRATION = "VIBRATION"
internal const val STYLE_SOUND = "SOUND"
