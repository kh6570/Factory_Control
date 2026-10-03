// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Doubling delays (2 s, 4 s, 8 s, ...) capped at [max]. Not thread-safe. */
internal class ReconnectBackoff(
    private val initial: Duration = 2.seconds,
    private val max: Duration = 30.seconds,
) {
    private var next = initial

    fun next(): Duration {
        val current = next
        next = (current * 2).coerceAtMost(max)
        return current
    }

    fun reset() {
        next = initial
    }
}
