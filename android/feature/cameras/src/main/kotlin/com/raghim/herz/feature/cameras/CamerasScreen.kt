// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cameras_title)) },
                actions = {
                    IconButton(onClick = onAddCamera) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cameras_add_camera))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.isLoading -> LoadingState(contentModifier)
            state.cameras.isEmpty() -> MessageState(
                title = stringResource(R.string.cameras_empty_title),
                body = stringResource(R.string.cameras_empty_body),
                actionLabel = stringResource(R.string.cameras_find_cameras),
                onAction = onAddCamera,
                modifier = contentModifier,
            )
            else -> CameraList(state.cameras, onIntent, contentModifier)
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
private fun CameraList(
    cameras: List<CameraOverview>,
    onIntent: (CamerasIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onLive = cameras.filter { it.isActive }
    val available = cameras.filter { !it.isActive }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.cameras_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectAllRow(
                allSelected = cameras.all { it.isActive },
                noneSelected = cameras.none { it.isActive },
                onSelectAll = { onIntent(CamerasIntent.ShowAllOnLive) },
                onDeselectAll = { onIntent(CamerasIntent.HideAllFromLive) },
                selectLabel = stringResource(R.string.cameras_select_all),
                deselectLabel = stringResource(R.string.cameras_deselect_all),
            )
        }
        if (onLive.isNotEmpty()) {
            item(key = "on_live_header") { SectionLabel(stringResource(R.string.cameras_section_on_live)) }
            items(onLive, key = { it.camera.id }) { overview -> CameraRow(overview, onIntent) }
        }
        if (available.isNotEmpty()) {
            item(key = "available_header") { SectionLabel(stringResource(R.string.cameras_section_available)) }
            items(available, key = { it.camera.id }) { overview -> CameraRow(overview, onIntent) }
        }
    }
}

@Composable
private fun SelectAllRow(
    allSelected: Boolean,
    noneSelected: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    selectLabel: String,
    deselectLabel: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onSelectAll, enabled = !allSelected) { Text(selectLabel) }
        TextButton(onClick = onDeselectAll, enabled = !noneSelected) { Text(deselectLabel) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun CameraRow(overview: CameraOverview, onIntent: (CamerasIntent) -> Unit) {
    val id = overview.camera.id
    CameraCard(
        overview = overview,
        onShowOnLive = { show ->
            onIntent(if (show) CamerasIntent.Start(id) else CamerasIntent.Stop(id))
        },
        onRename = { onIntent(CamerasIntent.RequestRename(id)) },
        onRemove = { onIntent(CamerasIntent.RequestRemove(id)) },
    )
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
        )
    }
}
