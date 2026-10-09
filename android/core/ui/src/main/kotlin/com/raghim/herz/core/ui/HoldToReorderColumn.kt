// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Hold an empty part of a row, then drag it. Buttons inside the row keep their own taps.
 * [onCommit] runs when the finger lifts, with the rows in their new order.
 */
@Composable
fun <T> HoldToReorderColumn(
    items: List<T>,
    key: (T) -> Any,
    onCommit: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, drag: Modifier) -> Unit,
) {
    val draft = remember { ReorderDraft(items) }
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(1f) }
    val gap = with(LocalDensity.current) { 8.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(items) {
        draft.onSource(items)
        order = draft.visible
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { item ->
            val id = key(item)
            val dragging = draggingKey == id
            key(id) {
                Column(
                    Modifier
                        .onSizeChanged { itemHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) offsetY else 0f },
                ) {
                    itemContent(
                        item,
                        Modifier.pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draft.begin()
                                    draggingKey = id
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (draggingKey != id) return@detectDragGesturesAfterLongPress
                                    offsetY += amount.y
                                    val index = order.indexOfFirst { key(it) == id }
                                    if (index < 0) return@detectDragGesturesAfterLongPress
                                    val step = itemHeight + gap
                                    val target = (index + (offsetY / step).toInt()).coerceIn(0, order.lastIndex)
                                    if (target != index) {
                                        val next = order.toMutableList().apply { add(target, removeAt(index)) }
                                        draft.show(next)
                                        order = next
                                        offsetY -= (target - index) * step
                                    }
                                },
                                onDragEnd = {
                                    if (draggingKey != null) onCommit(draft.finish())
                                    draggingKey = null
                                    offsetY = 0f
                                },
                                onDragCancel = {
                                    draft.cancel()
                                    order = draft.visible
                                    draggingKey = null
                                    offsetY = 0f
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * The rows on screen versus the order last saved. A cancelled drag puts the rows back
 * immediately, so the list never keeps an order that was not saved.
 */
internal class ReorderDraft<T>(initial: List<T>) {
    var saved: List<T> = initial
        private set
    var visible: List<T> = initial
        private set
    private var dragging = false

    fun onSource(items: List<T>) {
        saved = items
        if (!dragging) visible = items
    }

    fun begin() {
        dragging = true
    }

    fun show(next: List<T>) {
        visible = next
    }

    /** Finger lifted. The visible order becomes the saved one. */
    fun finish(): List<T> {
        dragging = false
        saved = visible
        return visible
    }

    /** The gesture was interrupted. Show the last saved order again. */
    fun cancel() {
        dragging = false
        visible = saved
    }
}
