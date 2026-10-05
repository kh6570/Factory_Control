// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.feature.liveview

import org.junit.Assert.assertEquals
import org.junit.Test

class GridLayoutTest {

    private val counts = listOf(1, 2, 3, 4, 5, 6, 7, 9, 10, 16)

    @Test
    fun `portrait columns follow the layout table`() {
        val expected = listOf(1, 1, 2, 2, 2, 2, 3, 3, 3, 3)
        assertEquals(expected, counts.map { gridColumns(it, wide = false) })
    }

    @Test
    fun `wide columns follow the layout table`() {
        val expected = listOf(1, 2, 2, 2, 3, 3, 3, 3, 4, 4)
        assertEquals(expected, counts.map { gridColumns(it, wide = true) })
    }

    @Test
    fun `a chosen column count wraps the remainder onto the next row`() {
        assertEquals(4, resolvedGridColumns(count = 6, chosen = 4, wide = false))
        assertEquals(2, (6 + 4 - 1) / 4)
    }

    @Test
    fun `a chosen count never leaves empty columns when there are fewer cameras`() {
        assertEquals(2, resolvedGridColumns(count = 2, chosen = 4, wide = false))
    }

    @Test
    fun `no choice keeps the layout table`() {
        assertEquals(gridColumns(6, wide = false), resolvedGridColumns(6, chosen = null, wide = false))
    }

    @Test
    fun `live budget goes to the tiles on screen after scrolling`() {
        val all = (1..20).map { "cam$it" }
        val onScreen = (10..15).map { "cam$it" }

        assertEquals(setOf("cam10", "cam11", "cam12", "cam13"), liveTileIds(onScreen, all, budget = 4))
    }

    @Test
    fun `before layout the first tiles of the list are live`() {
        val all = (1..6).map { "cam$it" }

        assertEquals(setOf("cam1", "cam2"), liveTileIds(emptyList(), all, budget = 2))
    }

    @Test
    fun `no budget means nothing is live`() {
        assertEquals(emptySet<String>(), liveTileIds(listOf("cam1"), listOf("cam1"), budget = 0))
    }

    @Test
    fun `no cameras still gives one column`() {
        assertEquals(1, gridColumns(0, wide = false))
        assertEquals(1, gridColumns(0, wide = true))
    }

    @Test
    fun `grid keeps full width when tiles already fit`() {
        // Portrait phone, 4 cameras in 2 x 2: tiles are short, nothing to shrink.
        assertEquals(400f, fittedGridWidth(4, 2, maxWidth = 400f, maxHeight = 700f, gap = 4f), 0.01f)
    }

    @Test
    fun `grid shrinks so all rows fit a landscape screen`() {
        // 2 rows of (330 - 4) / 2 = 163 dp tiles -> 2 * 163 * 16/9 + 4.
        val width = fittedGridWidth(4, 2, maxWidth = 800f, maxHeight = 330f, gap = 4f)
        assertEquals(2 * 163f * 16f / 9f + 4f, width, 0.01f)
    }

    @Test
    fun `grid scrolls instead of shrinking tiles too far`() {
        // 60 cameras in 3 columns on a phone would need a grid narrower than half the width.
        assertEquals(400f, fittedGridWidth(60, 3, maxWidth = 400f, maxHeight = 700f, gap = 4f), 0.01f)
    }
}
