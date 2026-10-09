// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.sensor

import com.raghim.herz.core.domain.camera.ActiveCamerasRepository
import com.raghim.herz.core.domain.camera.CameraRepository
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.RingingAlarm
import com.raghim.herz.core.model.Sensor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ObserveSensorsUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    operator fun invoke(): Flow<List<Sensor>> =
        repository.sensors.map { sensors ->
            sensors.sortedWith(compareBy<Sensor>({ it.sortOrder }, { it.name.lowercase() }))
        }.distinctUntilChanged()
}

class ReorderSensorsUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    suspend operator fun invoke(idsInOrder: List<String>) {
        if (idsInOrder.isNotEmpty()) repository.reorder(idsInOrder)
    }
}

class ObserveSavedCamerasUseCase @Inject constructor(
    private val cameras: CameraRepository,
) {
    operator fun invoke(): Flow<List<Camera>> =
        cameras.cameras.map { list -> list.sortedBy { it.name.lowercase() } }.distinctUntilChanged()
}

class AddSensorUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    /** Returns null when [name] is blank. [area] blank becomes null. */
    suspend operator fun invoke(name: String, area: String?): Sensor? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return repository.add(trimmed, area?.trim()?.ifEmpty { null })
    }
}

class UpdateSensorUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    /** Does nothing when [name] is blank. */
    suspend operator fun invoke(id: String, name: String, area: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        repository.update(id, trimmed, area?.trim()?.ifEmpty { null })
    }
}

class RemoveSensorUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val tests: SensorTestControls,
) {
    suspend operator fun invoke(id: String) {
        tests.cancel(id)
        repository.remove(id)
    }
}

class SetSensorCamerasUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    suspend operator fun invoke(id: String, cameraIds: Set<String>) {
        repository.setLinks(id, cameraIds)
    }
}

class SetSensorHighlightUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    suspend operator fun invoke(id: String, enabled: Boolean) = repository.setHighlight(id, enabled)
}

class SetSensorAlarmStyleUseCase @Inject constructor(
    private val repository: SensorRepository,
) {
    suspend operator fun invoke(id: String, style: AlarmStyle) = repository.setAlarmStyle(id, style)
}

class StartSensorTestUseCase @Inject constructor(
    private val tests: SensorTestControls,
) {
    operator fun invoke(sensorId: String) = tests.start(sensorId)
}

class CancelSensorTestUseCase @Inject constructor(
    private val tests: SensorTestControls,
) {
    operator fun invoke(sensorId: String) = tests.cancel(sensorId)
}

class ObserveSensorTestsUseCase @Inject constructor(
    private val tests: SensorTestControls,
) {
    operator fun invoke(): Flow<Map<String, Instant>> = tests.pending
}

class ObserveRingingAlarmUseCase @Inject constructor(
    private val store: RingingAlarmStore,
) {
    operator fun invoke(): Flow<RingingAlarm?> = store.current
}

class DismissAlarmUseCase @Inject constructor(
    private val store: RingingAlarmStore,
    private val announcer: AlarmAnnouncer,
) {
    /** Stops the sound and vibration. Cameras that the alarm started stay on the wall. */
    operator fun invoke() {
        announcer.stop()
        store.dismiss()
    }
}

class ResetLiveAlarmUseCase @Inject constructor(
    private val cameras: ActiveCamerasRepository,
    private val dismissAlarm: DismissAlarmUseCase,
) {
    /** Stops the ring and removes the alarm border. The cameras stay on the wall. */
    suspend operator fun invoke() {
        dismissAlarm()
        cameras.clearAlarm()
    }
}
