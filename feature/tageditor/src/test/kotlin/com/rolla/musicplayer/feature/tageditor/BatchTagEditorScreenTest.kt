package com.rolla.musicplayer.feature.tageditor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The batch tag editor on the One UI kit (Phase 6b ruling 6) and its "tracks" copy (ruling 7). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchTagEditorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Ruling 6/7: the top bar reads "Edit ${tracksCountLabel(n)}", and the "Editing N songs" line is gone. Trivial
    // stub: a static title → "Edit 1 track" missing. Mutation: "Edit $songCount songs" → RED.
    @Test
    fun theTitleCountsTracks() {
        var state by mutableStateOf(BatchTagEditorUiState(songCount = 1))
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { Screen(state = state) }
        }
        assertEquals("one selected track reads Edit 1 track", 1, textCount("Edit 1 track"))

        composeRule.runOnIdle { state = state.copy(songCount = 3) }
        assertEquals("three selected tracks read Edit 3 tracks", 1, textCount("Edit 3 tracks"))
        // The "Edit 3 tracks" assertion above is the control of this absence check: the title renders, counting
        // tracks.
        assertEquals(
            "no text on the batch editor says songs",
            0,
            composeRule.onAllNodes(hasText("songs", substring = true, ignoreCase = true), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    // Review focus 4: a switched-off field cannot be edited and its switch turns it on; switched back on, the field is
    // editable again with its value. Trivial stub: an inert toggle → no (GENRE, true) report. Mutations: a Checkbox
    // role (Role.Checkbox) → RED on the role; the field always enabled → RED on the first assertion.
    @Test
    fun aSwitchedOffFieldIsLockedAndItsSwitchTurnsItOn() {
        var state by mutableStateOf(genreState(applied = false))
        val toggles = mutableListOf<Pair<TagField, Boolean>>()
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Screen(state = state, onFieldApplyToggled = { field, applied -> toggles += field to applied })
            }
        }
        composeRule.onNode(GENRE_FIELD).performScrollTo()
            .assert(isNotEnabled()) { "the switched-off Genre field cannot be edited" }
        val toggle = composeRule.onNodeWithContentDescription("Apply Genre to every selected track")
        toggle.performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)) {
                "the Genre apply toggle is a switch"
            }
            .assert(isOff()) { "the Genre apply switch is off" }
        toggle.performClick()
        composeRule.runOnIdle {
            assertEquals("the switch reports Genre switched on", listOf(TagField.GENRE to true), toggles.toList())
        }

        composeRule.runOnIdle { state = genreState(applied = true) }
        composeRule.onNode(GENRE_FIELD)
            .assert(isEnabled()) { "the switched-on Genre field can be edited" }
            .assert(hasText("Rock")) { "the switched-on Genre field keeps its value" }
    }

    @Composable
    private fun Screen(
        state: BatchTagEditorUiState,
        onFieldApplyToggled: (TagField, Boolean) -> Unit = { _, _ -> },
    ) {
        BatchTagEditorScreen(
            uiState = state,
            onFieldValueChanged = { _, _ -> },
            onFieldApplyToggled = onFieldApplyToggled,
            onSaveClick = {},
            onNavigateUp = {},
            onDismissMessage = {},
        )
    }

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        /**
         * The Genre text field (its label and an editable value), not its apply switch. EditableText, not SetText: a
         * disabled field drops its SetText action.
         */
        val GENRE_FIELD = hasText("Genre") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

        /** Two selected tracks with only Genre holding a value ("Rock"), switched on or off. */
        fun genreState(applied: Boolean): BatchTagEditorUiState = BatchTagEditorUiState(
            songCount = 2,
            fields = BatchTagFields(genre = BatchFieldState(value = "Rock", applied = applied)),
        )
    }
}
