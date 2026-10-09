package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics: under LEGACY, Robolectric measures every text line at 36 dp whatever its style, which makes the
// two-line row 72 dp tall and the height assertion meaningless.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongListItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val song = Song(
        id = "1",
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 10L,
        durationMs = 354_000L,
        trackNumber = 11,
        year = 1975,
        contentUri = "content://media/external/audio/media/1",
        // Empty so the row renders ArtworkPlaceholder and Coil never starts a load under Robolectric.
        artworkUri = "",
    )

    @Test
    fun rowIsListRowHeightTall() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.testTag("host")) { SongListItem(song, onClick = {}) }
            }
        }
        composeRule.onNodeWithTag("host").assertHeightIsEqualTo(RollaDimens.listRowHeight)
    }

    @Test
    fun overflowEndsAtTheMeasuredInset() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(360.dp)) { SongListItem(song, onClick = {}) }
            }
        }
        // M3 IconButton's semantics node is its 40 dp state layer. Touch bounds are that node widened to 48 dp around
        // its centre, so this right edge pins the ⋮ glyph centre at 360 − listOverflowEnd − 24 = 310 dp; it does not
        // read a layout box.
        val node = composeRule.onNodeWithContentDescription("More options for ${song.title}").fetchSemanticsNode()
        val overflowRight = with(composeRule.density) { node.touchBoundsInRoot.right.toDp() }
        overflowRight.assertIsEqualTo(360.dp - RollaDimens.listOverflowEnd, "overflow right", tolerance = 0.5.dp)
    }

    @Test
    fun titleStartsAtListTextStart() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(360.dp)) { SongListItem(song, onClick = {}) }
            }
        }
        composeRule.onNodeWithText(song.title, useUnmergedTree = true)
            .getBoundsInRoot().left
            .assertIsEqualTo(RollaDimens.listTextStart, "title left", tolerance = 0.5.dp)
    }

    @Test
    fun selectedRowInSelectionModeReportsSelectedAndShowsCheckbox() {
        var selectionModeActive by mutableStateOf(false)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(360.dp)) {
                    SongListItem(song, onClick = {}, selected = true, selectionModeActive = selectionModeActive)
                }
            }
        }
        composeRule.onNodeWithTag(SONG_SELECTION_CHECKBOX_TAG, useUnmergedTree = true).assertDoesNotExist()

        selectionModeActive = true
        composeRule.onNodeWithText(song.title)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))
        composeRule.onNodeWithTag(SONG_SELECTION_CHECKBOX_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun selectionCheckboxCentresWhereTheOverflowGlyphDoes() {
        var selectionModeActive by mutableStateOf(false)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(360.dp)) {
                    SongListItem(song, onClick = {}, selectionModeActive = selectionModeActive)
                }
            }
        }
        val glyphCentre = 360.dp - RollaDimens.listOverflowEnd - RollaDimens.minTouchTarget / 2
        val overflow = composeRule.onNodeWithContentDescription("More options for ${song.title}").getBoundsInRoot()
        ((overflow.left + overflow.right) / 2).assertIsEqualTo(glyphCentre, "overflow centre", tolerance = 0.5.dp)

        selectionModeActive = true
        val checkbox = composeRule.onNodeWithTag(SONG_SELECTION_CHECKBOX_TAG, useUnmergedTree = true).getBoundsInRoot()
        ((checkbox.left + checkbox.right) / 2).assertIsEqualTo(glyphCentre, "checkbox centre", tolerance = 0.5.dp)
    }

    @Test
    fun trackNumberModeReplacesArtwork() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(360.dp)) { SongListItem(song, onClick = {}, trackNumber = 7) }
            }
        }
        composeRule.onNodeWithContentDescription("Track 7").assertExists()
    }
}
