// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import androidx.media3.common.PlaybackException
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.video.PlayerError
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun AppError.toPlayerError(): PlayerError = when (this) {
    AppError.Unauthorized -> PlayerError.Unauthorized
    AppError.Unreachable, AppError.Timeout, AppError.Offline -> PlayerError.Unreachable
    AppError.NotFound, AppError.NoStreamFound -> PlayerError.Unsupported
    AppError.Cancelled, AppError.AuthenticationUnavailable, AppError.KeyInvalidated, AppError.Expired,
    is AppError.Unknown,
    -> PlayerError.Unknown
}

private val UNAUTHORIZED_STATUS = Regex("""\b401\b""")

/**
 * Maps a [PlaybackException] error code and cause. Media3 reports RTSP auth failures as
 * `RtspPlaybackException("DESCRIBE 401")`, so the cause chain is searched for a 401 status.
 * Decoder init failures count as [PlayerError.Lost] because on a camera wall they usually
 * mean "out of hardware decoders right now" and are worth retrying.
 */
internal fun playbackError(errorCode: Int, cause: Throwable?): PlayerError {
    val causes = generateSequence(cause) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
    return when {
        causes.any { it.message?.let(UNAUTHORIZED_STATUS::containsMatchIn) == true } ->
            PlayerError.Unauthorized
        errorCode in UNSUPPORTED_CODES -> PlayerError.Unsupported
        errorCode in UNREACHABLE_CODES -> PlayerError.Unreachable
        causes.any { it.isNetworkUnreachable() } -> PlayerError.Unreachable
        else -> PlayerError.Lost
    }
}

private fun Throwable.isNetworkUnreachable(): Boolean =
    this is ConnectException ||
        this is NoRouteToHostException ||
        this is SocketTimeoutException ||
        this is UnknownHostException

private const val MAX_CAUSE_DEPTH = 8

private val UNREACHABLE_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
)

private val UNSUPPORTED_CODES = setOf(
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
)
