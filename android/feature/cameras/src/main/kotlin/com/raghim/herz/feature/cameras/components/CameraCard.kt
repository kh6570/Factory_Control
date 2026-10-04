// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraOverview
import com.raghim.herz.feature.cameras.R
import java.time.Instant

@Composable
internal fun CameraCard(
    overview: CameraOverview,
    onShowOnLive: (Boolean) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera = overview.camera
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = camera.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = camera.subtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CameraOverflowMenu(cameraName = camera.name, onRename = onRename, onRemove = onRemove)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.cameras_show_on_live), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = overview.isActive, onCheckedChange = onShowOnLive)
            }
        }
    }
}

@Composable
private fun CameraOverflowMenu(
    cameraName: String,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cameras_more_options, cameraName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cameras_rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cameras_remove)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

private fun Camera.subtitle(): String {
    val device = listOfNotNull(manufacturer, model).joinToString(" ")
    return if (device.isBlank()) host else "$host · $device"
}

@Preview
@Composable
private fun CameraCardPreview() {
    HerzTheme(darkTheme = true) {
        CameraCard(
            overview = CameraOverview(
                camera = Camera(
                    id = "cam01",
                    name = "Gate",
                    host = "192.168.1.20",
                    mainStreamUri = "rtsp://192.168.1.20:554/main",
                    subStreamUri = null,
                    manufacturer = "Tapo",
                    model = "C200",
                    addedAt = Instant.EPOCH,
                ),
                isActive = true,
            ),
            onShowOnLive = {},
            onRename = {},
            onRemove = {},
        )
    }
}
