package com.rolla.musicplayer.core.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongSelectionStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toggleAddsThenRemovesAndClearEmpties() {
        val state = SongSelectionState(emptySet())
        state.toggle("a")
        state.toggle("b")
        assertEquals(setOf("a", "b"), state.selectedIds)
        assertTrue(state.isActive)
        state.toggle("a")
        assertFalse(state.isSelected("a"))
        assertEquals(1, state.count)
        state.clear()
        assertFalse(state.isActive)
    }

    // Ruling P23: a locked selection ignores toggle() but still takes clear() and retainAll(). Control: toggle works
    // before the lock. Trivial stub: lock ignored → "b" is added while locked → RED. Mutation: retainAll also honours
    // the lock → "c" survives → RED.
    @Test
    fun lockFreezesToggleButNotClearOrRetainAll() {
        val state = SongSelectionState(emptySet())
        state.toggle("a")
        state.toggle("c")
        assertEquals("control: toggle works unlocked", setOf("a", "c"), state.selectedIds)

        state.isLocked = true
        state.toggle("b")
        state.toggle("a")
        assertEquals("a locked selection ignores toggle", setOf("a", "c"), state.selectedIds)
        state.retainAll(setOf("a"))
        assertEquals("retainAll still applies while locked", setOf("a"), state.selectedIds)
        state.clear()
        assertFalse("clear still applies while locked", state.isActive)

        state.isLocked = false
        state.toggle("b")
        assertEquals("unlocked again, toggle works", setOf("b"), state.selectedIds)
    }

    @Test
    fun retainAllDropsIdsMissingFromTheGivenSet() {
        val state = SongSelectionState(setOf("a", "b", "c"))
        state.retainAll(setOf("a", "c", "z"))
        assertEquals(setOf("a", "c"), state.selectedIds)
        assertEquals(2, state.count)
        state.retainAll(emptySet())
        assertFalse(state.isActive)
    }

    @Test
    fun retainAllKeepsSelectionOrder() {
        // Selected in reverse of hash order: a hash-ordered prune would reorder the survivors to a, b, c.
        val state = SongSelectionState(emptySet())
        listOf("c", "b", "a", "d").forEach(state::toggle)
        state.retainAll(setOf("a", "b", "c"))
        assertEquals(listOf("c", "b", "a"), state.selectedIds.toList())
    }

    @Test
    fun selectionSurvivesRecreation() {
        val tester = StateRestorationTester(composeRule)
        lateinit var state: SongSelectionState
        tester.setContent { state = rememberSongSelectionState() }
        composeRule.runOnIdle { state.toggle("42") }
        tester.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { assertTrue(state.isSelected("42")) }
    }
}
