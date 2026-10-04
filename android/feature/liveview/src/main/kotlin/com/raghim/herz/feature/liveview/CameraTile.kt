// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raghim.herz.core.designsystem.component.LiveStatus
import com.raghim.herz.core.designsystem.component.StatusBadge
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamState
import com.raghim.herz.core.video.PlayerError
import com.raghim.herz.core.video.PlayerState
import com.raghim.herz.core.video.VideoPlayer
import com.raghim.herz.core.video.VideoScale

internal val WallBackground = Color(0xFF07090C)
private val TileShape = RoundedCornerShape(6.dp)

/**
 * One camera on the wall. [isLive] = inside the live budget; [showVideo] = this tile may attach
 * the player's surface (false while the fullscreen view owns it).
 */
@Composable
internal fun CameraTile(
    camera: ActiveCamera,
    player: VideoPlayer,
    isLive: Boolean,
    showVideo: Boolean,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState by player.state.collectAsStateWithLifecycle()
    val isAlarm = camera.source == SessionSource.ALARM
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(TILE_ASPECT_RATIO)
            .clip(TileShape)
            .background(Color.Black)
            .then(if (isAlarm) Modifier.border(2.dp, HerzTheme.status.alarm, TileShape) else Modifier)
            .clickable(onClick = onTap),
    ) {
        if (isLive) {
            if (showVideo) player.Surface(Modifier.matchParentSize(), VideoScale.Fill)
            (playerState as? PlayerState.Error)?.let { error ->
                ErrorOverlay(error.reason, onRetry, compact = true, modifier = Modifier.matchParentSize())
            }
        } else {
            PausedPlaceholder(Modifier.matchParentSize())
        }

        CameraStatusBadge(
            isLive = isLive,
            playerState = playerState,
            streamState = camera.state,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = camera.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            SourceBadge(camera.source)
        }
    }
}

@Composable
internal fun SourceBadge(source: SessionSource, modifier: Modifier = Modifier) {
    val alarm = source == SessionSource.ALARM
    Text(
        text = stringResource(if (alarm) R.string.liveview_source_alarm else R.string.liveview_source_manual),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(
                if (alarm) HerzTheme.status.alarm else Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun CameraStatusBadge(
    isLive: Boolean,
    playerState: PlayerState,
    streamState: StreamState,
    modifier: Modifier = Modifier,
) {
    val (status, label) = when {
        !isLive -> LiveStatus.Idle to R.string.liveview_status_paused
        playerState is PlayerState.Error -> playerState.reason.badge()
        playerState == PlayerState.Playing -> LiveStatus.Live to R.string.liveview_status_live
        streamState == StreamState.ERROR -> LiveStatus.Offline to R.string.liveview_status_offline
        else -> LiveStatus.Connecting to R.string.liveview_status_connecting
    }
    StatusBadge(status, stringResource(label), modifier)
}

private fun PlayerError.badge(): Pair<LiveStatus, Int> = when (this) {
    PlayerError.Unauthorized -> LiveStatus.Alarm to R.string.liveview_status_unauthorized
    PlayerError.Unsupported -> LiveStatus.Alarm to R.string.liveview_status_unsupported
    PlayerError.Unreachable -> LiveStatus.Offline to R.string.liveview_status_unreachable
    PlayerError.Lost -> LiveStatus.Offline to R.string.liveview_status_lost
    PlayerError.Unknown -> LiveStatus.Offline to R.string.liveview_status_error
}

@StringRes
internal fun PlayerError.message(): Int = when (this) {
    PlayerError.Unreachable -> R.string.liveview_error_unreachable
    PlayerError.Unauthorized -> R.string.liveview_error_unauthorized
    PlayerError.Unsupported -> R.string.liveview_error_unsupported
    PlayerError.Lost -> R.string.liveview_error_lost
    PlayerError.Unknown -> R.string.liveview_error_unknown
}

/** Error text and a Retry button. [compact] hides the text when the tile is too small for it. */
@Composable
internal fun ErrorOverlay(
    reason: PlayerError,
    onRetry: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        val showText = !compact || maxHeight >= 110.dp
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showText) {
                Text(
                    text = stringResource(reason.message()),
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(if (compact) 6.dp else 12.dp))
            }
            FilledTonalButton(
                onClick = onRetry,
                contentPadding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else ButtonPadding,
            ) {
                Text(stringResource(R.string.liveview_retry))
            }
        }
    }
}

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)

@Composable
private fun PausedPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF14181E)), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.liveview_paused_tile),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = 220.dp),
        )
    }
}
