// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.camera

import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.model.StreamRequest
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Saved cameras (Room in direct-LAN mode, server cache later). */
interface CameraRepository {
    val cameras: Flow<List<Camera>>

    suspend fun get(id: String): Camera?

    suspend fun add(info: CameraInfo, name: String, credentials: CameraCredentials?): Camera

    suspend fun rename(id: String, name: String)

    suspend fun remove(id: String)

    suspend fun credentials(id: String): CameraCredentials?
}

/** Cameras currently on the live wall, alarm and manual (spec D7). */
interface ActiveCamerasRepository {
    val active: Flow<List<ActiveCamera>>

    suspend fun start(ids: List<String>)

    suspend fun stop(id: String)

    suspend fun refresh()
}

/**
 * Turns a camera id into an openable stream. `DirectLanCameraSource` today,
 * `ServerCameraSource` (go2rtc / WebRTC) later. Screens never know which one is used.
 */
interface CameraSource {
    suspend fun stream(cameraId: String, quality: StreamQuality): AppResult<StreamRequest>
}

/** Finds cameras on the local network. Emits each device once per scan. */
interface CameraDiscovery {
    fun scan(timeout: Duration = 6.seconds): Flow<DiscoveredDevice>
}

/** Tests a target and returns working stream URIs (camera plan section 3). */
interface CameraConnector {
    suspend fun connect(target: CameraTarget, credentials: CameraCredentials?): AppResult<CameraInfo>
}

interface NetworkMonitor {
    val mode: Flow<NetworkMode>
}
