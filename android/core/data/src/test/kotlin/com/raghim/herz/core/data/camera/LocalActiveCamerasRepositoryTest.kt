// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalActiveCamerasRepositoryTest {

    private val dao = FakeActiveSessionDao(mapOf("a" to "Gate", "b" to "Yard", "c" to "Dock"))
    private val clock = FakeAppClock()

    private fun TestScope.repository() =
        LocalActiveCamerasRepository(dao, clock, StandardTestDispatcher(testScheduler))

    @Test
    fun startMapsRowsToLiveManualSessions() = runTest {
        val repository = repository()
        repository.start(listOf("a"))

        val camera = repository.active.first().single()
        assertEquals("a", camera.cameraId)
        assertEquals("Gate", camera.name)
        assertEquals(SessionSource.MANUAL, camera.source)
        assertEquals(StreamState.LIVE, camera.state)
        assertNull(camera.startedBy)
        assertEquals(clock.time.toEpochMilli(), camera.startedAt.toEpochMilli())
    }

    @Test
    fun startKeepsRequestOrderWithinOneCall() = runTest {
        val repository = repository()
        repository.start(listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), repository.active.first().map { it.cameraId })
    }

    @Test
    fun startIgnoresAlreadyActiveCamerasAndKeepsTheirStartTime() = runTest {
        val repository = repository()
        repository.start(listOf("a", "b"))
        val firstStartOfB = repository.active.first().first { it.cameraId == "b" }.startedAt

        clock.time = clock.time.plusSeconds(60)
        repository.start(listOf("b", "c", "c"))

        val active = repository.active.first()
        assertEquals(listOf("a", "b", "c"), active.map { it.cameraId })
        assertEquals(firstStartOfB, active.first { it.cameraId == "b" }.startedAt)
    }

    @Test
    fun startSkipsUnknownCameras() = runTest {
        val repository = repository()
        repository.start(listOf("a", "missing"))

        assertEquals(listOf("a"), repository.active.first().map { it.cameraId })
    }

    @Test
    fun startWithEmptyListDoesNothing() = runTest {
        val repository = repository()
        repository.start(emptyList())

        assertTrue(repository.active.first().isEmpty())
    }

    @Test
    fun raiseAlarmPromotesAManualCameraAndAddsTheOthers() = runTest {
        val repository = repository()
        repository.start(listOf("a"))
        clock.time = clock.time.plusSeconds(30)

        repository.raiseAlarm(listOf("a", "b", "missing"), alarmId = "alarm-1", highlight = true)

        val active = repository.active.first().associateBy { it.cameraId }
        assertEquals(setOf("a", "b"), active.keys)
        assertEquals(SessionSource.ALARM, active.getValue("a").source)
        assertEquals(SessionSource.ALARM, active.getValue("b").source)
        assertEquals("alarm-1", active.getValue("a").alarmId)
        assertTrue(active.getValue("a").highlightAlarm)
        assertTrue(active.getValue("b").highlightAlarm)
        assertEquals(clock.time.toEpochMilli(), active.getValue("a").startedAt.toEpochMilli())
    }

    @Test
    fun raiseAlarmWithNoKnownCamerasLeavesTheWallAlone() = runTest {
        val repository = repository()
        repository.start(listOf("a"))

        repository.raiseAlarm(listOf("missing"), alarmId = "alarm-1", highlight = true)

        val camera = repository.active.first().single()
        assertEquals(SessionSource.MANUAL, camera.source)
        assertNull(camera.alarmId)
    }

    @Test
    fun clearAlarmKeepsTheCamerasAndDropsTheAlarmMark() = runTest {
        val repository = repository()
        repository.start(listOf("a"))
        repository.raiseAlarm(listOf("a", "b"), alarmId = "alarm-1", highlight = true)

        repository.clearAlarm()

        val active = repository.active.first()
        assertEquals(listOf("a", "b"), active.map { it.cameraId })
        assertTrue(active.all { it.source == SessionSource.MANUAL })
        assertTrue(active.all { it.alarmId == null && !it.highlightAlarm })
    }

    @Test
    fun stopRemovesOnlyThatCamera() = runTest {
        val repository = repository()
        repository.start(listOf("a", "b"))

        repository.stop("a")

        assertEquals(listOf("b"), repository.active.first().map { it.cameraId })
    }
}
