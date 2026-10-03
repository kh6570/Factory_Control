// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.model.CameraCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class RtspProbeTest {
    private val probe = RtspProbe(connectTimeoutMs = 1_000, readTimeoutMs = 2_000)
    private val login = CameraCredentials("admin", "secret")
    private var server: FakeRtspServer? = null

    @After
    fun tearDown() {
        server?.close()
    }

    @Test
    fun `answers the digest challenge and returns the SDP`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/stream1")))
        val uri = "rtsp://${FakeRtspServer.HOST}:${server.port}/stream1"

        val result = describe(uri, login)

        assertTrue(result is ProbeResult.Ok)
        assertTrue((result as ProbeResult.Ok).sdp.contains("m=video"))
        assertEquals(listOf("DESCRIBE $uri RTSP/1.0", "DESCRIBE $uri RTSP/1.0"), server.requestLines.toList())
        assertTrue(server.authorizations.single().startsWith("Digest "))
    }

    @Test
    fun `reconnects when the camera closes the connection after the challenge`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/stream1"), closeAfterChallenge = true))

        val result = describe("rtsp://${FakeRtspServer.HOST}:${server.port}/stream1", login)

        assertTrue(result is ProbeResult.Ok)
    }

    @Test
    fun `missing or wrong credentials are unauthorized`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/stream1")))
        val uri = "rtsp://${FakeRtspServer.HOST}:${server.port}/stream1"

        assertEquals(ProbeResult.Unauthorized, describe(uri, credentials = null))
        assertEquals(ProbeResult.Unauthorized, describe(uri, CameraCredentials("admin", "wrong")))
    }

    @Test
    fun `unknown path and audio-only stream are not found`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/stream1"), audioOnlyPaths = setOf("/audio")))

        assertEquals(ProbeResult.NotFound, describe("rtsp://${FakeRtspServer.HOST}:${server.port}/nope", login))
        assertEquals(ProbeResult.NotFound, describe("rtsp://${FakeRtspServer.HOST}:${server.port}/audio", login))
    }

    @Test
    fun `closed port is unreachable`() = runTest {
        val port = ServerSocket(0, 50, InetAddress.getByName(FakeRtspServer.HOST)).use { it.localPort }

        assertEquals(ProbeResult.Unreachable, describe("rtsp://${FakeRtspServer.HOST}:$port/stream1", login))
    }

    private fun start(fake: FakeRtspServer): FakeRtspServer = fake.also { server = it }

    private suspend fun describe(uri: String, credentials: CameraCredentials?): ProbeResult =
        withContext(Dispatchers.IO) { probe.describe(uri, credentials) }
}
