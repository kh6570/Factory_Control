// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.model

/** A device found on the local network that may be a camera. */
data class DiscoveredDevice(
    val host: String,
    val method: DiscoveryMethod,
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    /** ONVIF device service address (XAddr), when found by WS-Discovery. */
    val onvifServiceUri: String? = null,
    val rtspPort: Int = DEFAULT_RTSP_PORT,
) {
    val displayName: String
        get() = name ?: listOfNotNull(manufacturer, model).joinToString(" ").ifBlank { host }

    companion object {
        const val DEFAULT_RTSP_PORT = 554
    }
}

enum class DiscoveryMethod { ONVIF, PORT_SCAN }

/** Where to connect when adding a camera. */
sealed interface CameraTarget {
    data class Device(val device: DiscoveredDevice) : CameraTarget

    data class Host(
        val host: String,
        val rtspPort: Int = DiscoveredDevice.DEFAULT_RTSP_PORT,
    ) : CameraTarget

    data class RtspUrl(
        val mainUri: String,
        val subUri: String? = null,
    ) : CameraTarget
}

/** Result of a successful connection test: working stream URIs without credentials. */
data class CameraInfo(
    val host: String,
    val mainStreamUri: String,
    val subStreamUri: String?,
    val manufacturer: String? = null,
    val model: String? = null,
    val suggestedName: String? = null,
)
