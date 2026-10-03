// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raghim.herz.core.database.model.ActiveSessionEntity
import com.raghim.herz.core.database.model.ActiveSessionRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveSessionDao {
    @Query(
        """
        SELECT s.cameraId AS cameraId, c.name AS name, s.source AS source, s.startedAtEpochMs AS startedAtEpochMs
        FROM active_sessions s
        JOIN cameras c ON c.id = s.cameraId
        ORDER BY s.startedAtEpochMs
        """,
    )
    fun observeAll(): Flow<List<ActiveSessionRow>>

    /** Already-active cameras are skipped. Unknown camera ids violate the foreign key and throw. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sessions: List<ActiveSessionEntity>)

    @Query("SELECT id FROM cameras WHERE id IN (:ids)")
    suspend fun existingCameraIds(ids: List<String>): List<String>

    /** Like [insertIgnore], but drops sessions whose camera no longer exists. */
    @Transaction
    suspend fun insertIgnoreForExistingCameras(sessions: List<ActiveSessionEntity>) {
        if (sessions.isEmpty()) return
        val existing = existingCameraIds(sessions.map { it.cameraId }).toHashSet()
        insertIgnore(sessions.filter { it.cameraId in existing })
    }

    @Query("DELETE FROM active_sessions WHERE cameraId = :cameraId")
    suspend fun delete(cameraId: String)
}
