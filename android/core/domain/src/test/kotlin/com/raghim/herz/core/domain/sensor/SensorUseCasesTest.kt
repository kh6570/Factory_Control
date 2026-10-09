// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.sensor

import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.RingingAlarm
import com.raghim.herz.core.model.Sensor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SensorUseCasesTest {
    private val repository = MemorySensors()
    private val tests = MemoryTests()
    private val ringing = MemoryRinging()
    private val announcer = MemoryAnnouncer()

    @Test
    fun addTrimsTheNameAndDropsABlankArea() = runTest {
        val sensor = AddSensorUseCase(repository)("  Gate  ", "  ")

        assertEquals("Gate", sensor?.name)
        assertNull(sensor?.area)
        assertEquals(listOf("Gate"), ObserveSensorsUseCase(repository)().first().map { it.name })
    }

    @Test
    fun sensorsAreListedByName() = runTest {
        repository.add("Yard", null)
        repository.add("Gate", null)

        assertEquals(listOf("Gate", "Yard"), ObserveSensorsUseCase(repository)().first().map { it.name })
    }

    @Test
    fun blankNameIsNotSaved() = runTest {
        assertNull(AddSensorUseCase(repository)("   ", "Yard"))
        assertTrue(repository.state.value.isEmpty())
        UpdateSensorUseCase(repository)("missing", "  ", null)
        assertTrue(repository.state.value.isEmpty())
    }

    @Test
    fun removeCancelsARunningTest() = runTest {
        repository.add("Gate", null)
        val id = repository.state.value.single().id
        tests.start(id)

        RemoveSensorUseCase(repository, tests)(id)

        assertTrue(repository.state.value.isEmpty())
        assertEquals(listOf(id), tests.cancelled)
    }

    @Test
    fun linksHighlightAndSoundAreStoredOnThatSensor() = runTest {
        val sensor = repository.add("Gate", "North")
        SetSensorCamerasUseCase(repository)(sensor.id, setOf("cam-b", "cam-a"))
        SetSensorHighlightUseCase(repository)(sensor.id, false)
        SetSensorAlarmStyleUseCase(repository)(sensor.id, AlarmStyle.VibrationOnly)

        val saved = repository.state.value.single()
        assertEquals(setOf("cam-a", "cam-b"), saved.linkedCameraIds)
        assertEquals(false, saved.highlightOnAlarm)
        assertEquals(AlarmStyle.VibrationOnly, saved.alarmStyle)

        SetSensorAlarmStyleUseCase(repository)(sensor.id, AlarmStyle.Sound(AlarmSound.RINGTONE))
        assertEquals(AlarmStyle.Sound(AlarmSound.RINGTONE), repository.state.value.single().alarmStyle)
    }

    @Test
    fun dismissStopsTheSoundAndClearsTheRingWithoutACameraCall() {
        ringing.show(RingingAlarm("s", "Gate", AlarmStyle.VibrationOnly))

        DismissAlarmUseCase(ringing, announcer)()

        assertNull(ringing.current.value)
        assertEquals(1, announcer.stopped)
    }

    private class MemorySensors : SensorRepository {
        val state = MutableStateFlow<List<Sensor>>(emptyList())
        private var next = 1
        override val sensors: Flow<List<Sensor>> = state
        override suspend fun get(id: String) = state.value.firstOrNull { it.id == id }
        override suspend fun add(name: String, area: String?): Sensor {
            val sensor = Sensor("s${next++}", name, area, addedAt = Instant.EPOCH)
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
        override suspend fun reorder(idsInOrder: List<String>) = Unit
    }

    private class MemoryTests : SensorTestControls {
        val cancelled = mutableListOf<String>()
        private val state = MutableStateFlow<Map<String, Instant>>(emptyMap())
        override val pending: StateFlow<Map<String, Instant>> = state
        override fun start(sensorId: String) = Unit
        override fun cancel(sensorId: String) {
            cancelled += sensorId
        }
    }

    private class MemoryRinging : RingingAlarmStore {
        private val state = MutableStateFlow<RingingAlarm?>(null)
        override val current: StateFlow<RingingAlarm?> = state
        override fun show(alarm: RingingAlarm) {
            state.value = alarm
        }
        override fun dismiss() {
            state.value = null
        }
    }

    private class MemoryAnnouncer : AlarmAnnouncer {
        var stopped = 0
        override fun start(alarm: RingingAlarm) = Unit
        override fun stop() {
            stopped++
        }
    }
}
