// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class RtspCameraConnectorTest {
    private val connector = RtspCameraConnector(Dispatchers.IO)
    private val login = CameraCredentials("admin", "secret")
    private var server: FakeRtspServer? = null

    @After
    fun tearDown() {
        server?.close()
    }

    @Test
    fun `host target finds the Dahua stream pair after other paths 404`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf(DAHUA_MAIN, DAHUA_SUB)))

        val result = connector.connect(CameraTarget.Host(FakeRtspServer.HOST, server.port), login)

        assertEquals(
            AppResult.Success(
                CameraInfo(
                    host = FakeRtspServer.HOST,
                    mainStreamUri = server.url(DAHUA_MAIN),
                    subStreamUri = server.url(DAHUA_SUB),
                    suggestedName = FakeRtspServer.HOST,
                ),
            ),
            result,
        )
        assertEquals("DESCRIBE ${server.url("/Streaming/Channels/101")} RTSP/1.0", server.requestLines.first())
    }

    @Test
    fun `device hint tries the brand path first and keeps device details`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf(DAHUA_MAIN)))
        val device = DiscoveredDevice(
            host = FakeRtspServer.HOST,
            method = DiscoveryMethod.ONVIF,
            manufacturer = "Amcrest",
            model = "IP5M-T1179E",
            rtspPort = server.port,
        )

        val result = connector.connect(CameraTarget.Device(device), login)

        assertEquals(
            AppResult.Success(
                CameraInfo(
                    host = FakeRtspServer.HOST,
                    mainStreamUri = server.url(DAHUA_MAIN),
                    subStreamUri = null,
                    manufacturer = "Amcrest",
                    model = "IP5M-T1179E",
                    suggestedName = "Amcrest IP5M-T1179E",
                ),
            ),
            result,
        )
        assertEquals("DESCRIBE ${server.url(DAHUA_MAIN)} RTSP/1.0", server.requestLines.first())
    }

    @Test
    fun `missing or wrong login is unauthorized`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf(DAHUA_MAIN)))
        val target = CameraTarget.Host(FakeRtspServer.HOST, server.port)

        assertEquals(AppResult.Failure(AppError.Unauthorized), connector.connect(target, credentials = null))
        assertEquals(
            AppResult.Failure(AppError.Unauthorized),
            connector.connect(target, CameraCredentials("admin", "wrong")),
        )
    }

    @Test
    fun `no known path gives no stream found`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/custom/path")))

        val result = connector.connect(CameraTarget.Host(FakeRtspServer.HOST, server.port), login)

        assertEquals(AppResult.Failure(AppError.NoStreamFound), result)
    }

    @Test
    fun `dead host fails fast as unreachable`() = runTest {
        val port = ServerSocket(0, 50, InetAddress.getByName(FakeRtspServer.HOST)).use { it.localPort }

        val result = connector.connect(CameraTarget.Host(FakeRtspServer.HOST, port), login)

        assertEquals(AppResult.Failure(AppError.Unreachable), result)
    }

    @Test
    fun `rtsp url with userinfo uses it as login and never sends or returns it`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/stream1")))
        val main = "rtsp://admin:secret@${FakeRtspServer.HOST}:${server.port}/stream1"
        val sub = "rtsp://admin:secret@${FakeRtspServer.HOST}:${server.port}/stream2"

        val result = connector.connect(CameraTarget.RtspUrl(main, sub), credentials = null)

        assertEquals(
            AppResult.Success(
                CameraInfo(
                    host = FakeRtspServer.HOST,
                    mainStreamUri = server.url("/stream1"),
                    subStreamUri = null,
                    suggestedName = FakeRtspServer.HOST,
                ),
            ),
            result,
        )
        assertFalse(server.requestLines.any { "secret" in it || "admin" in it })
    }

    @Test
    fun `rtsp url keeps a working sub stream`() = runTest {
        val server = start(FakeRtspServer(videoPaths = setOf("/stream1", "/stream2")))

        val result = connector.connect(
            CameraTarget.RtspUrl(server.url("/stream1"), server.url("/stream2")),
            login,
        )

        assertEquals(server.url("/stream2"), (result as AppResult.Success).value.subStreamUri)
    }

    @Test
    fun `non rtsp url is rejected`() = runTest {
        val result = connector.connect(CameraTarget.RtspUrl("http://10.0.0.5/video"), login)

        assertEquals(true, (result as AppResult.Failure).error is AppError.Unknown)
    }

    private fun start(fake: FakeRtspServer): FakeRtspServer = fake.also { server = it }

    private fun FakeRtspServer.url(path: String) = "rtsp://${FakeRtspServer.HOST}:$port$path"

    private companion object {
        const val DAHUA_MAIN = "/cam/realmonitor?channel=1&subtype=0"
        const val DAHUA_SUB = "/cam/realmonitor?channel=1&subtype=1"
    }
}
