// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Finds hosts with an open RTSP port by plain TCP connects. Pure Kotlin. */
internal class RtspPortScanner(
    private val ports: List<Int> = listOf(DiscoveredDevice.DEFAULT_RTSP_PORT, ALT_RTSP_PORT),
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val parallelism: Int = PARALLELISM,
) {
    /** Calls [onFound] once per host, with the first open port in [ports] order. */
    suspend fun scan(hosts: List<String>, onFound: suspend (DiscoveredDevice) -> Unit) {
        val permits = Semaphore(parallelism)
        coroutineScope {
            hosts.forEach { host ->
                launch {
                    val port = permits.withPermit { firstOpenPort(host) } ?: return@launch
                    onFound(DiscoveredDevice(host = host, method = DiscoveryMethod.PORT_SCAN, rtspPort = port))
                }
            }
        }
    }

    suspend fun firstOpenPort(host: String): Int? = ports.firstOrNull { isOpen(host, it) }

    suspend fun isOpen(host: String, port: Int): Boolean = Socket().useCancellable { socket ->
        try {
            runInterruptible { socket.connect(InetSocketAddress(host, port), connectTimeoutMs) }
            true
        } catch (e: IOException) {
            false
        }
    }

    private companion object {
        const val ALT_RTSP_PORT = 8554
        const val CONNECT_TIMEOUT_MS = 350
        const val PARALLELISM = 48
    }
}
