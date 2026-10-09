package com.rolla.musicplayer.home

import com.rolla.musicplayer.core.ui.SongSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's batch "Edit tags" mapping. Plain JVM: SongSelectionState's constructor is internal to :core:ui, so each
 * selection is built through its public Saver (the same path process-death restore takes).
 */
class EditTagsForSelectionTest {

    @Test
    fun mapsIdsWithToLongOrNullAndPassesThemToTheCallback() {
        val selection = selectionOf("12", "not-a-number", "7")
        val received = mutableListOf<List<Long>>()

        editTagsForSelection(selection) { received += it }

        assertEquals("The callback must fire once", 1, received.size)
        assertEquals("Malformed ids are dropped, the rest parsed", setOf(12L, 7L), received.single().toSet())
        assertEquals(2, received.single().size)
    }

    @Test
    fun clearsTheSelectionAfterTheCallback() {
        val selection = selectionOf("1", "2")
        var activeDuringCallback = false

        editTagsForSelection(selection) { activeDuringCallback = selection.isActive }

        assertTrue("The callback must run before the selection is cleared", activeDuringCallback)
        assertFalse("The selection must be cleared afterwards", selection.isActive)
    }

    @Test
    fun allMalformedSelectionNeitherCallsBackNorClears() {
        val selection = selectionOf("x", "y")
        var calls = 0

        editTagsForSelection(selection) { calls++ }

        assertEquals("An empty batch must not navigate", 0, calls)
        assertEquals("The selection must stay as it was", setOf("x", "y"), selection.selectedIds)
    }

    private fun selectionOf(vararg ids: String): SongSelectionState =
        checkNotNull(SongSelectionState.Saver.restore(ids.toList()))
}
