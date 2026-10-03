// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import com.raghim.herz.core.model.CameraCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class RtspUrisTest {

    @Test
    fun `null credentials leave uri unchanged`() {
        val uri = "rtsp://10.0.0.5:554/stream1"
        assertEquals(uri, RtspUris.withCredentials(uri, null))
    }

    @Test
    fun `inserts userinfo and keeps port path and query`() {
        val result = RtspUris.withCredentials(
            "rtsp://10.0.0.5:554/cam/realmonitor?channel=1&subtype=0",
            CameraCredentials("admin", "secret"),
        )
        assertEquals("rtsp://admin:secret@10.0.0.5:554/cam/realmonitor?channel=1&subtype=0", result)
    }

    @Test
    fun `encodes reserved characters in user and password`() {
        val result = RtspUris.withCredentials(
            "rtsp://cam.local/live",
            CameraCredentials("op@site", "p:a/s#s@w ord"),
        )
        assertEquals("rtsp://op%40site:p%3Aa%2Fs%23s%40w%20ord@cam.local/live", result)
    }

    @Test
    fun `works without path`() {
        val result = RtspUris.withCredentials("rtsp://10.0.0.5:8554", CameraCredentials("u", "p"))
        assertEquals("rtsp://u:p@10.0.0.5:8554", result)
    }

    @Test
    fun `existing userinfo is kept`() {
        val uri = "rtsp://other:pw@10.0.0.5/stream"
        assertEquals(uri, RtspUris.withCredentials(uri, CameraCredentials("admin", "secret")))
    }

    @Test
    fun `at sign in query does not count as userinfo`() {
        val result = RtspUris.withCredentials(
            "rtsp://10.0.0.5/stream?tag=a@b",
            CameraCredentials("u", "p"),
        )
        assertEquals("rtsp://u:p@10.0.0.5/stream?tag=a@b", result)
    }

    @Test
    fun `uri without authority is unchanged`() {
        assertEquals("not a uri", RtspUris.withCredentials("not a uri", CameraCredentials("u", "p")))
    }
}
