// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raghim.herz.core.database.model.SensorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Query("SELECT * FROM sensors ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SensorEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM sensors")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM sensors WHERE id = :id")
    suspend fun get(id: String): SensorEntity?

    @Upsert
    suspend fun upsert(entity: SensorEntity)

    @Query("UPDATE sensors SET name = :name, area = :area WHERE id = :id")
    suspend fun update(id: String, name: String, area: String?)

    @Query("UPDATE sensors SET linkedCameraIds = :linkedCameraIds WHERE id = :id")
    suspend fun setLinks(id: String, linkedCameraIds: String)

    @Query("UPDATE sensors SET highlightOnAlarm = :enabled WHERE id = :id")
    suspend fun setHighlight(id: String, enabled: Boolean)

    @Query("UPDATE sensors SET alarmStyle = :alarmStyle, soundId = :soundId WHERE id = :id")
    suspend fun setAlarmStyle(id: String, alarmStyle: String, soundId: String)

    @Query("UPDATE sensors SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM sensors WHERE id = :id")
    suspend fun delete(id: String)
}
