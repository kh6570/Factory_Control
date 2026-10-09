// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.common.Dispatcher
import com.raghim.herz.core.common.HerzDispatchers
import com.raghim.herz.core.database.dao.SensorDao
import com.raghim.herz.core.database.model.SensorEntity
import com.raghim.herz.core.domain.sensor.SensorRepository
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.Sensor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSensorRepository @Inject constructor(
    private val sensorDao: SensorDao,
    private val clock: AppClock,
    @Dispatcher(HerzDispatchers.IO) private val io: CoroutineDispatcher,
) : SensorRepository {

    override val sensors: Flow<List<Sensor>> = sensorDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun get(id: String): Sensor? = withContext(io) {
        sensorDao.get(id)?.toModel()
    }

    override suspend fun add(name: String, area: String?): Sensor = withContext(io) {
        val entity = SensorEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            area = area,
            linkedCameraIds = "",
            highlightOnAlarm = true,
            alarmStyle = STYLE_SOUND,
            soundId = AlarmSound.ALARM.name,
            addedAtEpochMs = clock.now().toEpochMilli(),
            sortOrder = sensorDao.maxSortOrder() + 1,
        )
        sensorDao.upsert(entity)
        entity.toModel()
    }

    override suspend fun update(id: String, name: String, area: String?) = withContext(io) {
        sensorDao.update(id, name, area)
    }

    override suspend fun setLinks(id: String, cameraIds: Set<String>) = withContext(io) {
        sensorDao.setLinks(id, cameraIds.encodeCameraIds())
    }

    override suspend fun setHighlight(id: String, enabled: Boolean) = withContext(io) {
        sensorDao.setHighlight(id, enabled)
    }

    override suspend fun setAlarmStyle(id: String, style: AlarmStyle) = withContext(io) {
        val (alarmStyle, soundId) = style.toColumns()
        sensorDao.setAlarmStyle(id, alarmStyle, soundId)
    }

    override suspend fun remove(id: String) = withContext(io) {
        sensorDao.delete(id)
    }

    override suspend fun reorder(idsInOrder: List<String>) = withContext(io) {
        idsInOrder.forEachIndexed { index, id -> sensorDao.setSortOrder(id, index) }
    }
}
