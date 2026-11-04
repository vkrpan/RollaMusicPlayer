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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

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

    // Phase 7 Task 5 (6b Task 4 review M4): the apply switch stays centred on its field's text box when an error
    // message shows under the field. The text box is the field's container: it starts at the field's top, and its
    // height is a field without supporting text (Title, the control row). Trivial stub: the switch pinned to the row's
    // top → both rows miss by half the container's spare height (5 dp here) → RED on the control. Mutation: centre the
    // switch on the whole field (the 6b Row(CenterVertically)) → the Year row's switch drops by half the error message
    // → RED. Control: the Title row, with no error, shows the same.
    @Test
    fun switchStaysCentredOnAnErrorRow() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { Screen(state = yearErrorState()) }
        }
        composeRule.onNodeWithText(YEAR_ERROR, useUnmergedTree = true)
            .assertExists("the Year row shows its error message")

        val titleField = composeRule.onNode(fieldNode("Title")).getUnclippedBoundsInRoot()
        val textBoxHeight = titleField.bottom - titleField.top
        val titleOffset = switchCentre("Title") - (titleField.top + textBoxHeight / 2)
        assertTrue(
            "control: the Title switch is centred on its field (off by $titleOffset)",
            abs(titleOffset.value) <= 1f,
        )

        val yearField = composeRule.onNode(fieldNode("Year")).getUnclippedBoundsInRoot()
        assertTrue(
            "the Year field is taller than its text box: its error shows under it",
            yearField.bottom - yearField.top > textBoxHeight + 1.dp,
        )
        val yearOffset = switchCentre("Year") - (yearField.top + textBoxHeight / 2)
        assertTrue(
            "the Year switch is centred on the field's text box, not on the box plus the error (off by $yearOffset)",
            abs(yearOffset.value) <= 1f,
        )
    }

    private fun switchCentre(label: String): Dp {
        val bounds = composeRule.onNodeWithContentDescription("Apply $label to every selected track")
            .getUnclippedBoundsInRoot()
        return (bounds.top + bounds.bottom) / 2
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
        val GENRE_FIELD = fieldNode("Genre")

        const val YEAR_ERROR = "Year must be blank or up to 4 digits."

        /** The [label] text field, as [GENRE_FIELD] matches Genre's. */
        fun fieldNode(label: String) =
            hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

        /** Two selected tracks: Title applied with no error, Year applied with an invalid value and its error. */
        fun yearErrorState(): BatchTagEditorUiState = BatchTagEditorUiState(
            songCount = 2,
            fields = BatchTagFields(
                title = BatchFieldState(value = "Song", applied = true),
                year = BatchFieldState(value = "20x1", applied = true),
            ),
            yearError = YEAR_ERROR,
        )

        /** Two selected tracks with only Genre holding a value ("Rock"), switched on or off. */
        fun genreState(applied: Boolean): BatchTagEditorUiState = BatchTagEditorUiState(
            songCount = 2,
            fields = BatchTagFields(genre = BatchFieldState(value = "Rock", applied = applied)),
        )
    }
}
