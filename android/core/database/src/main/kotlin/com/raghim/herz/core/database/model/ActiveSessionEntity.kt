// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** A camera on the live wall. [source] is a `SessionSource` name. */
@Entity(
    tableName = "active_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CameraEntity::class,
            parentColumns = ["id"],
            childColumns = ["cameraId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActiveSessionEntity(
    @PrimaryKey val cameraId: String,
    val source: String,
    val startedAtEpochMs: Long,
    val alarmId: String? = null,
    /** Red border for this alarm. Stored with the session so a later settings change does not rewrite the wall. */
    @ColumnInfo(defaultValue = "0") val highlight: Boolean = false,
)

/** One live-wall row joined with the camera name. */
data class ActiveSessionRow(
    val cameraId: String,
    val name: String,
    val source: String,
    val startedAtEpochMs: Long,
    val alarmId: String? = null,
    val highlight: Boolean = false,
)
