// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.raghim.herz.core.common.Dispatcher
import com.raghim.herz.core.common.HerzDispatchers
import com.raghim.herz.core.domain.camera.CameraDiscovery
import com.raghim.herz.core.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration

/**
 * Scans the phone's LAN with ONVIF WS-Discovery and an RTSP port scan at the same time.
 * Completes when [scan]'s timeout elapses. A failure in one method does not stop the other.
 */
class LanCameraDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(HerzDispatchers.IO) private val io: CoroutineDispatcher,
) : CameraDiscovery {
    private val lanAddresses = LanAddressProvider(context)
    private val wsDiscovery = WsDiscoveryScanner()
    private val portScanner = RtspPortScanner()

    override fun scan(timeout: Duration): Flow<DiscoveredDevice> = channelFlow {
        val lan = guarded("lan address") { lanAddresses.current() }
        val multicastLock = acquireMulticastLock()
        try {
            withTimeoutOrNull(timeout) {
                launch {
                    guarded("ws-discovery") { wsDiscovery.scan(lan?.address) { send(it) } }
                }
                launch {
                    val range = lan?.let { SubnetRange.around(it.host, it.prefixLength) } ?: return@launch
                    guarded("port scan") { portScanner.scan(range.hosts) { send(it) } }
                }
            }
        } finally {
            multicastLock?.releaseQuietly()
        }
    }.flowOn(io)

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (e: Exception) {
        Log.d(TAG, "multicast lock unavailable: ${e.javaClass.simpleName}")
        null
    }

    private fun WifiManager.MulticastLock.releaseQuietly() {
        try {
            if (isHeld) release()
        } catch (e: Exception) {
            Log.d(TAG, "multicast lock release failed: ${e.javaClass.simpleName}")
        }
    }

    /** Runs [block], logging (without details) and swallowing anything except cancellation. */
    private suspend fun <T> guarded(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A socket closed by cancellation throws here; that is not a failure worth logging.
        currentCoroutineContext().ensureActive()
        Log.d(TAG, "$step failed: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val TAG = "HerzDiscovery"
        const val MULTICAST_LOCK_TAG = "herz:camera-discovery"
    }
}
