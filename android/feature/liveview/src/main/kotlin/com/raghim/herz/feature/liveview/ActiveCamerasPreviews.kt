// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Door
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.model.StreamState
import com.raghim.herz.core.video.PlayerError
import com.raghim.herz.core.video.PlayerState
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.core.video.VideoScale
import com.raghim.herz.feature.liveview.door.DoorCommand
import com.raghim.herz.feature.liveview.door.DoorItem
import com.raghim.herz.feature.liveview.door.DoorPanelUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

private class PreviewPlayer(override val cameraId: String, initial: PlayerState) : VideoPlayer {
    override val state: StateFlow<PlayerState> = MutableStateFlow(initial)
    override val quality: StateFlow<StreamQuality> = MutableStateFlow(StreamQuality.SUB)
    override fun play(quality: StreamQuality) = Unit
    override fun switchQuality(quality: StreamQuality) = Unit
    override fun pause() = Unit
    override fun retry() = Unit
    override fun release() = Unit

    @Composable
    override fun Surface(modifier: Modifier, scale: VideoScale) {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF2B3440), Color(0xFF151A20)))))
    }
}

private fun previewState(count: Int, mode: NetworkMode = NetworkMode.LAN): ActiveCamerasUiState {
    val tiles = List(count) { i ->
        ActiveCamera(
            cameraId = "cam$i",
            name = if (i == 0) "Gate North" else "Hall ${i + 1}",
            source = if (i == 0 && count > 1) SessionSource.ALARM else SessionSource.MANUAL,
            state = StreamState.LIVE,
            startedBy = null,
            startedAt = Instant.EPOCH,
            alarmId = if (i == 0 && count > 1) "preview-alarm" else null,
            highlightAlarm = i == 0 && count > 1,
        )
    }
    val budget = if (mode == NetworkMode.CELLULAR) 4 else 9
    return ActiveCamerasUiState(
        tiles = tiles,
        liveBudget = budget,
        networkMode = mode,
        isLoading = false,
    )
}

private fun previewPlayer(id: String): VideoPlayer = PreviewPlayer(
    cameraId = id,
    initial = when (id) {
        "cam2" -> PlayerState.Connecting
        "cam3" -> PlayerState.Error(PlayerError.Unauthorized)
        else -> PlayerState.Playing
    },
)

private val previewDoors = DoorPanelUiState(
    doors = listOf(
        DoorItem(Door("d1", "Main gate", "Gate", contact = DoorContact.CLOSED), onWall = true),
        DoorItem(
            Door("d2", "Loading bay", "Warehouse", lock = LockState.UNLOCKED, contact = DoorContact.OPEN),
        ),
        DoorItem(Door("d3", "Office entrance", "Office", contact = DoorContact.CLOSED), command = DoorCommand.Unlocking),
        DoorItem(Door("d4", "Roof access", "Roof", isOnline = false)),
    ),
    isLoading = false,
)

@Composable
private fun WallPreview(state: ActiveCamerasUiState) {
    HerzTheme(darkTheme = true) {
        ActiveCamerasScreen(
            state = state,
            player = ::previewPlayer,
            onIntent = {},
            doorState = previewDoors,
            onDoorIntent = {},
        )
    }
}

@Preview(name = "1 camera", widthDp = 360, heightDp = 720)
@Composable
private fun OneCameraPreview() = WallPreview(previewState(1))

@Preview(name = "4 cameras", widthDp = 360, heightDp = 720)
@Composable
private fun FourCamerasPreview() = WallPreview(previewState(4))

@Preview(name = "9 cameras, cellular", widthDp = 360, heightDp = 720)
@Composable
private fun NineCamerasPreview() = WallPreview(previewState(9, NetworkMode.CELLULAR))

@Preview(name = "6 cameras, landscape", widthDp = 800, heightDp = 360)
@Composable
private fun SixCamerasLandscapePreview() = WallPreview(previewState(6))

@Preview(name = "Empty", widthDp = 360, heightDp = 720)
@Composable
private fun EmptyPreview() = WallPreview(ActiveCamerasUiState(isLoading = false))
