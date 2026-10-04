// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.StreamQuality
import com.raghim.herz.core.video.PlayerState
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.core.video.VideoScale
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 3_000L

/**
 * One maximized camera, swipe for the next. [maximizedCameraId] is the current state and is
 * null while this view animates out; then it gives the video surface back to the grid.
 */
@Composable
internal fun FullscreenCamera(
    tiles: List<ActiveCamera>,
    maximizedCameraId: String?,
    player: (String) -> VideoPlayer,
    onSwipe: (String) -> Unit,
    onMinimize: () -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = maximizedCameraId != null
    val startPage = remember { tiles.indexOfFirst { it.cameraId == maximizedCameraId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startPage) { tiles.size }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    val currentTiles by rememberUpdatedState(tiles)
    val currentMaximized by rememberUpdatedState(maximizedCameraId)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val id = currentTiles.getOrNull(page)?.cameraId
            val max = currentMaximized
            if (id != null && max != null && id != max) currentOnSwipe(id)
        }
    }
    // Tiles can reorder (a new alarm camera goes first): keep the maximized camera on screen.
    LaunchedEffect(maximizedCameraId, tiles) {
        val index = tiles.indexOfFirst { it.cameraId == maximizedCameraId }
        if (index >= 0 && index != pagerState.settledPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(index)
        }
    }
    LaunchedEffect(controlsVisible, pagerState.settledPage) {
        if (controlsVisible) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            key = { index -> tiles.getOrNull(index)?.cameraId ?: index },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val camera = tiles.getOrNull(page) ?: return@HorizontalPager
            val id = camera.cameraId
            val pagePlayer = remember(id) { player(id) }
            PlaybackEffect(
                pagePlayer,
                shouldPlay = active && page == pagerState.settledPage,
                quality = StreamQuality.MAIN,
            )
            val playerState by pagePlayer.state.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize()) {
                if (active) pagePlayer.Surface(Modifier.fillMaxSize(), VideoScale.Fit)
                (playerState as? PlayerState.Error)?.let { error ->
                    ErrorOverlay(error.reason, onRetry = { onRetry(id) }, compact = false, modifier = Modifier.fillMaxSize())
                }
            }
        }

        val current = tiles.getOrNull(pagerState.settledPage)
        if (current != null) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                FullscreenControls(
                    camera = current,
                    player = remember(current.cameraId) { player(current.cameraId) },
                    page = pagerState.settledPage + 1,
                    pageCount = tiles.size,
                    onMinimize = onMinimize,
                )
            }
        }
    }
}

@Composable
private fun FullscreenControls(
    camera: ActiveCamera,
    player: VideoPlayer,
    page: Int,
    pageCount: Int,
    onMinimize: () -> Unit,
) {
    val playerState by player.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.liveview_minimize),
                    tint = Color.White,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CameraStatusBadge(isLive = true, playerState = playerState, streamState = camera.state)
                SourceBadge(camera.source)
            }
        }
        if (pageCount > 1) {
            Text(
                text = stringResource(R.string.liveview_page_indicator, page, pageCount),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(bottom = 20.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
