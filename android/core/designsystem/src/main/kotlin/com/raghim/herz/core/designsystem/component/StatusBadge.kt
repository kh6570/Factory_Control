// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.designsystem.theme.HerzTheme

enum class LiveStatus { Live, Connecting, Offline, Idle, Alarm }

@Composable
fun LiveStatus.color(): Color = when (this) {
    LiveStatus.Live -> HerzTheme.status.live
    LiveStatus.Connecting -> HerzTheme.status.connecting
    LiveStatus.Offline -> HerzTheme.status.offline
    LiveStatus.Idle -> HerzTheme.status.idle
    LiveStatus.Alarm -> HerzTheme.status.alarm
}

/** Small pill with a colored dot. Readable on top of video. */
@Composable
fun StatusBadge(
    status: LiveStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(status.color(), CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Preview
@Composable
private fun StatusBadgePreview() {
    HerzTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(LiveStatus.Live, "LIVE")
            StatusBadge(LiveStatus.Connecting, "Connecting")
            StatusBadge(LiveStatus.Alarm, "ALARM")
        }
    }
}
