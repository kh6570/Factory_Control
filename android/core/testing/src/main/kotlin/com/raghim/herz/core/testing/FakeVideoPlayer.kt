// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.video.PlayerState
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.core.video.VideoPlayerFactory
import com.raghim.herz.core.video.VideoScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeVideoPlayer(override val cameraId: String) : VideoPlayer {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    private val _quality = MutableStateFlow(StreamQuality.SUB)
    override val state: StateFlow<PlayerState> = _state
    override val quality: StateFlow<StreamQuality> = _quality

    val calls = mutableListOf<String>()
    var released = false
        private set

    override fun play(quality: StreamQuality) {
        calls += "play:$quality"
        _quality.value = quality
        _state.value = PlayerState.Playing
    }

    override fun switchQuality(quality: StreamQuality) {
        calls += "switch:$quality"
        _quality.value = quality
    }

    override fun pause() {
        calls += "pause"
        _state.value = PlayerState.Idle
    }

    override fun retry() {
        calls += "retry"
        _state.value = PlayerState.Playing
    }

    override fun release() {
        calls += "release"
        released = true
    }

    fun emit(state: PlayerState) {
        _state.value = state
    }

    @Composable
    override fun Surface(modifier: Modifier, scale: VideoScale) {
        Box(modifier.background(Color.DarkGray))
    }
}

class FakeVideoPlayerFactory : VideoPlayerFactory {
    val created = mutableMapOf<String, FakeVideoPlayer>()

    override fun create(cameraId: String): VideoPlayer =
        FakeVideoPlayer(cameraId).also { created[cameraId] = it }
}
