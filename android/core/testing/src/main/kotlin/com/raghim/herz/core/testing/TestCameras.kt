// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.testing

import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamState
import java.time.Instant

object TestCameras {
    val epoch: Instant = Instant.parse("2026-01-01T10:00:00Z")

    fun camera(index: Int, name: String = "Camera $index"): Camera = Camera(
        id = "cam%02d".format(index),
        name = name,
        host = "192.168.1.${10 + index}",
        mainStreamUri = "rtsp://192.168.1.${10 + index}:554/main",
        subStreamUri = "rtsp://192.168.1.${10 + index}:554/sub",
        manufacturer = "Test",
        model = "T-$index",
        addedAt = epoch.plusSeconds(index.toLong()),
    )

    fun active(
        camera: Camera,
        source: SessionSource = SessionSource.MANUAL,
        startedAt: Instant = camera.addedAt,
        state: StreamState = StreamState.LIVE,
    ): ActiveCamera = ActiveCamera(
        cameraId = camera.id,
        name = camera.name,
        source = source,
        state = state,
        startedBy = null,
        startedAt = startedAt,
    )

    fun info(index: Int): CameraInfo = CameraInfo(
        host = "192.168.1.${10 + index}",
        mainStreamUri = "rtsp://192.168.1.${10 + index}:554/main",
        subStreamUri = "rtsp://192.168.1.${10 + index}:554/sub",
        manufacturer = "Test",
        model = "T-$index",
        suggestedName = "Test T-$index",
    )

    fun device(
        index: Int,
        method: DiscoveryMethod = DiscoveryMethod.ONVIF,
    ): DiscoveredDevice = DiscoveredDevice(
        host = "192.168.1.${10 + index}",
        method = method,
        name = if (method == DiscoveryMethod.ONVIF) "Cam $index" else null,
        manufacturer = if (method == DiscoveryMethod.ONVIF) "Test" else null,
    )
}
