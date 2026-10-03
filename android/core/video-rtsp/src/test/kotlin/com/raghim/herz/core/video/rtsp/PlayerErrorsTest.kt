// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import androidx.media3.common.PlaybackException
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.video.PlayerError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

class PlayerErrorsTest {

    @Test
    fun `app errors map to player errors`() {
        assertEquals(PlayerError.Unauthorized, AppError.Unauthorized.toPlayerError())
        assertEquals(PlayerError.Unreachable, AppError.Unreachable.toPlayerError())
        assertEquals(PlayerError.Unreachable, AppError.Timeout.toPlayerError())
        assertEquals(PlayerError.Unreachable, AppError.Offline.toPlayerError())
        assertEquals(PlayerError.Unsupported, AppError.NotFound.toPlayerError())
        assertEquals(PlayerError.Unsupported, AppError.NoStreamFound.toPlayerError())
        assertEquals(PlayerError.Unknown, AppError.Unknown("x").toPlayerError())
    }

    @Test
    fun `network connection codes are unreachable`() {
        assertEquals(
            PlayerError.Unreachable,
            playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null),
        )
        assertEquals(
            PlayerError.Unreachable,
            playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null),
        )
    }

    @Test
    fun `rtsp 401 anywhere in the cause chain is unauthorized`() {
        val cause = IOException("wrapper", IOException("DESCRIBE 401"))
        assertEquals(
            PlayerError.Unauthorized,
            playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause),
        )
    }

    @Test
    fun `unsupported formats are unsupported`() {
        assertEquals(
            PlayerError.Unsupported,
            playbackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, null),
        )
        assertEquals(
            PlayerError.Unsupported,
            playbackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, null),
        )
    }

    @Test
    fun `connect exception cause is unreachable`() {
        assertEquals(
            PlayerError.Unreachable,
            playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, IOException(ConnectException("refused"))),
        )
    }

    @Test
    fun `other failures are lost`() {
        assertEquals(
            PlayerError.Lost,
            playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, IOException("socket closed")),
        )
        assertEquals(PlayerError.Lost, playbackError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null))
    }

    @Test
    fun `port numbers containing 401 are not unauthorized`() {
        assertEquals(
            PlayerError.Lost,
            playbackError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, IOException("port 14012 closed")),
        )
    }
}
