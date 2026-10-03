// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.ui.LoadingState
import com.raghim.herz.core.ui.MessageState
import com.raghim.herz.core.video.VideoPlayer
import kotlinx.coroutines.launch

@Composable
internal fun ActiveCamerasRoute(
    onAddCameras: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    viewModel: ActiveCamerasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fullscreen = state.maximizedCameraId != null
    val currentOnFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    LaunchedEffect(fullscreen) { currentOnFullscreenChanged(fullscreen) }
    DisposableEffect(Unit) { onDispose { currentOnFullscreenChanged(false) } }

    ActiveCamerasScreen(
        state = state,
        player = viewModel::player,
        onIntent = viewModel::onIntent,
        onAddCameras = onAddCameras,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveCamerasScreen(
    state: ActiveCamerasUiState,
    player: (String) -> VideoPlayer,
    onIntent: (ActiveCamerasIntent) -> Unit,
    onAddCameras: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullscreen = state.maximizedCameraId != null
    // Hoisted above AnimatedContent so Back from fullscreen returns to the same scroll position.
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val stoppedMessage = stringResource(R.string.liveview_camera_stopped)
    val onStop: (String) -> Unit = { id ->
        onIntent(ActiveCamerasIntent.Stop(id))
        scope.launch { snackbarHostState.showSnackbar(stoppedMessage) }
    }
    val onRetry: (String) -> Unit = { id -> onIntent(ActiveCamerasIntent.Retry(id)) }

    BackHandler(enabled = fullscreen) { onIntent(ActiveCamerasIntent.Minimize) }
    ImmersiveModeEffect(hidden = fullscreen)

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            if (state.tiles.isEmpty()) {
                                stringResource(R.string.liveview_title)
                            } else {
                                stringResource(R.string.liveview_title_count, state.tiles.size)
                            },
                        )
                    },
                    actions = {
                        IconButton(onClick = onAddCameras) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.liveview_add_cameras))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = if (fullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.tiles.isEmpty() -> MessageState(
                title = stringResource(R.string.liveview_empty_title),
                body = stringResource(R.string.liveview_empty_body),
                actionLabel = stringResource(R.string.liveview_add_cameras),
                onAction = onAddCameras,
                modifier = Modifier.padding(padding),
            )
            else -> AnimatedContent(
                targetState = fullscreen,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f))
                },
                modifier = Modifier.fillMaxSize(),
                label = "maximize",
            ) { showFullscreen ->
                if (showFullscreen) {
                    FullscreenCamera(
                        tiles = state.tiles,
                        maximizedCameraId = state.maximizedCameraId,
                        player = player,
                        onSwipe = { onIntent(ActiveCamerasIntent.SwipeTo(it)) },
                        onMinimize = { onIntent(ActiveCamerasIntent.Minimize) },
                        onStop = onStop,
                        onRetry = onRetry,
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (state.networkMode == NetworkMode.OFFLINE) OfflineBanner()
                        AdaptiveCameraGrid(
                            tiles = state.tiles,
                            liveIds = state.liveIds,
                            gridState = gridState,
                            playbackEnabled = !fullscreen,
                            player = player,
                            onTap = { onIntent(ActiveCamerasIntent.Maximize(it)) },
                            onStop = onStop,
                            onRetry = onRetry,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.liveview_offline_banner), style = MaterialTheme.typography.bodySmall)
        }
    }
}
