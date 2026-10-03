// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raghim.herz.core.model.StreamQuality
import kotlinx.coroutines.flow.StateFlow

sealed interface PlayerState {
    data object Idle : PlayerState

    data object Connecting : PlayerState

    data object Playing : PlayerState

    data class Error(val reason: PlayerError) : PlayerState
}

enum class PlayerError { Unreachable, Unauthorized, Unsupported, Lost, Unknown }

/**
 * One camera's live stream (spec D9). All calls must happen on the main thread.
 * A player may render into one [Surface] at a time.
 */
interface VideoPlayer {
    val cameraId: String
    val state: StateFlow<PlayerState>
    val quality: StateFlow<StreamQuality>

    /** Starts or resumes playback. No-op if already playing this quality. */
    fun play(quality: StreamQuality)

    /** Changes quality and keeps the last frame on screen until the new one arrives. */
    fun switchQuality(quality: StreamQuality)

    /** Stops decoding but keeps the player, so [play] resumes quickly. */
    fun pause()

    /** Reconnects after an error. */
    fun retry()

    fun release()

    @Composable
    fun Surface(modifier: Modifier, scale: VideoScale)
}

enum class VideoScale {
    /** Whole frame visible, letterboxed. Fullscreen. */
    Fit,

    /** Fills the box, edges cropped. Grid tiles. */
    Fill,
}

fun interface VideoPlayerFactory {
    fun create(cameraId: String): VideoPlayer
}
