// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one player per active camera so maximize and back reuse the same connection
 * (spec D9). Main thread only.
 */
@Singleton
class PlayerPool @Inject constructor(
    private val factory: VideoPlayerFactory,
) {
    private val players = LinkedHashMap<String, VideoPlayer>()

    val size: Int get() = players.size

    fun get(cameraId: String): VideoPlayer =
        players.getOrPut(cameraId) { factory.create(cameraId) }

    fun peek(cameraId: String): VideoPlayer? = players[cameraId]

    /** Keeps only these cameras and releases the rest. */
    fun retain(cameraIds: Set<String>) {
        val stale = players.keys.filterNot { it in cameraIds }
        stale.forEach { players.remove(it)?.release() }
    }

    fun pauseAll() {
        players.values.forEach { it.pause() }
    }

    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
    }
}
