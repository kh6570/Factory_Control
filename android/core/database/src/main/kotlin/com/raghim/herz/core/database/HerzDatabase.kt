// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raghim.herz.core.database.dao.ActiveSessionDao
import com.raghim.herz.core.database.dao.CameraDao
import com.raghim.herz.core.database.model.ActiveSessionEntity
import com.raghim.herz.core.database.model.CameraEntity

@Database(
    entities = [CameraEntity::class, ActiveSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HerzDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

    abstract fun activeSessionDao(): ActiveSessionDao
}
