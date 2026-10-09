// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.Dispatcher
import com.raghim.herz.core.common.HerzDispatchers
import com.raghim.herz.core.database.dao.ActiveSessionDao
import com.raghim.herz.core.database.model.ActiveSessionEntity
import com.raghim.herz.core.domain.camera.ActiveCamerasRepository
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.SessionSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Live wall for direct-LAN mode, persisted so it survives app restarts. */
@Singleton
class LocalActiveCamerasRepository @Inject constructor(
    private val activeSessionDao: ActiveSessionDao,
    private val clock: AppClock,
    @Dispatcher(HerzDispatchers.IO) private val io: CoroutineDispatcher,
) : ActiveCamerasRepository {

    override val active: Flow<List<ActiveCamera>> =
        activeSessionDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun start(ids: List<String>) {
        if (ids.isEmpty()) return
        withContext(io) {
            val now = clock.now().toEpochMilli()
            val sessions = ids.distinct().mapIndexed { index, id ->
                ActiveSessionEntity(
                    cameraId = id,
                    source = SessionSource.MANUAL.name,
                    startedAtEpochMs = now + index,
                )
            }
            activeSessionDao.insertIgnoreForExistingCameras(sessions)
        }
    }

    override suspend fun raiseAlarm(cameraIds: List<String>, alarmId: String, highlight: Boolean) {
        val ids = cameraIds.distinct()
        if (ids.isEmpty()) return
        withContext(io) {
            val known = activeSessionDao.existingCameraIds(ids).toSet()
            if (known.isEmpty()) return@withContext
            val present = activeSessionDao.activeCameraIds(known.toList()).toSet()
            val now = clock.now().toEpochMilli()
            known.filter { it in present }.forEach { id ->
                activeSessionDao.promote(
                    cameraId = id,
                    source = SessionSource.ALARM.name,
                    startedAtEpochMs = now,
                    alarmId = alarmId,
                    highlight = highlight,
                )
            }
            val fresh = known.filter { it !in present }.map { id ->
                ActiveSessionEntity(
                    cameraId = id,
                    source = SessionSource.ALARM.name,
                    startedAtEpochMs = now,
                    alarmId = alarmId,
                    highlight = highlight,
                )
            }
            activeSessionDao.insertIgnore(fresh)
        }
    }

    override suspend fun clearAlarm() = withContext(io) {
        activeSessionDao.clearAlarm(
            manualSource = SessionSource.MANUAL.name,
            alarmSource = SessionSource.ALARM.name,
        )
    }

    override suspend fun stop(id: String) = withContext(io) {
        activeSessionDao.delete(id)
    }

    override suspend fun refresh() = Unit
}
