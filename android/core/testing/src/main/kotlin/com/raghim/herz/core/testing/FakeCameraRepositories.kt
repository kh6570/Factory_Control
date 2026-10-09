// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.testing

import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.domain.camera.ActiveCamerasRepository
import com.raghim.herz.core.domain.camera.CameraConnector
import com.raghim.herz.core.domain.camera.CameraDiscovery
import com.raghim.herz.core.domain.camera.CameraRepository
import com.raghim.herz.core.domain.camera.CameraSource
import com.raghim.herz.core.domain.camera.NetworkMonitor
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.model.StreamRequest
import com.raghim.herz.core.model.StreamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

class FakeCameraRepository(initial: List<Camera> = emptyList()) : CameraRepository {
    val state = MutableStateFlow(initial)
    val credentialsById = mutableMapOf<String, CameraCredentials>()
    private var nextId = 100

    override val cameras: Flow<List<Camera>> = state

    override suspend fun get(id: String): Camera? = state.value.firstOrNull { it.id == id }

    override suspend fun add(info: CameraInfo, name: String, credentials: CameraCredentials?): Camera {
        val camera = Camera(
            id = "cam${nextId++}",
            name = name,
            host = info.host,
            mainStreamUri = info.mainStreamUri,
            subStreamUri = info.subStreamUri,
            manufacturer = info.manufacturer,
            model = info.model,
            addedAt = TestCameras.epoch,
        )
        state.update { it + camera }
        credentials?.let { credentialsById[camera.id] = it }
        return camera
    }

    override suspend fun rename(id: String, name: String) {
        state.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    }

    override suspend fun remove(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
        credentialsById.remove(id)
    }

    override suspend fun reorder(idsInOrder: List<String>) {
        val rank = idsInOrder.withIndex().associate { it.value to it.index }
        state.update { list ->
            list.map { camera -> rank[camera.id]?.let { camera.copy(sortOrder = it) } ?: camera }
                .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
        }
    }

    override suspend fun credentials(id: String): CameraCredentials? = credentialsById[id]
}

class FakeActiveCamerasRepository(
    private val cameras: FakeCameraRepository? = null,
    initial: List<ActiveCamera> = emptyList(),
) : ActiveCamerasRepository {
    val state = MutableStateFlow(initial)
    var refreshCount = 0
        private set

    override val active: Flow<List<ActiveCamera>> = state

    override suspend fun start(ids: List<String>) {
        val known = cameras?.state?.value.orEmpty().associateBy { it.id }
        state.update { current ->
            val present = current.mapTo(HashSet()) { it.cameraId }
            current + ids.filterNot { it in present }.map { id ->
                ActiveCamera(
                    cameraId = id,
                    name = known[id]?.name ?: id,
                    source = SessionSource.MANUAL,
                    state = StreamState.LIVE,
                    startedBy = null,
                    startedAt = TestCameras.epoch.plusSeconds(current.size.toLong()),
                )
            }
        }
    }

    override suspend fun raiseAlarm(cameraIds: List<String>, alarmId: String, highlight: Boolean) {
        val known = cameras?.state?.value.orEmpty().associateBy { it.id }
        val now = TestCameras.epoch.plusSeconds(10_000)
        state.update { current ->
            val byId = current.associateBy { it.cameraId }.toMutableMap()
            cameraIds.distinct().forEach { id ->
                val existing = byId[id]
                byId[id] = ActiveCamera(
                    cameraId = id,
                    name = existing?.name ?: known[id]?.name ?: id,
                    source = SessionSource.ALARM,
                    state = StreamState.LIVE,
                    startedBy = existing?.startedBy,
                    startedAt = now,
                    alarmId = alarmId,
                    highlightAlarm = highlight,
                )
            }
            byId.values.toList()
        }
    }

    override suspend fun clearAlarm() {
        state.update { list ->
            list.map { camera ->
                if (camera.source == SessionSource.ALARM) {
                    camera.copy(source = SessionSource.MANUAL, alarmId = null, highlightAlarm = false)
                } else {
                    camera
                }
            }
        }
    }

    override suspend fun stop(id: String) {
        state.update { list -> list.filterNot { it.cameraId == id } }
    }

    override suspend fun refresh() {
        refreshCount++
    }
}

class FakeCameraSource : CameraSource {
    var result: (String, StreamQuality) -> AppResult<StreamRequest> = { id, quality ->
        AppResult.Success(StreamRequest("rtsp://test/$id/${quality.name.lowercase()}", null))
    }

    override suspend fun stream(cameraId: String, quality: StreamQuality): AppResult<StreamRequest> =
        result(cameraId, quality)
}

class FakeCameraDiscovery(var devices: List<DiscoveredDevice> = emptyList()) : CameraDiscovery {
    var scans = 0
        private set

    override fun scan(timeout: Duration): Flow<DiscoveredDevice> {
        scans++
        return devices.asFlow()
    }
}

class FakeCameraConnector : CameraConnector {
    var result: AppResult<CameraInfo> = AppResult.Failure(AppError.Unreachable)
    val calls = mutableListOf<Pair<CameraTarget, CameraCredentials?>>()

    override suspend fun connect(target: CameraTarget, credentials: CameraCredentials?): AppResult<CameraInfo> {
        calls += target to credentials
        return result
    }
}

class FakeNetworkMonitor(mode: NetworkMode = NetworkMode.LAN) : NetworkMonitor {
    val state = MutableStateFlow(mode)
    override val mode: Flow<NetworkMode> = state
}
