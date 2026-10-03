// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.model.CameraCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtspUriTest {

    @Test
    fun `url without userinfo is kept as is`() {
        val uri = RtspUri.parse("rtsp://192.168.1.10:8554/cam/realmonitor?channel=1&subtype=0")

        assertEquals("rtsp://192.168.1.10:8554/cam/realmonitor?channel=1&subtype=0", uri.sanitized)
        assertNull(uri.credentials)
        assertEquals("192.168.1.10", uri.host)
        assertEquals(8554, uri.port)
    }

    @Test
    fun `percent encoded userinfo is extracted and removed`() {
        val uri = RtspUri.parse("rtsp://admin:p%40ss+word@192.168.1.10/Streaming/Channels/101")

        assertEquals("rtsp://192.168.1.10/Streaming/Channels/101", uri.sanitized)
        assertEquals(CameraCredentials("admin", "p@ss+word"), uri.credentials)
        assertEquals(554, uri.port)
    }

    @Test
    fun `raw passwords with at sign and slash are handled`() {
        val uri = RtspUri.parse("rtsp://admin:p@ss/1@10.0.0.5:554/stream1")

        assertEquals("rtsp://10.0.0.5:554/stream1", uri.sanitized)
        assertEquals(CameraCredentials("admin", "p@ss/1"), uri.credentials)
        assertEquals("10.0.0.5", uri.host)
        assertEquals(554, uri.port)
    }

    @Test
    fun `user without password`() {
        val uri = RtspUri.parse("rtsp://viewer@10.0.0.5/live")

        assertEquals("rtsp://10.0.0.5/live", uri.sanitized)
        assertEquals(CameraCredentials("viewer", ""), uri.credentials)
    }

    @Test
    fun `garbage has no host`() {
        val uri = RtspUri.parse("not a url")

        assertNull(uri.host)
        assertNull(uri.credentials)
    }

    @Test
    fun `rtspUrl omits the default port and brackets IPv6`() {
        assertEquals("rtsp://10.0.0.5/stream1", rtspUrl("10.0.0.5", 554, "/stream1"))
        assertEquals("rtsp://10.0.0.5:8554/stream1", rtspUrl("10.0.0.5", 8554, "stream1"))
        assertEquals("rtsp://[fe80::1]:8554/", rtspUrl("fe80::1", 8554, "/"))
    }
}
