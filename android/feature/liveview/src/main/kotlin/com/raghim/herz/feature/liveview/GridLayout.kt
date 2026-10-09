// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

/** Columns from the layout table in system design 6.3. [wide] = landscape or width >= 600 dp. */
fun gridColumns(count: Int, wide: Boolean): Int = when {
    count <= 1 -> 1
    count == 2 -> if (wide) 2 else 1
    count <= 4 -> 2
    count <= 6 -> if (wide) 3 else 2
    count <= 9 -> 3
    else -> if (wide) 4 else 3
}

/**
 * Columns actually used. [chosen] is the user's pick (1 to 4); null keeps [gridColumns].
 * The result never exceeds [count], so 2 cameras with a choice of 4 still sit on one row.
 */
fun resolvedGridColumns(count: Int, chosen: Int?, wide: Boolean): Int {
    if (count <= 0) return 1
    val wanted = chosen ?: return gridColumns(count, wide)
    return wanted.coerceIn(1, count)
}

/**
 * Tiles allowed to decode live video. Alarm cameras take slots first, even when scrolled off
 * screen, then the first [budget] remaining tiles on screen. Before the grid has been laid out
 * [visibleIds] is empty, so the first tiles of the list fill whatever budget is left.
 */
fun liveTileIds(
    visibleIds: List<String>,
    allIds: List<String>,
    budget: Int,
    alarmIds: Set<String> = emptySet(),
): Set<String> {
    val allowed = budget.coerceAtLeast(0)
    val alarms = allIds.filter { it in alarmIds }.take(allowed)
    val reserved = alarms.toSet()
    val pool = visibleIds.ifEmpty { allIds }.filter { it !in reserved }
    return (alarms + pool.take((allowed - alarms.size).coerceAtLeast(0))).toSet()
}

internal const val TILE_ASPECT_RATIO = 16f / 9f

/** Narrowest grid, as a share of the available width, before we scroll instead of shrinking. */
private const val MIN_FIT_FRACTION = 0.5f

/**
 * Width in dp for the grid so all rows of 16:9 tiles fit the height. Returns [maxWidth] when
 * the tiles already fit at full width, or when fitting would make them too small (then the grid scrolls).
 */
internal fun fittedGridWidth(
    count: Int,
    columns: Int,
    maxWidth: Float,
    maxHeight: Float,
    gap: Float,
): Float {
    if (count <= 0 || columns <= 0) return maxWidth
    val rows = (count + columns - 1) / columns
    val tileHeight = (maxHeight - gap * (rows - 1)) / rows
    if (tileHeight <= 0f) return maxWidth
    val width = tileHeight * TILE_ASPECT_RATIO * columns + gap * (columns - 1)
    return when {
        width >= maxWidth -> maxWidth
        width >= maxWidth * MIN_FIT_FRACTION -> width
        else -> maxWidth
    }
}
