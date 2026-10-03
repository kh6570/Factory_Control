// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ReconnectBackoffTest {

    @Test
    fun `doubles from two seconds and caps at thirty`() {
        val backoff = ReconnectBackoff()
        val delays = List(7) { backoff.next() }
        assertEquals(
            listOf(2.seconds, 4.seconds, 8.seconds, 16.seconds, 30.seconds, 30.seconds, 30.seconds),
            delays,
        )
    }

    @Test
    fun `reset starts again from two seconds`() {
        val backoff = ReconnectBackoff()
        repeat(3) { backoff.next() }
        backoff.reset()
        assertEquals(2.seconds, backoff.next())
        assertEquals(4.seconds, backoff.next())
    }
}
