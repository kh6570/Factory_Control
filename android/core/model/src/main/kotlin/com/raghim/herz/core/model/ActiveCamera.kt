// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.model

import java.time.Instant

/** A camera that is currently on the live wall (system design 6.3). */
data class ActiveCamera(
    val cameraId: String,
    val name: String,
    val source: SessionSource,
    val state: StreamState,
    val startedBy: String?,
    val startedAt: Instant,
    val alarmId: String? = null,
)

enum class SessionSource { ALARM, MANUAL }

enum class StreamState { STARTING, LIVE, ERROR }

enum class NetworkMode { LAN, REMOTE_VPN, CELLULAR, OFFLINE }
