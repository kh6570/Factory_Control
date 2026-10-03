// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.raghim.herz.core.domain.camera.NetworkMonitor
import com.raghim.herz.core.model.NetworkMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val mode: Flow<NetworkMode> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            trySend(NetworkMode.OFFLINE)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.toNetworkMode())
            }

            override fun onLost(network: Network) {
                trySend(NetworkMode.OFFLINE)
            }
        }

        trySend(connectivityManager.currentMode())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .conflate()

    private fun ConnectivityManager.currentMode(): NetworkMode =
        activeNetwork?.let { getNetworkCapabilities(it) }?.toNetworkMode() ?: NetworkMode.OFFLINE

    private fun NetworkCapabilities.toNetworkMode(): NetworkMode = networkModeOf(
        hasVpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        hasWifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        hasEthernet = hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        hasCellular = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    )
}
