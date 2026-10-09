// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved door. Lock and reed state are live and are not stored here. */
@Entity(tableName = "doors")
data class DoorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val area: String?,
    /** Camera ids joined with commas. Empty when the door is not linked to a camera. */
    val linkedCameraIds: String,
    val isOnline: Boolean,
    val onLivePanel: Boolean,
    val addedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
