// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.sensor

import com.raghim.herz.core.common.AppClock
import com.raghim.herz.core.domain.sensor.SensorLimits
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.SensorSignal
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.testing.FakeActiveCamerasRepository
import com.raghim.herz.core.testing.FakeAlarmAnnouncer
import com.raghim.herz.core.testing.FakeSensorRepository
import com.raghim.herz.core.testing.TestSensors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SensorAlarmTest {

    @Test
    fun cameraLinksAreCappedSortedAndSkipCommas() {
        val ids = (1..70).map { "c$it" }.toSet() + "bad,id"
        val encoded = ids.encodeCameraIds()

        assertEquals(SensorLimits.MAX_LINKED_CAMERAS, encoded.split(',').size)
        assertFalse(encoded.contains("bad"))
        assertEquals(encoded, encoded.split(',').sorted().joinToString(","))
    }

    @Test
    fun testTimerFiresOnceAfterTwentySecondsAndThenClears() = runTest {
        val bus = SensorSignalBus()
        val received = mutableListOf<SensorSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.signals.collect { received += it } }
        val timer = SensorTestTimer(bus, AppClock { Instant.EPOCH }, this)

        timer.start("gate")
        runCurrent()
        assertTrue(timer.pending.value.containsKey("gate"))

        advanceTimeBy(SensorLimits.TEST_DELAY.inWholeMilliseconds - 1)
        runCurrent()
        assertTrue(received.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(SensorSignal("gate")), received)
        assertTrue(timer.pending.value.isEmpty())
    }

    @Test
    fun startingAgainReplacesTheTimerSoItFiresOnce() = runTest {
        val bus = SensorSignalBus()
        val received = mutableListOf<SensorSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.signals.collect { received += it } }
        val timer = SensorTestTimer(bus, AppClock { Instant.EPOCH }, this)

        timer.start("gate")
        runCurrent()
        advanceTimeBy(5_000)
        timer.start("gate")
        runCurrent()
        advanceTimeBy(SensorLimits.TEST_DELAY.inWholeMilliseconds)
        runCurrent()

        assertEquals(listOf(SensorSignal("gate")), received)
    }

    @Test
    fun cancelStopsTheTimer() = runTest {
        val bus = SensorSignalBus()
        val received = mutableListOf<SensorSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.signals.collect { received += it } }
        val timer = SensorTestTimer(bus, AppClock { Instant.EPOCH }, this)

        timer.start("gate")
        runCurrent()
        timer.cancel("gate")
        advanceTimeBy(SensorLimits.TEST_DELAY.inWholeMilliseconds)
        runCurrent()

        assertTrue(received.isEmpty())
        assertTrue(timer.pending.value.isEmpty())
    }

    @Test
    fun pendingTimersStayCapped() = runTest {
        val timer = SensorTestTimer(SensorSignalBus(), AppClock { Instant.EPOCH }, this)

        repeat(SensorLimits.MAX_TEST_TIMERS + 5) { index -> timer.start("s$index") }

        assertEquals(SensorLimits.MAX_TEST_TIMERS, timer.pending.value.size)
    }

    @Test
    fun signalPutsLinkedCamerasOnTheWallAndRings() = runTest {
        val sensors = FakeSensorRepository(listOf(TestSensors.sensor(1, cameras = setOf("cam-a", "cam-b"))))
        val cameras = FakeActiveCamerasRepository()
        val announcer = FakeAlarmAnnouncer()
        val ringing = RingingAlarmSession()
        val bus = SensorSignalBus()
        AlarmCoordinator(bus, sensors, cameras, ringing, announcer, backgroundScope).start()
        runCurrent()

        bus.emit(SensorSignal("sensor1"))
        runCurrent()

        val wall = cameras.state.value.associateBy { it.cameraId }
        assertEquals(setOf("cam-a", "cam-b"), wall.keys)
        assertTrue(wall.values.all { it.source == SessionSource.ALARM && it.highlightAlarm })
        assertEquals("Sensor 1", ringing.current.value?.sensorName)
        assertEquals(1, announcer.started)
    }

    @Test
    fun signalForAMissingSensorDoesNothing() = runTest {
        val cameras = FakeActiveCamerasRepository()
        val announcer = FakeAlarmAnnouncer()
        val ringing = RingingAlarmSession()
        val bus = SensorSignalBus()
        AlarmCoordinator(
            bus,
            FakeSensorRepository(),
            cameras,
            ringing,
            announcer,
            backgroundScope,
        ).start()
        runCurrent()

        bus.emit(SensorSignal("gone"))
        runCurrent()

        assertTrue(cameras.state.value.isEmpty())
        assertNull(ringing.current.value)
        assertEquals(0, announcer.started)
    }

    @Test
    fun sensorWithNoCamerasStillRings() = runTest {
        val sensors = FakeSensorRepository(
            listOf(TestSensors.sensor(1, cameras = emptySet(), style = AlarmStyle.VibrationOnly)),
        )
        val announcer = FakeAlarmAnnouncer()
        val ringing = RingingAlarmSession()
        val bus = SensorSignalBus()
        AlarmCoordinator(bus, sensors, FakeActiveCamerasRepository(), ringing, announcer, backgroundScope).start()
        runCurrent()

        bus.emit(SensorSignal("sensor1"))
        runCurrent()

        assertEquals(AlarmStyle.VibrationOnly, announcer.last?.style)
        assertEquals(AlarmStyle.VibrationOnly, ringing.current.value?.style)
    }
}
