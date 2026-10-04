// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview.door

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raghim.herz.core.common.AppError
import com.raghim.herz.core.designsystem.component.HoldToConfirmButton
import com.raghim.herz.core.designsystem.icon.HerzIcons
import com.raghim.herz.core.designsystem.theme.HerzTheme
import com.raghim.herz.core.model.DoorContact
import com.raghim.herz.core.model.LockState
import com.raghim.herz.feature.liveview.R
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.time.Duration

private val SidePanelWidth = 320.dp
private val HandleHeight = 24.dp
private val HeaderHeight = 44.dp
private val RowHeight = 68.dp

/**
 * Puts the door list next to the wall without covering it. Phones in portrait get a
 * bottom panel that peeks with the first door and drags up; wide or landscape screens
 * get a fixed side column. [content] receives the padding that keeps it clear of the panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoorPanelLayout(
    state: DoorPanelUiState,
    onIntent: (DoorPanelIntent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (state.isLoading && state.doors.isEmpty()) {
        Box(modifier) { content(PaddingValues()) }
        return
    }
    BoxWithConstraints(modifier) {
        val side = maxWidth >= 600.dp || maxWidth > maxHeight
        if (side) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content(PaddingValues()) }
                VerticalDivider()
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(SidePanelWidth)) {
                    Column {
                        DoorPanelHeader(state)
                        DoorList(state, onIntent, Modifier.weight(1f))
                    }
                }
            }
        } else {
            val peek = HandleHeight + HeaderHeight + RowHeight
            BottomSheetScaffold(
                scaffoldState = rememberBottomSheetScaffoldState(),
                sheetPeekHeight = peek,
                sheetDragHandle = { DragHandle() },
                sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                sheetShadowElevation = 0.dp,
                containerColor = Color.Transparent,
                sheetContent = {
                    DoorPanelHeader(state)
                    DoorList(state, onIntent, Modifier.fillMaxWidth())
                },
            ) {
                content(PaddingValues(bottom = peek))
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(Modifier.fillMaxWidth().height(HandleHeight), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun DoorPanelHeader(state: DoorPanelUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = 16.dp),
    ) {
        Icon(HerzIcons.DoorFront, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.liveview_doors_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = pluralStringResource(R.plurals.liveview_doors_count, state.doors.size, state.doors.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state.unlockedCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.liveview_doors_unlocked_count, state.unlockedCount, state.unlockedCount),
                style = MaterialTheme.typography.labelMedium,
                color = HerzTheme.status.live,
            )
        }
    }
}

@Composable
private fun DoorList(state: DoorPanelUiState, onIntent: (DoorPanelIntent) -> Unit, modifier: Modifier) {
    if (state.doors.isEmpty()) {
        Text(
            text = stringResource(R.string.liveview_doors_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 8.dp)) {
        items(state.doors, key = { it.door.id }) { item ->
            DoorRow(
                item = item,
                holdToOpen = state.holdToOpen,
                onOpen = { onIntent(DoorPanelIntent.Open(item.door.id)) },
                onLock = { onIntent(DoorPanelIntent.Lock(item.door.id)) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun DoorRow(
    item: DoorItem,
    holdToOpen: Duration,
    onOpen: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val door = item.door
    val secondsLeft by rememberSecondsLeft(door.unlockedUntil)
    val status = doorStatus(item, secondsLeft)
    val open = door.lock == LockState.UNLOCKED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (open) HerzTheme.status.live.copy(alpha = 0.18f) else Color.Transparent)
            .height(RowHeight)
            .padding(horizontal = 8.dp),
    ) {
        Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(door.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(door.area, status.text).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = status.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (open && item.command == null) {
            FilledTonalButton(onClick = onLock, modifier = Modifier.width(ActionWidth)) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.liveview_door_lock))
            }
        } else {
            HoldToConfirmButton(
                label = status.action,
                holdingLabel = stringResource(R.string.liveview_door_keep_holding),
                holdDuration = holdToOpen,
                onConfirm = onOpen,
                enabled = item.canOpen,
                icon = if (item.canOpen) HerzIcons.LockOpen else null,
                modifier = Modifier.width(ActionWidth),
            )
        }
    }
}

private val ActionWidth: Dp = 148.dp

private class DoorStatus(val text: String, val action: String, val icon: ImageVector, val color: Color)

@Composable
private fun doorStatus(item: DoorItem, secondsLeft: Long?): DoorStatus {
    val door = item.door
    val colors = HerzTheme.status
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hold = stringResource(R.string.liveview_door_hold)
    return when {
        !door.isOnline -> DoorStatus(
            stringResource(R.string.liveview_door_status_offline),
            stringResource(R.string.liveview_door_status_offline),
            Icons.Default.Lock,
            colors.offline,
        )
        item.command == DoorCommand.Confirming -> DoorStatus(
            stringResource(R.string.liveview_door_status_confirm),
            stringResource(R.string.liveview_door_action_confirm),
            Icons.Default.Lock,
            colors.connecting,
        )
        item.command == DoorCommand.Unlocking -> DoorStatus(
            stringResource(R.string.liveview_door_status_unlocking),
            stringResource(R.string.liveview_door_status_unlocking),
            Icons.Default.Lock,
            colors.connecting,
        )
        door.lock == LockState.UNLOCKED -> DoorStatus(
            text = when {
                door.contact == DoorContact.OPEN -> stringResource(R.string.liveview_door_status_open)
                secondsLeft != null -> stringResource(R.string.liveview_door_status_unlocked_seconds, secondsLeft)
                else -> stringResource(R.string.liveview_door_status_unlocked)
            },
            action = stringResource(R.string.liveview_door_status_unlocked),
            icon = if (door.contact == DoorContact.OPEN) HerzIcons.DoorOpen else HerzIcons.LockOpen,
            color = colors.live,
        )
        door.contact == DoorContact.OPEN -> DoorStatus(
            stringResource(R.string.liveview_door_status_locked_open),
            hold,
            HerzIcons.DoorOpen,
            colors.alarm,
        )
        else -> DoorStatus(stringResource(R.string.liveview_door_status_locked), hold, Icons.Default.Lock, muted)
    }
}

/** Whole seconds until [until], ticking; null when there is no deadline. */
@Composable
private fun rememberSecondsLeft(until: Instant?) = produceState<Long?>(initialValue = until?.let(::secondsUntil), until) {
    if (until == null) {
        value = null
        return@produceState
    }
    while (true) {
        val left = secondsUntil(until)
        value = left
        if (left <= 0) break
        delay(250)
    }
}

private fun secondsUntil(until: Instant): Long {
    val millis = java.time.Duration.between(Instant.now(), until).toMillis()
    return ((millis + 999) / 1000).coerceAtLeast(0)
}

internal fun DoorPanelEffect.message(resources: Resources): String = when (this) {
    is DoorPanelEffect.OpenFailed -> when (error) {
        AppError.Offline -> resources.getString(R.string.liveview_door_error_offline, doorName)
        AppError.Unreachable, AppError.Timeout -> resources.getString(R.string.liveview_door_error_unreachable, doorName)
        AppError.Unauthorized -> resources.getString(R.string.liveview_door_error_refused, doorName)
        AppError.Expired -> resources.getString(R.string.liveview_door_error_expired, doorName)
        AppError.AuthenticationUnavailable -> resources.getString(R.string.liveview_door_error_no_biometric)
        AppError.KeyInvalidated -> resources.getString(R.string.liveview_door_error_key_invalidated)
        AppError.Cancelled, AppError.NotFound, AppError.NoStreamFound, is AppError.Unknown ->
            resources.getString(R.string.liveview_door_error_unknown, doorName)
    }
}
