// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.common.AppResult
import com.raghim.herz.core.common.Dispatcher
import com.raghim.herz.core.common.HerzDispatchers
import com.raghim.herz.core.domain.camera.CameraConnector
import com.raghim.herz.core.model.CameraCredentials
import com.raghim.herz.core.model.CameraInfo
import com.raghim.herz.core.model.CameraTarget
import com.raghim.herz.core.model.DiscoveredDevice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Finds working RTSP stream URIs by sending `DESCRIBE` to well-known paths, one at a time
 * (cameras dislike parallel sessions). Returned URIs never contain credentials.
 */
class RtspCameraConnector internal constructor(
    private val io: CoroutineDispatcher,
    private val probe: RtspProbe,
    private val timeout: Duration,
) : CameraConnector {

    @Inject
    constructor(@Dispatcher(HerzDispatchers.IO) io: CoroutineDispatcher) : this(io, RtspProbe(), DEFAULT_TIMEOUT)

    override suspend fun connect(target: CameraTarget, credentials: CameraCredentials?): AppResult<CameraInfo> =
        withContext(io) {
            withTimeoutOrNull(timeout) {
                when (target) {
                    is CameraTarget.RtspUrl -> connectToUrl(target, credentials)
                    is CameraTarget.Host -> searchPaths(target.host.trim(), target.rtspPort, credentials, device = null)
                    is CameraTarget.Device -> searchPaths(target.device.host, target.device.rtspPort, credentials, target.device)
                }
            } ?: AppResult.Failure(AppError.Timeout)
        }

    private suspend fun connectToUrl(target: CameraTarget.RtspUrl, credentials: CameraCredentials?): AppResult<CameraInfo> {
        val main = RtspUri.parse(target.mainUri)
        val host = main.host
        if (host == null || !main.sanitized.startsWith("rtsp://", ignoreCase = true)) {
            return AppResult.Failure(AppError.Unknown("Not an rtsp:// URL"))
        }
        val sub = target.subUri?.takeIf { it.isNotBlank() }?.let { RtspUri.parse(it) }
        val login = credentials ?: main.credentials ?: sub?.credentials
        return when (probe.describe(main.sanitized, login)) {
            is ProbeResult.Ok -> {
                val workingSub = sub?.sanitized?.takeIf { probe.describe(it, login) is ProbeResult.Ok }
                AppResult.Success(cameraInfo(host, main.sanitized, workingSub, device = null))
            }
            ProbeResult.Unauthorized -> AppResult.Failure(AppError.Unauthorized)
            ProbeResult.NotFound -> AppResult.Failure(AppError.NoStreamFound)
            ProbeResult.Unreachable -> AppResult.Failure(AppError.Unreachable)
        }
    }

    private suspend fun searchPaths(
        host: String,
        port: Int,
        credentials: CameraCredentials?,
        device: DiscoveredDevice?,
    ): AppResult<CameraInfo> {
        val candidates = RtspPathCatalog.candidates(device?.manufacturer, device?.name, device?.model)
        for ((index, paths) in candidates.withIndex()) {
            val mainUri = rtspUrl(host, port, paths.main)
            when (probe.describe(mainUri, credentials)) {
                is ProbeResult.Ok -> {
                    val subUri = paths.sub
                        ?.let { rtspUrl(host, port, it) }
                        ?.takeIf { probe.describe(it, credentials) is ProbeResult.Ok }
                    return AppResult.Success(cameraInfo(host, mainUri, subUri, device))
                }
                ProbeResult.Unauthorized -> return AppResult.Failure(AppError.Unauthorized)
                // A dead host fails every path; do not wait for all of them.
                ProbeResult.Unreachable -> if (index == 0) return AppResult.Failure(AppError.Unreachable)
                ProbeResult.NotFound -> Unit
            }
        }
        return AppResult.Failure(AppError.NoStreamFound)
    }

    private fun cameraInfo(host: String, mainUri: String, subUri: String?, device: DiscoveredDevice?) = CameraInfo(
        host = host,
        mainStreamUri = mainUri,
        subStreamUri = subUri,
        manufacturer = device?.manufacturer,
        model = device?.model,
        suggestedName = device?.displayName ?: host,
    )

    private companion object {
        val DEFAULT_TIMEOUT = 25.seconds
    }
}
