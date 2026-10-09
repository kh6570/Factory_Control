// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderDraftTest {

    @Test
    fun aCancelledDragReturnsToTheSavedOrderImmediately() {
        val draft = ReorderDraft(listOf("a", "b", "c"))
        draft.begin()
        draft.show(listOf("c", "a", "b"))

        draft.cancel()

        assertEquals(listOf("a", "b", "c"), draft.visible)
    }

    @Test
    fun aNewListDoesNotReplaceRowsWhileAFingerIsDown() {
        val draft = ReorderDraft(listOf("a", "b"))
        draft.begin()
        draft.show(listOf("b", "a"))

        draft.onSource(listOf("a", "b"))

        assertEquals(listOf("b", "a"), draft.visible)
    }

    @Test
    fun liftingTheFingerKeepsTheNewOrder() {
        val draft = ReorderDraft(listOf("a", "b", "c"))
        draft.begin()
        draft.show(listOf("b", "a", "c"))

        assertEquals(listOf("b", "a", "c"), draft.finish())

        draft.cancel()
        assertEquals(listOf("b", "a", "c"), draft.visible)
    }
}
