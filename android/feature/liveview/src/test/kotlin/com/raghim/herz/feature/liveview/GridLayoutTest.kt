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
