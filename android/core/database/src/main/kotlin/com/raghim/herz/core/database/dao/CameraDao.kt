// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.raghim.herz.core.database.model.CameraEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CameraEntity>>

    @Query("SELECT * FROM cameras WHERE id = :id")
    suspend fun get(id: String): CameraEntity?

    @Upsert
    suspend fun upsert(entity: CameraEntity)

    @Query("UPDATE cameras SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM cameras WHERE id = :id")
    suspend fun delete(id: String)
}
