// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import com.raghim.herz.core.model.DiscoveredDevice
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Sends ONVIF WS-Discovery probes and reports each replying host once. Runs until cancelled,
 * so callers bound it with a timeout. Pure Kotlin (java.net only).
 */
internal class WsDiscoveryScanner(
    private val probeCount: Int = PROBE_COUNT,
    private val probeIntervalMs: Long = PROBE_INTERVAL_MS,
) {
    suspend fun scan(localAddress: InetAddress?, onFound: suspend (DiscoveredDevice) -> Unit) {
        MulticastSocket().useCancellable { socket ->
            socket.soTimeout = RECEIVE_POLL_MS
            socket.timeToLive = MULTICAST_TTL
            localAddress?.let { address ->
                try {
                    NetworkInterface.getByInetAddress(address)?.let { socket.networkInterface = it }
                } catch (e: Exception) {
                    // Fall back to the default multicast interface.
                }
            }
            val group = InetAddress.getByName(WsDiscoveryCodec.MULTICAST_ADDRESS)
            val probe = WsDiscoveryCodec.probe().toByteArray(Charsets.UTF_8)
            val seen = HashSet<String>()
            coroutineScope {
                launch {
                    repeat(probeCount) { index ->
                        if (index > 0) delay(probeIntervalMs)
                        runInterruptible {
                            socket.send(DatagramPacket(probe, probe.size, group, WsDiscoveryCodec.PORT))
                        }
                    }
                }
                val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runInterruptible {
                        try {
                            socket.receive(packet)
                            true
                        } catch (e: SocketTimeoutException) {
                            false
                        }
                    }
                    if (!received) continue
                    val xml = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    WsDiscoveryCodec.parseProbeMatches(xml, packet.address?.hostAddress)
                        .filter { seen.add(it.host) }
                        .forEach { onFound(it) }
                }
            }
        }
    }

    private companion object {
        const val PROBE_COUNT = 3
        const val PROBE_INTERVAL_MS = 300L
        const val RECEIVE_POLL_MS = 250
        const val MULTICAST_TTL = 4
        const val RECEIVE_BUFFER_BYTES = 16 * 1024
    }
}
