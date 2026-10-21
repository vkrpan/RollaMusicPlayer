package com.rolla.musicplayer.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics, like AlbumDetailScreenTest: rows are measured from their text, and the hero test draws the root.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistDetailScreenTest {

    // An activity rule (what createComposeRule() builds internally), so the hero test can use drawRoot().
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The hosted state. setContent runs once per test (ruling P5 rule 1); a test that needs a second state flips this.
    private var state by mutableStateOf(ArtistDetailUiState())

    // Theme colours read inside the composition, for the hero's pixel probe.
    private var panelColor = Color.Unspecified
    private var placeholderColor = Color.Unspecified

    // Spec §12 / ruling 6: the ⋮ on a track opens the options sheet (it was dead). Control: the row exists, and the
    // sheet is absent before the tap. Mutation: onMoreClick = {} → no "Add to playlist" → RED.
    @Test
    fun `a track's more button opens the options sheet`() {
        setContent(loaded())
        scrollToTrack(1)
        composeRule.onNodeWithText("Track 1").assertExists("the row is rendered")
        composeRule.onNodeWithText("Add to playlist").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options for Track 1").performClick()
        composeRule.onNodeWithText("Add to playlist").assertExists("the options sheet offers Add to playlist")
        composeRule.onNodeWithText("Edit tags").assertExists("the options sheet offers Edit tags")
    }

    // Ruling 6: Edit tags hands the tapped track out. Control: nothing is passed before the tap; the tapped id is
    // passed after it. Mutation: pass songs.first() → RED for track 2.
    @Test
    fun `edit tags passes the tapped track`() {
        val edited = mutableListOf<String>()
        setContent(loaded(), onEditTagsClick = { edited += it.id })
        assertEquals("nothing is passed before the tap", emptyList<String>(), edited)
        scrollToTrack(2)
        composeRule.onNodeWithContentDescription("More options for Track 2").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()
        assertEquals("Edit tags passes the tapped track", listOf("2"), edited)
    }

    // Ruling 5 / spec §8.7: the hero subtitle reads "N albums, N tracks", the sort label is "Album" (ArtistDao orders
    // by album, then track), and the strip has an "Albums" heading. The fixture's 2 albums and 3 tracks keep the two
    // counts apart. Mutation: the old "N albums · N songs" label → RED.
    @Test
    fun `the header says album and track count`() {
        setContent(loaded())
        composeRule.onNodeWithText("2 albums, 3 tracks").assertExists("the hero subtitle is N albums, N tracks")
        composeRule.onNodeWithText("Album").assertExists("the sort label describes the artist order")
        composeRule.onNode(hasText("Albums") and isHeading()).assertExists("the albums strip has a heading")
    }

    // Shuffle and Play are real (spec §12). Control: no action runs before a tap. Mutation: swap the callbacks → RED.
    @Test
    fun `shuffle and play call their actions`() {
        val calls = mutableListOf<String>()
        setContent(loaded(), onShuffleClick = { calls += "shuffle" }, onPlayClick = { calls += "play" })
        assertEquals("no action runs before a tap", emptyList<String>(), calls)
        composeRule.onNodeWithContentDescription("Shuffle artist").performClick()
        composeRule.onNodeWithContentDescription("Play artist").performClick()
        assertEquals("Shuffle then Play ran their own actions", listOf("shuffle", "play"), calls)
    }

    // 0017: the artist screen passes circular = true (the shape itself is DetailArtworkHeaderTest's). Pixel probe at
    // HERO_CORNER_PROBE diagonally in from the hero's top-left corner, with the list at the top (controller ruling P7:
    // 16 dp lies outside the 84 dp-radius circle but inside the 26 dp rounded square's corner arc; 4 dp is outside
    // both). Control: the centre column above the glyph is placeholder. Mutation: circular = false → the probe hits
    // placeholder → RED.
    @Test
    fun `the artist hero is circular`() {
        setContent(loaded())
        // Guard: if a token change made the two colours equal, the corner probe would prove nothing.
        assertFalse(
            "the panel and the placeholder must be distinguishable",
            isColorNear(panelColor, placeholderColor.toArgb()),
        )
        val hero = composeRule.onNodeWithTag(HERO_ART_TAG, useUnmergedTree = true).getBoundsInRoot()
        val bitmap = composeRule.drawRoot()
        fun pixel(x: Dp, y: Dp): Int = with(composeRule.density) {
            bitmap.getPixel((hero.left + x).roundToPx(), (hero.top + y).roundToPx())
        }
        assertTrue(
            "the hero's centre column is placeholder",
            isColorNear(placeholderColor, pixel(RollaDimens.detailArtworkSize / 2, HERO_CONTROL_Y)),
        )
        assertTrue(
            "the circular hero's corner shows the panel",
            isColorNear(panelColor, pixel(HERO_CORNER_PROBE, HERO_CORNER_PROBE)),
        )
    }

    // An album card opens its own album. Control: no id is passed before the tap. Mutation: pass the artist's first
    // album → 1 instead of 2 → RED.
    @Test
    fun `an album card opens its album`() {
        val opened = mutableListOf<Long>()
        setContent(loaded(), onAlbumClick = { opened += it })
        assertEquals("no album opens before the tap", emptyList<Long>(), opened)
        composeRule.onNodeWithText("Jazz").performClick()
        assertEquals("the tapped card's album opens", listOf(JAZZ_ID), opened)
    }

    // Review Focus 5: the last track scrolls fully above the pill (bounds, not presence). Control: the list exists and
    // the last row is found. Mutation: drop the bottom contentPadding → RED.
    @Test
    fun `the last track scrolls fully above the mini-player`() {
        val songs = songs(20)
        setContent(loaded(songs = songs), miniPlayerInset = 60.dp)
        scrollToTrack(songs.size)
        val last = composeRule.onNodeWithText("Track 20").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the last track's bottom ${last.bottom} is at or above the pill's top ${root.bottom - 60.dp}",
            last.bottom <= root.bottom - 60.dp,
        )
    }

    // Not found: a gone artist says so. Control: the message shows once loading ends with no artist (the loading
    // state, which renders nothing, is the extra check). Mutation: artist == null → Unit → message missing → RED.
    @Test
    fun `a missing artist says it is not available`() {
        setContent(ArtistDetailUiState(isLoading = true))
        composeRule.onNodeWithText("Artist not available").assertDoesNotExist()
        composeRule.runOnIdle { state = ArtistDetailUiState(isLoading = false, artist = null) }
        composeRule.onNodeWithText("Artist not available").assertExists("a null artist after loading is not found")
    }

    // Ruling 10 / controller ruling P6: the sheet closes when its track leaves the list the rows render. Control: the
    // sheet is open on track 2 first. Mutation: drop currentSongs from the SongOptionsHost call → the sheet stays →
    // RED.
    @Test
    fun `the options sheet closes when its track leaves the list`() {
        val songs = songs(3)
        setContent(loaded(songs = songs))
        scrollToTrack(2)
        composeRule.onNodeWithContentDescription("More options for Track 2").performClick()
        composeRule.onNodeWithText("Edit tags").assertIsDisplayed()
        composeRule.runOnIdle { state = loaded(songs = songs.filterNot { it.id == "2" }) }
        composeRule.onNodeWithText("Track 3").assertExists("the list still renders the remaining tracks")
        composeRule.onNodeWithText("Edit tags").assertDoesNotExist()
    }

    // The hero, the albums strip and the sort row fill most of a 640 dp screen, so a track row is below the fold until
    // the list scrolls to it. They are items 0 to 2, so track n (1-based) is item n + 2.
    private fun scrollToTrack(n: Int) {
        composeRule.onNodeWithTag(ARTIST_SONG_LIST_TEST_TAG).performScrollToIndex(n + 2)
    }

    // Defaulted test-host knobs: each test overrides only the inputs it probes.
    @Suppress("LongParameterList")
    private fun setContent(
        uiState: ArtistDetailUiState,
        onNavigateUp: () -> Unit = {},
        onAlbumClick: (Long) -> Unit = {},
        onSongClick: (Song) -> Unit = {},
        onShuffleClick: () -> Unit = {},
        onPlayClick: () -> Unit = {},
        onEditTagsClick: (Song) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        state = uiState
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                panelColor = MaterialTheme.colorScheme.surfaceContainer
                placeholderColor = MaterialTheme.colorScheme.artworkPlaceholderLarge
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    ArtistDetailContent(
                        uiState = state,
                        userPlaylists = emptyList(),
                        onNavigateUp = onNavigateUp,
                        onAlbumClick = onAlbumClick,
                        onSongClick = onSongClick,
                        onShuffleClick = onShuffleClick,
                        onPlayClick = onPlayClick,
                        onAddSongToPlaylist = { _, _ -> },
                        onCreatePlaylistAndAddSong = { _, _ -> },
                        onEditTagsClick = onEditTagsClick,
                    )
                }
            }
        }
    }

    private companion object {
        // DetailArtworkHeader's DETAIL_ARTWORK_TEST_TAG, which is internal to :core:ui. A rename fails this test loudly
        // (no node), never silently.
        const val HERO_ART_TAG = "detail_artwork"

        val HERO_CORNER_PROBE = 16.dp

        // A quarter of the way down the 168 dp hero: inside both shapes and clear of the centred 60 dp glyph (54-114).
        val HERO_CONTROL_Y = 42.dp

        const val JAZZ_ID = 2L

        fun loaded(songs: List<Song> = songs(3), albums: List<Album> = albums()) = ArtistDetailUiState(
            isLoading = false,
            artist = Artist(id = 1L, name = "Queen", albumCount = albums.size, songCount = songs.size),
            albums = albums,
            songs = songs,
        )

        // Empty artwork URIs everywhere, so placeholders render and Coil never starts a load under Robolectric.
        fun albums(): List<Album> = listOf(
            Album(id = 1L, title = "A Night at the Opera", artist = "Queen", songCount = 2, artworkUri = ""),
            Album(id = JAZZ_ID, title = "Jazz", artist = "Queen", songCount = 1, artworkUri = ""),
        )

        fun songs(n: Int): List<Song> = (1..n).map { index ->
            Song(
                id = "$index",
                title = "Track $index",
                artist = "Queen",
                album = "A Night at the Opera",
                albumId = 1L,
                durationMs = 200_000L,
                trackNumber = index,
                year = 1975,
                contentUri = "content://media/external/audio/media/$index",
                artworkUri = "",
            )
        }
    }
}
