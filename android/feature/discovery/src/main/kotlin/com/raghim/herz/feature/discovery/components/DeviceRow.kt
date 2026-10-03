// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.discovery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import com.raghim.herz.feature.discovery.R
import com.raghim.herz.feature.discovery.model.DiscoveredDeviceItem

@Composable
internal fun DeviceRow(
    item: DiscoveredDeviceItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = item.device
    val subtitle = device.subtitle()
    ListItem(
        headlineContent = {
            Text(device.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelPill(
                    text = stringResource(
                        when (device.method) {
                            DiscoveryMethod.ONVIF -> R.string.discovery_method_onvif
                            DiscoveryMethod.PORT_SCAN -> R.string.discovery_method_rtsp_port
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.alreadyAdded) {
                    LabelPill(text = stringResource(R.string.discovery_added), color = HerzTheme.status.live)
                }
            }
        },
        modifier = modifier
            .clickable(enabled = !item.alreadyAdded, onClick = onClick)
            .alpha(if (item.alreadyAdded) 0.6f else 1f),
    )
}

@Composable
private fun LabelPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun DiscoveredDevice.subtitle(): String? {
    val device = listOfNotNull(manufacturer, model).joinToString(" ")
    return when {
        name != null && device.isNotBlank() -> "$host · $device"
        displayName == host -> null
        else -> host
    }
}

@Preview
@Composable
private fun DeviceRowPreview() {
    HerzTheme {
        DeviceRow(
            item = DiscoveredDeviceItem(
                device = DiscoveredDevice(
                    host = "192.168.1.20",
                    method = DiscoveryMethod.ONVIF,
                    name = "Gate",
                    manufacturer = "Tapo",
                    model = "C200",
                ),
                alreadyAdded = true,
            ),
            onClick = {},
        )
    }
}
