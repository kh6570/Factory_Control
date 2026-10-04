// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * A button that fires only after a continuous hold of [holdDuration]. Releasing early, moving the
 * finger past touch slop, leaving the button, or a parent scroll or swipe taking the gesture cancels
 * the hold, so a scroll through a list or a swipe between cameras can never trigger it.
 * A light tick plays every 500 ms while holding and a stronger one on confirm.
 *
 * Accessibility services get a plain click action. Callers must still require a second
 * confirmation (the door flow asks for a fingerprint), so this is not a weaker path.
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    holdingLabel: String,
    holdDuration: Duration,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val holdMillis = holdDuration.inWholeMilliseconds.toInt().coerceAtLeast(1)

    val colors = MaterialTheme.colorScheme
    val container = if (enabled) colors.secondaryContainer else colors.surfaceContainerHigh
    val content = if (enabled) colors.onSecondaryContainer else colors.onSurface.copy(alpha = 0.38f)
    val fill = colors.primary.copy(alpha = 0.55f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .drawBehind {
                if (progress.value > 0f) {
                    drawRect(fill, topLeft = Offset.Zero, size = Size(size.width * progress.value, size.height))
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (enabled) {
                    onClick(label = label) {
                        currentOnConfirm()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled, holdMillis) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val slop = viewConfiguration.touchSlop
                    holding = true
                    val hold = scope.launch {
                        val ticks = launch {
                            while (isActive) {
                                delay(TICK_MS)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        ticks.cancel()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnConfirm()
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val moved = (change.position - down.position).getDistance() > slop
                        val outside = change.position.x !in 0f..size.width.toFloat() ||
                            change.position.y !in 0f..size.height.toFloat()
                        if (!change.pressed || change.isConsumed || moved || outside) break
                    }
                    val confirmed = hold.isCompleted && !hold.isCancelled
                    hold.cancel()
                    holding = false
                    scope.launch {
                        if (confirmed) progress.snapTo(0f) else progress.animateTo(0f, tween(RESET_MS))
                    }
                }
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (icon != null && !holding) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(
                text = if (holding) holdingLabel else label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TICK_MS = 500L
private const val RESET_MS = 150
