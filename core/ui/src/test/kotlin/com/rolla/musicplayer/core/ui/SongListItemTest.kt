package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongCustomTags
import com.rolla.musicplayer.core.ui.customtags.LocalSongCustomTags
import com.rolla.musicplayer.core.ui.customtags.SONG_TAG_CHIPS_TAG
import com.rolla.musicplayer.core.ui.customtags.SongCustomTagsLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Custom tags phase 4, RF5: a song with no tags has no chip line and today's height. Shown with tags first (the
    // line exists), then without: no line, and at fontScale 2 the row is exactly as tall as it was before chips
    // existed (73 dp, measured on 15c0738 with this song at fontScale 2 and width 360 dp). Stub: never render the
    // line → the first assertion fails. Mutation: always compose an empty chip line (gap + tagChipHeight) → node
    // exists, height grows.
    @Test
    fun row_withoutTags_keepsTodaysHeight() {
        var lookup by mutableStateOf(SongCustomTagsLookup { TECHNO_HARD })
        setTaggedRow(fontScale = 2f, lookup = { lookup })
        composeRule.onNodeWithTag(SONG_TAG_CHIPS_TAG, useUnmergedTree = true).assertExists()

        lookup = SongCustomTagsLookup { null }
        composeRule.onNodeWithTag(SONG_TAG_CHIPS_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(HOST_TAG).getBoundsInRoot().height
            .assertIsEqualTo(TODAYS_ROW_HEIGHT_AT_FONT_SCALE_2, "untagged row height at fontScale 2", 0.5.dp)
    }

    // The song's tag (Techno, Tonal) and subtag (Hard, Outline) show as chips on a line below the artist. Stub: never
    // read the local → no chip texts. Mutation: never read the local → "Techno" missing.
    @Test
    fun row_withTags_showsChipsUnderArtist() {
        setTaggedRow(lookup = { SongCustomTagsLookup { id -> TECHNO_HARD.takeIf { id == song.id } } })
        // assertExists first: its failure names the label, assertIsDisplayed's does not.
        composeRule.onNodeWithText("Techno", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeRule.onNodeWithText("Hard", useUnmergedTree = true).assertExists().assertIsDisplayed()
        val artistBottom = composeRule.onNodeWithText("Queen", useUnmergedTree = true).getBoundsInRoot().bottom
        val lineTop = composeRule.onNodeWithTag(SONG_TAG_CHIPS_TAG, useUnmergedTree = true).getBoundsInRoot().top
        assertTrue(
            "chip line top ($lineTop) is at or below the artist's bottom ($artistBottom)",
            lineTop >= artistBottom,
        )
    }

    // At the default font a tagged row keeps the list pitch (title + artist + gap + one chip fits in listRowHeight).
    // Stub: never render the line → the chip is not displayed. Mutation: chip height 32 dp → 74 dp row.
    @Test
    fun row_withTags_keepsListRowHeightAtDefaultFont() {
        setTaggedRow(lookup = { SongCustomTagsLookup { TECHNO_HARD } })
        composeRule.onNodeWithText("Techno", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeRule.onNodeWithTag(HOST_TAG).getBoundsInRoot().height
            .assertIsEqualTo(RollaDimens.listRowHeight, "tagged row height at the default font", tolerance = 0.5.dp)
    }

    // RF5: at fontScale 1.5 on a 360 dp screen, ten tags stay on one line ending in a "+N" chip, and the ⋮ stays wholly
    // inside the row. Stub: never render the line → no "+N" chip. Mutation: maxLines = 2 → chips on two lines.
    @Test
    fun row_manyTagsAtLargeFont_staysOneLineWithPlusN() {
        setTaggedRow(fontScale = 1.5f, lookup = { SongCustomTagsLookup { TEN_TAGS } })
        val shownChipTexts = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(SONG_TAG_CHIPS_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }
        val labels = shownChipTexts.map { node -> node.config[SemanticsProperties.Text].joinToString() }
        assertTrue("a \"+N\" chip is shown among $labels", labels.any { it.startsWith("+") })
        val tops = shownChipTexts.map { it.boundsInRoot.top }.distinct()
        assertEquals("distinct chip text tops (lines) for $labels", 1, tops.size)

        val row = composeRule.onNodeWithTag(HOST_TAG).getBoundsInRoot()
        val more = composeRule.onNodeWithContentDescription("More options for ${song.title}").getBoundsInRoot()
        assertTrue(
            "⋮ bounds $more lie inside the row $row",
            more.left >= row.left && more.right <= row.right && more.top >= row.top && more.bottom <= row.bottom,
        )
    }

    // TalkBack reads the line as one description, tags then subtags, and omits an empty part; the chips' own labels do
    // not reach the row. Stub: no description → mismatch. Mutation: subtags first → "Subtags: Hard. Tags: …" mismatch.
    @Test
    fun row_tagsDescription_readsTagsThenSubtags() {
        var tags by mutableStateOf(TECHNO_HOUSE_HARD)
        setTaggedRow(lookup = { SongCustomTagsLookup { tags } })
        composeRule.onNodeWithTag(SONG_TAG_CHIPS_TAG, useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Tags: Techno, House. Subtags: Hard."),
            ),
        )
        val rowTexts = composeRule.onNodeWithText(song.title).fetchSemanticsNode().config[SemanticsProperties.Text]
        assertEquals("the merged row's texts (no chip labels)", listOf(song.title, "Queen"), rowTexts.map { it.text })

        tags = SongCustomTags(tags = emptyList(), subTags = TECHNO_HOUSE_HARD.subTags)
        composeRule.onNodeWithTag(SONG_TAG_CHIPS_TAG, useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Subtags: Hard.")),
        )
    }

    /** The row at [fontScale] in a 360 dp host tagged [HOST_TAG], with [lookup] provided as [LocalSongCustomTags]. */
    private fun setTaggedRow(fontScale: Float = 1f, lookup: () -> SongCustomTagsLookup) {
        composeRule.setContent {
            val scaled = Density(LocalDensity.current.density, fontScale)
            CompositionLocalProvider(LocalDensity provides scaled, LocalSongCustomTags provides lookup()) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    Box(Modifier.width(360.dp).testTag(HOST_TAG)) { SongListItem(song, onClick = {}) }
                }
            }
        }
    }

    private companion object {
        const val HOST_TAG = "host"

        /** The untagged row's height at fontScale 2, measured on the code before chips (tree 15c0738). */
        val TODAYS_ROW_HEIGHT_AT_FONT_SCALE_2 = 73.dp

        fun tag(id: Long, name: String) = CustomTag(kind = CustomTagKind.TAG, id = id, name = name)
        fun subTag(id: Long, name: String) = CustomTag(kind = CustomTagKind.SUBTAG, id = id, name = name)

        val TECHNO_HARD = SongCustomTags(tags = listOf(tag(1L, "Techno")), subTags = listOf(subTag(1L, "Hard")))
        val TECHNO_HOUSE_HARD = SongCustomTags(
            tags = listOf(tag(1L, "Techno"), tag(2L, "House")),
            subTags = listOf(subTag(1L, "Hard")),
        )
        val TEN_TAGS = SongCustomTags(tags = (1L..10L).map { tag(it, "Tag $it") }, subTags = emptyList())
    }
}
