// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.model

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
)

/** One live-wall row joined with the camera name. */
data class ActiveSessionRow(
    val cameraId: String,
    val name: String,
    val source: String,
    val startedAtEpochMs: Long,
)
