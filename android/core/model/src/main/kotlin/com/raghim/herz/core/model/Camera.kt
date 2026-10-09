// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.model

import java.time.Instant

/**
 * A camera the user saved. Stream URIs never contain credentials; those are stored
 * encrypted and joined only when a player opens the stream.
 */
data class Camera(
    val id: String,
    val name: String,
    val host: String,
    val mainStreamUri: String,
    val subStreamUri: String?,
    val manufacturer: String? = null,
    val model: String? = null,
    val addedAt: Instant,
    /** Position in the Cameras list. Live uses this same order. */
    val sortOrder: Int = 0,
) {
    fun streamUri(quality: StreamQuality): String = when (quality) {
        StreamQuality.SUB -> subStreamUri ?: mainStreamUri
        StreamQuality.MAIN -> mainStreamUri
    }
}

/** A saved camera plus whether it is currently on the live wall. */
data class CameraOverview(
    val camera: Camera,
    val isActive: Boolean,
)

enum class StreamQuality { SUB, MAIN }

class CameraCredentials(
    val username: String,
    val password: String,
) {
    override fun equals(other: Any?): Boolean =
        other is CameraCredentials && other.username == username && other.password == password

    override fun hashCode(): Int = 31 * username.hashCode() + password.hashCode()

    override fun toString(): String = "CameraCredentials(username=$username, password=***)"
}

/** What a player needs to open one stream. */
class StreamRequest(
    val uri: String,
    val credentials: CameraCredentials?,
) {
    override fun equals(other: Any?): Boolean =
        other is StreamRequest && other.uri == uri && other.credentials == credentials

    override fun hashCode(): Int = 31 * uri.hashCode() + (credentials?.hashCode() ?: 0)

    override fun toString(): String = "StreamRequest(uri=$uri, credentials=${credentials?.let { "***" }})"
}
