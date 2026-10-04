// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raghim.herz.core.database.model.DoorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoorDao {
    @Query("SELECT * FROM doors ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<DoorEntity>>

    @Query("SELECT COUNT(*) FROM doors")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: DoorEntity)

    @Query("UPDATE doors SET name = :name, area = :area WHERE id = :id")
    suspend fun update(id: String, name: String, area: String?)

    @Query("UPDATE doors SET onLivePanel = :shown WHERE id = :id")
    suspend fun setOnLivePanel(id: String, shown: Boolean)

    @Query("DELETE FROM doors WHERE id = :id")
    suspend fun delete(id: String)
}
