// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.network

import com.raghim.herz.core.model.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModesTest {

    private fun mode(
        vpn: Boolean = false,
        wifi: Boolean = false,
        ethernet: Boolean = false,
        cellular: Boolean = false,
    ) = networkModeOf(hasVpn = vpn, hasWifi = wifi, hasEthernet = ethernet, hasCellular = cellular)

    @Test
    fun wifiIsLan() = assertEquals(NetworkMode.LAN, mode(wifi = true))

    @Test
    fun ethernetIsLan() = assertEquals(NetworkMode.LAN, mode(ethernet = true))

    @Test
    fun cellularIsCellular() = assertEquals(NetworkMode.CELLULAR, mode(cellular = true))

    @Test
    fun vpnWinsOverUnderlyingTransport() {
        assertEquals(NetworkMode.REMOTE_VPN, mode(vpn = true))
        assertEquals(NetworkMode.REMOTE_VPN, mode(vpn = true, wifi = true))
        assertEquals(NetworkMode.REMOTE_VPN, mode(vpn = true, cellular = true))
    }

    @Test
    fun wifiWinsOverCellular() = assertEquals(NetworkMode.LAN, mode(wifi = true, cellular = true))

    @Test
    fun noTransportIsOffline() = assertEquals(NetworkMode.OFFLINE, mode())
}
