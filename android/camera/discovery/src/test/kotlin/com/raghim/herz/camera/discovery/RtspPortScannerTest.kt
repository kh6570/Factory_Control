// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class RtspPortScannerTest {

    @Test
    fun `reports a host with an open port once, using the first open port`() = runBlocking {
        ServerSocket(0, 50, InetAddress.getByName(LOCALHOST)).use { open ->
            val closedPort = freePort()
            val scanner = RtspPortScanner(ports = listOf(closedPort, open.localPort), connectTimeoutMs = 500)
            val found = mutableListOf<DiscoveredDevice>()

            withContext(Dispatchers.IO) { scanner.scan(listOf(LOCALHOST)) { synchronized(found) { found += it } } }

            assertEquals(
                listOf(DiscoveredDevice(LOCALHOST, DiscoveryMethod.PORT_SCAN, rtspPort = open.localPort)),
                found,
            )
        }
    }

    @Test
    fun `closed port is not open`() = runBlocking {
        val scanner = RtspPortScanner(connectTimeoutMs = 500)

        assertFalse(withContext(Dispatchers.IO) { scanner.isOpen(LOCALHOST, freePort()) })
    }

    @Test
    fun `listening port is open`() = runBlocking {
        ServerSocket(0, 50, InetAddress.getByName(LOCALHOST)).use { server ->
            val scanner = RtspPortScanner(connectTimeoutMs = 500)

            assertTrue(withContext(Dispatchers.IO) { scanner.isOpen(LOCALHOST, server.localPort) })
        }
    }

    private fun freePort(): Int = ServerSocket(0, 50, InetAddress.getByName(LOCALHOST)).use { it.localPort }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
    }
}
