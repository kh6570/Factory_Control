// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.video.VideoPlayer

private val TileGap = 4.dp

/**
 * The wall. Tiles are composed only while visible, so off-screen tiles are paused by
 * [PlaybackEffect]. [playbackEnabled] is false while a camera is maximized.
 */
@Composable
internal fun AdaptiveCameraGrid(
    tiles: List<ActiveCamera>,
    liveIds: Set<String>,
    gridState: LazyGridState,
    playbackEnabled: Boolean,
    player: (String) -> VideoPlayer,
    onTap: (String) -> Unit,
    onStop: (String) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WallBackground)
            .padding(TileGap),
        contentAlignment = Alignment.Center,
    ) {
        val wide = landscape || maxWidth >= 600.dp
        val columns = gridColumns(tiles.size, wide)
        val gridWidth = fittedGridWidth(
            count = tiles.size,
            columns = columns,
            maxWidth = maxWidth.value,
            maxHeight = maxHeight.value,
            gap = TileGap.value,
        ).dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .width(gridWidth)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterVertically),
        ) {
            items(tiles, key = { it.cameraId }, contentType = { "camera" }) { camera ->
                val id = camera.cameraId
                // Remembered so a tile that is going away never asks the pool to recreate its player.
                val tilePlayer = remember(id) { player(id) }
                val isLive = id in liveIds
                PlaybackEffect(tilePlayer, shouldPlay = isLive && playbackEnabled, quality = StreamQuality.SUB)
                CameraTile(
                    camera = camera,
                    player = tilePlayer,
                    isLive = isLive,
                    showVideo = playbackEnabled,
                    onTap = { onTap(id) },
                    onStop = { onStop(id) },
                    onRetry = { onRetry(id) },
                    modifier = Modifier.animateItem(fadeOutSpec = null),
                )
            }
        }
    }
}
