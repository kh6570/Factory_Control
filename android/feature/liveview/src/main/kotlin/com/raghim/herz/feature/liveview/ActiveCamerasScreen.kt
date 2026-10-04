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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.domain.door.LiveGridColumns
import com.raghim.herz.core.model.NetworkMode
import com.raghim.herz.core.ui.LoadingState
import com.raghim.herz.core.ui.MessageState
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.feature.liveview.door.DoorPanelIntent
import com.raghim.herz.feature.liveview.door.DoorPanelLayout
import com.raghim.herz.feature.liveview.door.DoorPanelUiState
import com.raghim.herz.feature.liveview.door.DoorPanelViewModel
import com.raghim.herz.feature.liveview.door.message

@Composable
internal fun ActiveCamerasRoute(
    onFullscreenChanged: (Boolean) -> Unit,
    viewModel: ActiveCamerasViewModel = hiltViewModel(),
    doorViewModel: DoorPanelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val doorState by doorViewModel.state.collectAsStateWithLifecycle()
    val fullscreen = state.maximizedCameraId != null
    val currentOnFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    LaunchedEffect(fullscreen) { currentOnFullscreenChanged(fullscreen) }
    DisposableEffect(Unit) { onDispose { currentOnFullscreenChanged(false) } }

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(doorViewModel) {
        doorViewModel.effects.collect { snackbarHostState.showSnackbar(it.message(resources)) }
    }

    ActiveCamerasScreen(
        state = state,
        player = viewModel::player,
        onIntent = viewModel::onIntent,
        doorState = doorState,
        onDoorIntent = doorViewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveCamerasScreen(
    state: ActiveCamerasUiState,
    player: (String) -> VideoPlayer,
    onIntent: (ActiveCamerasIntent) -> Unit,
    doorState: DoorPanelUiState,
    onDoorIntent: (DoorPanelIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val fullscreen = state.maximizedCameraId != null
    // Hoisted above AnimatedContent so Back from fullscreen returns to the same scroll position.
    val gridState = rememberLazyGridState()
    var columnsOpen by rememberSaveable { mutableStateOf(false) }
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
                        IconButton(onClick = { columnsOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.liveview_settings))
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
            state.tiles.isEmpty() -> DoorPanelLayout(
                state = doorState,
                onIntent = onDoorIntent,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { panelPadding ->
                MessageState(
                    title = stringResource(R.string.liveview_empty_title),
                body = stringResource(R.string.liveview_empty_body),
                modifier = Modifier.padding(panelPadding),
                )
            }
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
                        onRetry = onRetry,
                    )
                } else {
                    DoorPanelLayout(
                        state = doorState,
                        onIntent = onDoorIntent,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) { panelPadding ->
                        Column(Modifier.fillMaxSize().padding(panelPadding)) {
                            if (state.networkMode == NetworkMode.OFFLINE) OfflineBanner()
                            AdaptiveCameraGrid(
                                tiles = state.tiles,
                                liveIds = state.liveIds,
                                gridState = gridState,
                                chosenColumns = state.gridColumns,
                                playbackEnabled = !fullscreen,
                                player = player,
                                onTap = { onIntent(ActiveCamerasIntent.Maximize(it)) },
                                onRetry = onRetry,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
    if (columnsOpen) {
        GridColumnsDialog(
            selected = state.gridColumns,
            onSelect = { onIntent(ActiveCamerasIntent.SetGridColumns(it)) },
            onDismiss = { columnsOpen = false },
        )
    }
}

@Composable
private fun GridColumnsDialog(selected: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.liveview_columns_title)) },
        text = {
            Column {
                GridColumnChoice(
                    label = stringResource(R.string.liveview_columns_automatic),
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
                for (count in LiveGridColumns.MIN..LiveGridColumns.MAX) {
                    GridColumnChoice(
                        label = stringResource(R.string.liveview_columns_count, count),
                        selected = selected == count,
                        onClick = { onSelect(count) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.liveview_done)) }
        },
    )
}

@Composable
private fun GridColumnChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
