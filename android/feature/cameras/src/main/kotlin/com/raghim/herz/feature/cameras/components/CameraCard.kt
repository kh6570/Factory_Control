// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.cameras.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.designsystem.component.LiveStatus
import com.raghim.herz.core.designsystem.component.color
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.CameraOverview
import com.raghim.herz.feature.cameras.R
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CameraCard(
    overview: CameraOverview,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera = overview.camera
    val shape = CardDefaults.shape
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val selectLabel = stringResource(R.string.cameras_select, camera.name)
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.semantics { contentDescription = selectLabel },
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                WallStatusPill(isActive = overview.isActive)
                if (overview.isActive) {
                    OutlinedButton(onClick = onStop) { Text(stringResource(R.string.cameras_stop)) }
                } else {
                    Button(onClick = onStart, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = stringResource(R.string.cameras_start),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                }
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
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cameras_more_options, cameraName),
            )
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

@Composable
internal fun WallStatusPill(isActive: Boolean, modifier: Modifier = Modifier) {
    val status = if (isActive) LiveStatus.Live else LiveStatus.Idle
    val color = status.color()
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = stringResource(if (isActive) R.string.cameras_status_on_wall else R.string.cameras_status_off),
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Camera.subtitle(): String {
    val device = listOfNotNull(manufacturer, model).joinToString(" ")
    return if (device.isBlank()) host else "$host · $device"
}

@Preview
@Composable
private fun CameraCardPreview() {
    HerzTheme {
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
            selected = false,
            onClick = {},
            onLongClick = {},
            onToggleSelect = {},
            onStart = {},
            onStop = {},
            onRename = {},
            onRemove = {},
        )
    }
}
