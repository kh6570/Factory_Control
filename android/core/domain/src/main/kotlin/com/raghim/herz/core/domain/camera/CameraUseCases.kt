// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.domain.camera

import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.CameraOverview
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.SessionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Alarm cameras first, then manual cameras in start order (system design 6.3). */
fun List<ActiveCamera>.sortedForWall(): List<ActiveCamera> =
    sortedWith(compareBy<ActiveCamera> { it.source != SessionSource.ALARM }.thenBy { it.startedAt })

class ObserveActiveCamerasUseCase @Inject constructor(
    private val repository: ActiveCamerasRepository,
) {
    operator fun invoke(): Flow<List<ActiveCamera>> =
        repository.active.map { it.sortedForWall() }.distinctUntilChanged()
}

class ObserveCameraOverviewsUseCase @Inject constructor(
    private val cameras: CameraRepository,
    private val active: ActiveCamerasRepository,
) {
    operator fun invoke(): Flow<List<CameraOverview>> =
        combine(cameras.cameras, active.active) { all, live ->
            val liveIds = live.mapTo(HashSet()) { it.cameraId }
            all.sortedBy { it.name.lowercase() }.map { CameraOverview(it, it.id in liveIds) }
        }.distinctUntilChanged()
}

class StartCamerasUseCase @Inject constructor(
    private val repository: ActiveCamerasRepository,
) {
    suspend operator fun invoke(ids: List<String>) {
        if (ids.isNotEmpty()) repository.start(ids.distinct())
    }
}

class StopCameraUseCase @Inject constructor(
    private val repository: ActiveCamerasRepository,
) {
    suspend operator fun invoke(id: String) = repository.stop(id)
}

class TestCameraConnectionUseCase @Inject constructor(
    private val connector: CameraConnector,
) {
    suspend operator fun invoke(target: CameraTarget, credentials: CameraCredentials?): AppResult<CameraInfo> =
        connector.connect(target, credentials?.takeIf { it.username.isNotBlank() })
}

/** Saves a tested camera and, by default, puts it on the live wall. */
class AddCameraUseCase @Inject constructor(
    private val cameras: CameraRepository,
    private val active: ActiveCamerasRepository,
) {
    suspend operator fun invoke(
        info: CameraInfo,
        name: String,
        credentials: CameraCredentials?,
        startNow: Boolean = true,
    ): Camera {
        val cleanName = name.trim().ifBlank { info.suggestedName ?: info.host }
        val camera = cameras.add(info, cleanName, credentials?.takeIf { it.username.isNotBlank() })
        if (startNow) active.start(listOf(camera.id))
        return camera
    }
}

class RenameCameraUseCase @Inject constructor(
    private val cameras: CameraRepository,
) {
    suspend operator fun invoke(id: String, name: String) {
        name.trim().takeIf { it.isNotEmpty() }?.let { cameras.rename(id, it) }
    }
}

class RemoveCameraUseCase @Inject constructor(
    private val cameras: CameraRepository,
    private val active: ActiveCamerasRepository,
) {
    suspend operator fun invoke(id: String) {
        active.stop(id)
        cameras.remove(id)
    }
}

/**
 * Runs one discovery scan and emits the growing, de-duplicated list of devices.
 * ONVIF details win over a bare port-scan hit for the same host.
 */
class ScanForCamerasUseCase @Inject constructor(
    private val discovery: CameraDiscovery,
) {
    operator fun invoke(timeout: Duration = 6.seconds): Flow<List<DiscoveredDevice>> =
        discovery.scan(timeout)
            .runningFold(emptyMap<String, DiscoveredDevice>()) { acc, device ->
                val existing = acc[device.host]
                acc + (device.host to if (existing == null) device else existing.mergedWith(device))
            }
            .map { byHost -> byHost.values.sortedWith(compareBy(::ipSortKey)) }
            .distinctUntilChanged()

    private fun DiscoveredDevice.mergedWith(other: DiscoveredDevice): DiscoveredDevice {
        val (primary, secondary) = if (other.method == DiscoveryMethod.ONVIF) other to this else this to other
        return primary.copy(
            name = primary.name ?: secondary.name,
            manufacturer = primary.manufacturer ?: secondary.manufacturer,
            model = primary.model ?: secondary.model,
            onvifServiceUri = primary.onvifServiceUri ?: secondary.onvifServiceUri,
        )
    }

    private fun ipSortKey(device: DiscoveredDevice): String =
        device.host.split('.').joinToString(".") { it.padStart(3, '0') }
}

class ObserveNetworkModeUseCase @Inject constructor(
    private val monitor: NetworkMonitor,
) {
    operator fun invoke(): Flow<NetworkMode> = monitor.mode.distinctUntilChanged()
}

/** How many tiles may decode live video at once (spec D9). The rest show a placeholder. */
data class LiveTileLimits(
    val wifi: Int = 9,
    val remote: Int = 6,
    val cellular: Int = 4,
)

fun liveBudget(mode: NetworkMode, limits: LiveTileLimits = LiveTileLimits()): Int = when (mode) {
    NetworkMode.LAN -> limits.wifi
    NetworkMode.REMOTE_VPN -> limits.remote
    NetworkMode.CELLULAR -> limits.cellular
    NetworkMode.OFFLINE -> 0
}
