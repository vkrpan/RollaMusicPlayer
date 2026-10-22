package com.rolla.musicplayer.feature.tageditor

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The single tag editor on the One UI kit (Phase 6b ruling 6) and its "track" copy (ruling 7). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagEditorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Ruling 6: the fields are filled OneUiTextFields (surfaceContainerHigh) on a surfaceContainer card. An
    // OutlinedTextField is transparent, and its outline starts 8 dp below its top edge, so the probe 2 dp in reads the
    // card. Control: 4 dp above the field is the card's padding. Trivial stub: OutlinedTextField kept → the inside
    // probe reads surfaceContainer. Mutation: revert to OutlinedTextField → RED.
    @Test
    fun fieldsAreFilledOneUiFields() {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                scheme = MaterialTheme.colorScheme
                Screen(state = loadedState())
            }
        }
        composeRule.onNode(TITLE_FIELD).performScrollTo()
        val bitmap = composeRule.drawRoot()
        val field = composeRule.onNode(TITLE_FIELD).getUnclippedBoundsInRoot()
        val centreX = (field.left + field.right) / 2
        val card = bitmap.pixelAt(centreX, field.top - CARD_PROBE)
        assertTrue(
            "4 dp above the Title field is the surfaceContainer card (control), was ${card.hex()}",
            isColorNear(scheme.surfaceContainer, card),
        )
        val inside = bitmap.pixelAt(centreX, field.top + FIELD_PROBE)
        assertTrue(
            "2 dp inside the Title field's top edge is its surfaceContainerHigh container, was ${inside.hex()}",
            isColorNear(scheme.surfaceContainerHigh, inside),
        )
    }

    // Ruling 6: errors go through isError and supportingText, so the message is drawn in destructiveText (6a ruling
    // P2). Dark error (#FF5A5A) sits within the pixel tolerance of dark destructiveText (#FF6060), so the fixture
    // repaints error far from every colour on screen, as OneUiInputsTest does. Trivial stub: errors dropped → the
    // message never shows after the flip. Mutations: omit supportingText → RED on the count; isError = false on the
    // Year field (the message then draws in onSurfaceVariant) → RED on the destructiveText ink (T4 M1).
    @Test
    fun aYearErrorShowsItsMessage() {
        var state by mutableStateOf(loadedState())
        var gray = Color.Unspecified
        var destructive = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(error = ERROR_SEPARATOR)) {
                    gray = MaterialTheme.colorScheme.onSurfaceVariant
                    destructive = MaterialTheme.colorScheme.destructiveText
                    Screen(state = state)
                }
            }
        }
        assertEquals("no year error text while yearError is null", 0, textCount(YEAR_ERROR))

        composeRule.runOnIdle {
            state = state.copy(tags = state.tags.copy(year = "19755"), yearError = YEAR_ERROR, canSave = false)
        }
        composeRule.onNodeWithText(YEAR_ERROR, useUnmergedTree = true).performScrollTo()
        assertEquals("the year error text shows under the Year field", 1, textCount(YEAR_ERROR))
        val ink = yearErrorInk()
        assertTrue("the year error text is drawn in destructiveText", ink.any { isColorNear(destructive, it) })
        assertFalse("the year error text is not onSurfaceVariant", ink.any { isColorNear(gray, it) })
        assertFalse("the year error text never draws error", ink.any { isColorNear(ERROR_SEPARATOR, it) })
    }

    // Ruling 6: Save is a filled pill with loading = isSaving and loadingDescription "Saving"; Cancel locks while
    // saving. Trivial stub: isSaving ignored → "Save" stays after the flip. Mutation: pass loading = false → RED.
    @Test
    fun savingShowsProgressAndLocksCancel() {
        var state by mutableStateOf(loadedState())
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { Screen(state = state) }
        }
        composeRule.onNodeWithText("Cancel").performScrollTo()
        assertEquals("the Save label shows while idle", 1, textCount("Save"))
        composeRule.onNodeWithText("Cancel").assert(isEnabled()) { "Cancel is enabled while idle" }

        composeRule.runOnIdle { state = state.copy(isSaving = true, canSave = false) }
        assertEquals("the Save label is swapped for the progress ring while saving", 0, textCount("Save"))
        assertEquals(
            "the saving button is described as Saving",
            1,
            composeRule.onAllNodesWithContentDescription("Saving").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText("Cancel").assert(isNotEnabled()) { "Cancel is locked while saving" }
    }

    // Ruling 6: the top bar's back and the Cancel pill both navigate up, and Cancel never saves. Trivial stub: no
    // callbacks → 0 navigations. Mutation: Cancel wired to save → 1 navigation and 1 save → RED.
    @Test
    fun backAndCancelBothNavigateUp() {
        var ups = 0
        var saves = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Screen(state = loadedState(), onSaveClick = { saves++ }, onNavigateUp = { ups++ })
            }
        }
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("Navigate up and Cancel each navigate up once", 2, ups)
            assertEquals("Cancel never saves", 0, saves)
        }
    }

    // Ruling 7: "Couldn't load this track's tags.", and the error state's Back (a tonal pill) navigates up.
    // Control: the form (its Title field) shows before the flip. Trivial stub: the form renders regardless → no
    // message. Mutation: the old "song's" copy → RED.
    @Test
    fun aLoadFailureSaysTrackAndOffersBack() {
        var state by mutableStateOf(loadedState())
        var ups = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { Screen(state = state, onNavigateUp = { ups++ }) }
        }
        assertEquals("the form shows its Title field before the load fails (control)", 1, nodeCount(TITLE_FIELD))

        composeRule.runOnIdle { state = TagEditorUiState(isLoading = false, loadFailed = true) }
        assertEquals("the load failure names a track", 1, textCount("Couldn't load this track's tags."))
        assertEquals("the form is gone after a load failure", 0, nodeCount(TITLE_FIELD))
        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle { assertEquals("Back navigates up once", 1, ups) }
    }

    @Composable
    private fun Screen(
        state: TagEditorUiState,
        onSaveClick: () -> Unit = {},
        onNavigateUp: () -> Unit = {},
    ) {
        TagEditorScreen(
            uiState = state,
            onFieldChanged = { _, _ -> },
            onChangeArtworkClick = {},
            onSaveClick = onSaveClick,
            onNavigateUp = onNavigateUp,
            onDismissMessage = {},
        )
    }

    /** Every pixel inside the year error text's bounds. */
    private fun yearErrorInk(): List<Int> {
        val bitmap = composeRule.drawRoot()
        val bounds = composeRule.onNodeWithText(YEAR_ERROR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return composeRule.pixelsIn(bitmap, bounds)
    }

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun nodeCount(matcher: SemanticsMatcher): Int = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    private fun Bitmap.pixelAt(x: Dp, y: Dp): Int = with(composeRule.density) { getPixel(x.roundToPx(), y.roundToPx()) }

    private fun Int.hex(): String = Integer.toHexString(this)

    private companion object {
        const val YEAR_ERROR = "Year must be blank or up to 4 digits."

        /** A test-only stand-in for colorScheme.error, far from every token on a dark screen. */
        val ERROR_SEPARATOR = Color(0xFF00FF00)

        /** The Title text field (its label and an editable value), not the header's track title. */
        val TITLE_FIELD = hasText("Title") and hasSetTextAction()

        /** How far above the field the card probe sits, and how far inside the field its probe sits. */
        val CARD_PROBE = 4.dp
        val FIELD_PROBE = 2.dp

        /** A loaded, dirty, saveable track whose tag values never equal a field label. */
        fun loadedState(): TagEditorUiState = TagEditorUiState(
            isLoading = false,
            songTitle = "Bohemian Rhapsody",
            artistName = "Queen",
            artworkUri = "",
            tags = SongTags(
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                albumArtist = "Queen",
                genre = "Rock",
                year = "1975",
                trackNumber = "11",
                composer = "Freddie Mercury",
            ),
            isDirty = true,
            canSave = true,
        )
    }
}
