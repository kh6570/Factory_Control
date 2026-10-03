// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.network

import com.raghim.herz.core.model.NetworkMode

/**
 * Maps the transports of the default network to a [NetworkMode]. VPN wins because a
 * VPN network also reports its underlying transport. Validation is not required:
 * camera Wi-Fi often has no internet.
 */
internal fun networkModeOf(
    hasVpn: Boolean,
    hasWifi: Boolean,
    hasEthernet: Boolean,
    hasCellular: Boolean,
): NetworkMode = when {
    hasVpn -> NetworkMode.REMOTE_VPN
    hasWifi || hasEthernet -> NetworkMode.LAN
    hasCellular -> NetworkMode.CELLULAR
    else -> NetworkMode.OFFLINE
}
