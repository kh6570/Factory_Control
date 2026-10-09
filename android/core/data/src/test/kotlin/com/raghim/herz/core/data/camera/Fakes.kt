// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.database.dao.ActiveSessionDao
import com.raghim.herz.core.database.dao.CameraDao
import com.raghim.herz.core.database.model.ActiveSessionEntity
import com.raghim.herz.core.database.model.ActiveSessionRow
import com.raghim.herz.core.database.model.CameraEntity
import com.raghim.herz.core.security.CredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Reversible, obviously-not-plaintext cipher. Anything without the prefix fails like a lost key. */
class FakeCredentialCipher : CredentialCipher {
    override fun encrypt(plain: String): String = PREFIX + plain.reversed()

    override fun decrypt(encoded: String): String {
        require(encoded.startsWith(PREFIX)) { "bad ciphertext" }
        return encoded.removePrefix(PREFIX).reversed()
    }

    companion object {
        const val PREFIX = "enc:"
    }
}

class FakeAppClock(var time: Instant = Instant.parse("2026-01-01T10:00:00Z")) : AppClock {
    override fun now(): Instant = time
}

class FakeCameraDao : CameraDao {
    val rows = MutableStateFlow<Map<String, CameraEntity>>(emptyMap())

    override fun observeAll(): Flow<List<CameraEntity>> =
        rows.map { map -> map.values.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })) }

    override suspend fun maxSortOrder(): Int = rows.value.values.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun get(id: String): CameraEntity? = rows.value[id]

    override suspend fun upsert(entity: CameraEntity) {
        rows.update { it + (entity.id to entity) }
    }

    override suspend fun rename(id: String, name: String) {
        rows.update { map -> map[id]?.let { map + (id to it.copy(name = name)) } ?: map }
    }

    override suspend fun setSortOrder(id: String, sortOrder: Int) {
        rows.update { map -> map[id]?.let { map + (id to it.copy(sortOrder = sortOrder)) } ?: map }
    }

    override suspend fun delete(id: String) {
        rows.update { it - id }
    }
}

/** Mimics the JOIN with `cameras` and the primary-key IGNORE conflict strategy. */
class FakeActiveSessionDao(private val cameraNames: Map<String, String>) : ActiveSessionDao {
    val sessions = MutableStateFlow<List<ActiveSessionEntity>>(emptyList())

    override fun observeAll(): Flow<List<ActiveSessionRow>> = sessions.map { list ->
        list.mapNotNull { session ->
            cameraNames[session.cameraId]?.let { name ->
                ActiveSessionRow(
                    cameraId = session.cameraId,
                    name = name,
                    source = session.source,
                    startedAtEpochMs = session.startedAtEpochMs,
                    alarmId = session.alarmId,
                    highlight = session.highlight,
                )
            }
        }.sortedBy { it.startedAtEpochMs }
    }

    override suspend fun insertIgnore(sessions: List<ActiveSessionEntity>) {
        require(sessions.all { it.cameraId in cameraNames }) { "FOREIGN KEY constraint failed" }
        this.sessions.update { current ->
            val present = current.mapTo(HashSet()) { it.cameraId }
            current + sessions.filter { present.add(it.cameraId) }
        }
    }

    override suspend fun existingCameraIds(ids: List<String>): List<String> = ids.filter { it in cameraNames }

    override suspend fun activeCameraIds(ids: List<String>): List<String> {
        val wanted = ids.toSet()
        return sessions.value.map { it.cameraId }.filter { it in wanted }
    }

    override suspend fun clearAlarm(manualSource: String, alarmSource: String) {
        sessions.update { list ->
            list.map { session ->
                if (session.source == alarmSource) {
                    session.copy(source = manualSource, alarmId = null, highlight = false)
                } else {
                    session
                }
            }
        }
    }

    override suspend fun promote(
        cameraId: String,
        source: String,
        startedAtEpochMs: Long,
        alarmId: String,
        highlight: Boolean,
    ) {
        sessions.update { list ->
            list.map { session ->
                if (session.cameraId == cameraId) {
                    session.copy(
                        source = source,
                        startedAtEpochMs = startedAtEpochMs,
                        alarmId = alarmId,
                        highlight = highlight,
                    )
                } else {
                    session
                }
            }
        }
    }

    override suspend fun delete(cameraId: String) {
        sessions.update { list -> list.filterNot { it.cameraId == cameraId } }
    }
}
