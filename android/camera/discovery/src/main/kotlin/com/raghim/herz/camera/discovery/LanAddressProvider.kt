// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The phone's IPv4 address on a Wi-Fi or Ethernet network. */
internal data class LanAddress(val address: Inet4Address, val prefixLength: Int) {
    val host: String get() = address.hostAddress.orEmpty()
}

internal class LanAddressProvider(private val context: Context) {
    /** Null when the phone has no Wi-Fi or Ethernet network (for example cellular only). */
    fun current(): LanAddress? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return candidateNetworks(connectivity)
            .filter { connectivity.isLan(it) }
            .firstNotNullOfOrNull { connectivity.ipv4Of(it) }
    }

    @Suppress("DEPRECATION")
    private fun candidateNetworks(connectivity: ConnectivityManager): List<Network> =
        (listOfNotNull(connectivity.activeNetwork) + connectivity.allNetworks).distinct()

    private fun ConnectivityManager.isLan(network: Network): Boolean {
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun ConnectivityManager.ipv4Of(network: Network): LanAddress? =
        getLinkProperties(network)?.linkAddresses
            ?.firstOrNull { link ->
                val address = link.address
                address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress
            }
            ?.let { LanAddress(it.address as Inet4Address, it.prefixLength) }
}
