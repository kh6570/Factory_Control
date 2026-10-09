// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved sensor. Live signals are not stored here. */
@Entity(tableName = "sensors")
data class SensorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val area: String?,
    /** Camera ids joined with commas. Empty when the sensor is not linked to a camera. */
    val linkedCameraIds: String,
    val highlightOnAlarm: Boolean,
    /** `VIBRATION` or `SOUND`. */
    val alarmStyle: String,
    /** An [com.raghim.herz.core.model.AlarmSound] name. Ignored when [alarmStyle] is vibration. */
    val soundId: String,
    val addedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
