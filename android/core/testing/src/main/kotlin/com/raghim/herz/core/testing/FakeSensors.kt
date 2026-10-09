// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.testing

import com.raghim.herz.core.domain.sensor.AlarmAnnouncer
import com.raghim.herz.core.domain.sensor.RingingAlarmStore
import com.raghim.herz.core.domain.sensor.SensorRepository
import com.raghim.herz.core.domain.sensor.SensorSignalSink
import com.raghim.herz.core.domain.sensor.SensorSignalSource
import com.raghim.herz.core.domain.sensor.SensorTestControls
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.RingingAlarm
import com.raghim.herz.core.model.Sensor
import com.raghim.herz.core.model.SensorSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

object TestSensors {
    fun sensor(
        n: Int,
        cameras: Set<String> = emptySet(),
        highlight: Boolean = true,
        style: AlarmStyle = AlarmStyle.Sound(AlarmSound.ALARM),
    ) = Sensor(
        id = "sensor$n",
        name = "Sensor $n",
        area = "Area $n",
        linkedCameraIds = cameras,
        highlightOnAlarm = highlight,
        alarmStyle = style,
        addedAt = Instant.EPOCH.plusSeconds(n.toLong()),
    )
}

class FakeSensorRepository(initial: List<Sensor> = emptyList()) : SensorRepository {
    val state = MutableStateFlow(initial)
    private var nextId = initial.size + 1

    override val sensors: Flow<List<Sensor>> = state

    override suspend fun get(id: String): Sensor? = state.value.firstOrNull { it.id == id }

    override suspend fun add(name: String, area: String?): Sensor {
        val sensor = Sensor(
            id = "sensor${nextId++}",
            name = name,
            area = area,
            addedAt = Instant.EPOCH,
        )
        state.update { it + sensor }
        return sensor
    }

    override suspend fun update(id: String, name: String, area: String?) {
        state.update { list -> list.map { if (it.id == id) it.copy(name = name, area = area) else it } }
    }

    override suspend fun setLinks(id: String, cameraIds: Set<String>) {
        state.update { list -> list.map { if (it.id == id) it.copy(linkedCameraIds = cameraIds) else it } }
    }

    override suspend fun setHighlight(id: String, enabled: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(highlightOnAlarm = enabled) else it } }
    }

    override suspend fun setAlarmStyle(id: String, style: AlarmStyle) {
        state.update { list -> list.map { if (it.id == id) it.copy(alarmStyle = style) else it } }
    }

    override suspend fun remove(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun reorder(idsInOrder: List<String>) {
        val rank = idsInOrder.withIndex().associate { it.value to it.index }
        state.update { list ->
            list.map { sensor -> rank[sensor.id]?.let { sensor.copy(sortOrder = it) } ?: sensor }
                .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
        }
    }
}

class FakeSensorSignalBus : SensorSignalSource, SensorSignalSink {
    private val flow = MutableSharedFlow<SensorSignal>(extraBufferCapacity = 16)
    val emitted = mutableListOf<SensorSignal>()

    override val signals: Flow<SensorSignal> = flow

    override suspend fun emit(signal: SensorSignal) {
        emitted += signal
        flow.emit(signal)
    }
}

class FakeSensorTestControls : SensorTestControls {
    private val state = MutableStateFlow<Map<String, Instant>>(emptyMap())
    val started = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override val pending: StateFlow<Map<String, Instant>> = state.asStateFlow()

    override fun start(sensorId: String) {
        started += sensorId
    }

    override fun cancel(sensorId: String) {
        cancelled += sensorId
        state.update { it - sensorId }
    }
}

class FakeAlarmAnnouncer : AlarmAnnouncer {
    var started = 0
        private set
    var stopped = 0
        private set
    var last: RingingAlarm? = null
        private set

    override fun start(alarm: RingingAlarm) {
        started++
        last = alarm
    }

    override fun stop() {
        stopped++
    }
}

class FakeRingingAlarmStore : RingingAlarmStore {
    private val state = MutableStateFlow<RingingAlarm?>(null)

    override val current: StateFlow<RingingAlarm?> = state.asStateFlow()

    override fun show(alarm: RingingAlarm) {
        state.value = alarm
    }

    override fun dismiss() {
        state.value = null
    }
}
