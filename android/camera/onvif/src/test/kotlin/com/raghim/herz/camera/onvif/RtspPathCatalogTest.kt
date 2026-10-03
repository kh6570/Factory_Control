// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtspPathCatalogTest {

    @Test
    fun `without hints the most common brand comes first and generic paths last`() {
        val candidates = RtspPathCatalog.candidates()

        assertEquals(StreamPaths("/Streaming/Channels/101", "/Streaming/Channels/102"), candidates.first())
        assertEquals(StreamPaths("/", null), candidates.last())
    }

    @Test
    fun `manufacturer hint moves that brand to the front, case-insensitive`() {
        val candidates = RtspPathCatalog.candidates("AMCREST", null, null)

        assertEquals(
            StreamPaths("/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1"),
            candidates.first(),
        )
    }

    @Test
    fun `name and model hints work too`() {
        assertEquals("/h264Preview_01_main", RtspPathCatalog.candidates(null, "Reolink Garage", null)[0].main)
        assertEquals("/Preview_01_main", RtspPathCatalog.candidates(null, "Reolink Garage", null)[1].main)
        assertEquals("/Streaming/Channels/101", RtspPathCatalog.candidates(null, null, "DS-2CD2143G0-I")[0].main)
        assertEquals("/stream1", RtspPathCatalog.candidates("TP-Link", "Tapo C200", null)[0].main)
        assertEquals("/profile2/media.smp", RtspPathCatalog.candidates("Hanwha Vision", null, null)[0].main)
        assertEquals(StreamPaths("/axis-media/media.amp", null), RtspPathCatalog.candidates("AXIS", null, null)[0])
    }

    @Test
    fun `hints only reorder, every path is still tried exactly once`() {
        val plain = RtspPathCatalog.candidates()
        val hinted = RtspPathCatalog.candidates("Uniview", null, null)

        assertEquals("/media/video1", hinted.first().main)
        assertEquals(plain.toSet(), hinted.toSet())
        assertEquals(hinted.size, hinted.distinct().size)
    }

    @Test
    fun `unknown hint keeps the default order`() {
        assertEquals(RtspPathCatalog.candidates(), RtspPathCatalog.candidates("Acme", "Front door", "X1"))
    }

    @Test
    fun `catalog contains the generic fallbacks`() {
        val mains = RtspPathCatalog.candidates().map { it.main }

        assertTrue(mains.containsAll(listOf("/live/ch00_0", "/11", "/ch0_0.h264", "/onvif1", "/live", "/stream", "/")))
        assertTrue(mains.containsAll(listOf("/h264/ch1/main/av_stream", "/media/video1", "/stream1")))
    }
}
