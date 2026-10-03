// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraOverview
import com.raghim.herz.core.ui.LoadingState
import com.raghim.herz.core.ui.MessageState
import com.raghim.herz.feature.cameras.components.CameraCard
import com.raghim.herz.feature.cameras.components.RemoveCameraDialog
import com.raghim.herz.feature.cameras.components.RenameCameraDialog
import com.raghim.herz.feature.cameras.model.CamerasDialog
import com.raghim.herz.feature.cameras.model.CamerasIntent
import com.raghim.herz.feature.cameras.model.CamerasUiState
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CamerasScreen(
    state: CamerasUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (CamerasIntent) -> Unit,
    onAddCamera: () -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isSelecting) {
                SelectionTopBar(
                    count = state.selection.size,
                    onClear = { onIntent(CamerasIntent.ClearSelection) },
                    onStartSelected = { onIntent(CamerasIntent.StartSelected) },
                    onStopSelected = { onIntent(CamerasIntent.StopSelected) },
                )
            } else {
                TopAppBar(title = { Text(stringResource(R.string.cameras_title)) })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isSelecting && state.cameras.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.cameras_add_camera)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onAddCamera,
                )
            }
        },
        bottomBar = {
            if (state.liveCount > 0 && !state.isSelecting) {
                LiveWallBar(count = state.liveCount, onOpenLive = onOpenLive)
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingState(contentModifier)
            state.cameras.isEmpty() -> MessageState(
                title = stringResource(R.string.cameras_empty_title),
                body = stringResource(R.string.cameras_empty_body),
                actionLabel = stringResource(R.string.cameras_find_cameras),
                onAction = onAddCamera,
                modifier = contentModifier,
            )
            else -> CameraGrid(state = state, onIntent = onIntent, modifier = contentModifier)
        }
    }

    when (val dialog = state.dialog) {
        is CamerasDialog.Rename -> RenameCameraDialog(
            currentName = dialog.currentName,
            onConfirm = { onIntent(CamerasIntent.ConfirmRename(it)) },
            onDismiss = { onIntent(CamerasIntent.DismissDialog) },
        )
        is CamerasDialog.Remove -> RemoveCameraDialog(
            name = dialog.name,
            onConfirm = { onIntent(CamerasIntent.ConfirmRemove) },
            onDismiss = { onIntent(CamerasIntent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun CameraGrid(
    state: CamerasUiState,
    onIntent: (CamerasIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.cameras, key = { it.camera.id }) { overview ->
            val id = overview.camera.id
            CameraCard(
                overview = overview,
                selected = id in state.selection,
                onClick = { if (state.isSelecting) onIntent(CamerasIntent.ToggleSelect(id)) },
                onLongClick = { onIntent(CamerasIntent.ToggleSelect(id)) },
                onToggleSelect = { onIntent(CamerasIntent.ToggleSelect(id)) },
                onStart = { onIntent(CamerasIntent.Start(id)) },
                onStop = { onIntent(CamerasIntent.Stop(id)) },
                onRename = { onIntent(CamerasIntent.RequestRename(id)) },
                onRemove = { onIntent(CamerasIntent.RequestRemove(id)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onStartSelected: () -> Unit,
    onStopSelected: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.cameras_selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cameras_clear_selection))
            }
        },
        actions = {
            TextButton(onClick = onStopSelected) { Text(stringResource(R.string.cameras_stop_selected)) }
            TextButton(onClick = onStartSelected) { Text(stringResource(R.string.cameras_start_selected)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@Composable
private fun LiveWallBar(count: Int, onOpenLive: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onOpenLive,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                text = stringResource(R.string.cameras_show_live_wall, count),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun CamerasScreenPreview() {
    val cameras = (1..3).map { index ->
        CameraOverview(
            camera = Camera(
                id = "cam$index",
                name = "Camera $index",
                host = "192.168.1.${10 + index}",
                mainStreamUri = "rtsp://192.168.1.${10 + index}:554/main",
                subStreamUri = null,
                addedAt = Instant.EPOCH,
            ),
            isActive = index == 1,
        )
    }
    HerzTheme {
        CamerasScreen(
            state = CamerasUiState(cameras = cameras, isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onAddCamera = {},
            onOpenLive = {},
        )
    }
}
