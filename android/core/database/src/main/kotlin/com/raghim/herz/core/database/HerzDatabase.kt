// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raghim.herz.core.database.dao.ActiveSessionDao
import com.raghim.herz.core.database.dao.CameraDao
import com.raghim.herz.core.database.dao.DoorDao
import com.raghim.herz.core.database.dao.SensorDao
import com.raghim.herz.core.database.model.ActiveSessionEntity
import com.raghim.herz.core.database.model.CameraEntity
import com.raghim.herz.core.database.model.DoorEntity
import com.raghim.herz.core.database.model.SensorEntity

@Database(
    entities = [CameraEntity::class, ActiveSessionEntity::class, DoorEntity::class, SensorEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class HerzDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

    abstract fun activeSessionDao(): ActiveSessionDao

    abstract fun doorDao(): DoorDao

    abstract fun sensorDao(): SensorDao
}
