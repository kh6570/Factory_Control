// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.camera

import app.cash.turbine.test
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration

class CameraUseCasesTest {

    private val t0 = Instant.parse("2026-01-01T10:00:00Z")

    private fun active(id: String, source: SessionSource, seconds: Long) = ActiveCamera(
        cameraId = id, name = id, source = source, state = StreamState.LIVE,
        startedBy = null, startedAt = t0.plusSeconds(seconds),
    )

    private fun camera(id: String, name: String) = Camera(
        id = id, name = name, host = "10.0.0.$id", mainStreamUri = "rtsp://x/$id",
        subStreamUri = null, addedAt = t0,
    )

    @Test
    fun wallSortsNewestAlarmFirstThenManualCamerasByStartTime() {
        val sorted = listOf(
            active("m2", SessionSource.MANUAL, 2),
            active("a2", SessionSource.ALARM, 5),
            active("m1", SessionSource.MANUAL, 1),
            active("a1", SessionSource.ALARM, 3),
        ).sortedForWall()

        assertEquals(listOf("a2", "a1", "m1", "m2"), sorted.map { it.cameraId })
    }

    @Test
    fun wallUsesTheSavedCameraOrderInsideEachGroup() {
        val order = mapOf("a1" to 0, "m2" to 1, "a2" to 2, "m1" to 3)
        val sorted = listOf(
            active("m2", SessionSource.MANUAL, 2),
            active("a2", SessionSource.ALARM, 5),
            active("m1", SessionSource.MANUAL, 1),
            active("a1", SessionSource.ALARM, 3),
        ).sortedForWall(order)

        assertEquals(listOf("a1", "a2", "m2", "m1"), sorted.map { it.cameraId })
    }

    @Test
    fun overviewsMarkActiveCamerasAndSortByName() = runTest {
        val cameras = InMemoryCameras(listOf(camera("1", "yard"), camera("2", "Gate")))
        val active = InMemoryActive(listOf(active("1", SessionSource.MANUAL, 0)))

        ObserveCameraOverviewsUseCase(cameras, active)().test {
            val items = awaitItem()
            assertEquals(listOf("Gate", "yard"), items.map { it.camera.name })
            assertEquals(listOf(false, true), items.map { it.isActive })
        }
    }

    @Test
    fun addCameraSavesAndStartsWithFallbackName() = runTest {
        val cameras = InMemoryCameras()
        val active = InMemoryActive()
        val info = CameraInfo(host = "10.0.0.9", mainStreamUri = "rtsp://10.0.0.9/main", subStreamUri = null)

        val saved = AddCameraUseCase(cameras, active)(info, "  ", CameraCredentials("", ""))

        assertEquals("10.0.0.9", saved.name)
        assertNull(cameras.savedCredentials[saved.id])
        assertEquals(listOf(saved.id), active.state.value.map { it.cameraId })
    }

    @Test
    fun removeCameraStopsItFirst() = runTest {
        val cameras = InMemoryCameras(listOf(camera("1", "a")))
        val active = InMemoryActive(listOf(active("1", SessionSource.MANUAL, 0)))

        RemoveCameraUseCase(cameras, active)("1")

        assertTrue(active.state.value.isEmpty())
        assertTrue(cameras.state.value.isEmpty())
    }

    @Test
    fun scanMergesDuplicateHostsPreferringOnvifDetails() = runTest {
        val portScan = DiscoveredDevice(host = "192.168.1.20", method = DiscoveryMethod.PORT_SCAN)
        val onvif = DiscoveredDevice(
            host = "192.168.1.20", method = DiscoveryMethod.ONVIF,
            manufacturer = "Hikvision", onvifServiceUri = "http://192.168.1.20/onvif/device_service",
        )
        val other = DiscoveredDevice(host = "192.168.1.3", method = DiscoveryMethod.PORT_SCAN)
        val discovery = object : CameraDiscovery {
            override fun scan(timeout: Duration): Flow<DiscoveredDevice> = listOf(portScan, other, onvif).asFlow()
        }

        val result = ScanForCamerasUseCase(discovery)().last()

        assertEquals(listOf("192.168.1.3", "192.168.1.20"), result.map { it.host })
        assertEquals(DiscoveryMethod.ONVIF, result[1].method)
        assertEquals("Hikvision", result[1].manufacturer)
    }

    @Test
    fun blankUsernameIsSentAsNoCredentials() = runTest {
        var received: CameraCredentials? = CameraCredentials("x", "y")
        val connector = object : CameraConnector {
            override suspend fun connect(target: CameraTarget, credentials: CameraCredentials?): AppResult<CameraInfo> {
                received = credentials
                return AppResult.Success(CameraInfo("h", "rtsp://h/1", null))
            }
        }

        TestCameraConnectionUseCase(connector)(CameraTarget.Host("h"), CameraCredentials(" ", "p"))

        assertNull(received)
    }

    @Test
    fun liveBudgetFollowsNetworkMode() {
        assertEquals(9, liveBudget(NetworkMode.LAN))
        assertEquals(4, liveBudget(NetworkMode.CELLULAR))
        assertEquals(0, liveBudget(NetworkMode.OFFLINE))
    }

    private class InMemoryCameras(initial: List<Camera> = emptyList()) : CameraRepository {
        val state = MutableStateFlow(initial)
        val savedCredentials = mutableMapOf<String, CameraCredentials>()
        override val cameras: Flow<List<Camera>> = state
        override suspend fun get(id: String) = state.value.firstOrNull { it.id == id }
        override suspend fun add(info: CameraInfo, name: String, credentials: CameraCredentials?): Camera {
            val c = Camera("n${state.value.size}", name, info.host, info.mainStreamUri, info.subStreamUri, addedAt = Instant.EPOCH)
            state.update { it + c }
            credentials?.let { savedCredentials[c.id] = it }
            return c
        }
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun remove(id: String) = state.update { l -> l.filterNot { it.id == id } }
        override suspend fun reorder(idsInOrder: List<String>) = Unit
        override suspend fun credentials(id: String) = savedCredentials[id]
    }

    private inner class InMemoryActive(initial: List<ActiveCamera> = emptyList()) : ActiveCamerasRepository {
        val state = MutableStateFlow(initial)
        override val active: Flow<List<ActiveCamera>> = state
        override suspend fun start(ids: List<String>) =
            state.update { it + ids.map { id -> active(id, SessionSource.MANUAL, 0) } }
        override suspend fun raiseAlarm(cameraIds: List<String>, alarmId: String, highlight: Boolean) = Unit
        override suspend fun clearAlarm() = Unit
        override suspend fun stop(id: String) = state.update { l -> l.filterNot { it.cameraId == id } }
        override suspend fun refresh() = Unit
    }
}
