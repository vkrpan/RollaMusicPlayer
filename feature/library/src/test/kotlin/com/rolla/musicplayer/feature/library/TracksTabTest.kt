package com.rolla.musicplayer.feature.library

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.SongSelectionState
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics: the scroll-geometry test measures a row whose height depends on its text, and LEGACY mode measures
// every text line at ~36 dp. captureToImage times out under NATIVE, so nothing here captures pixels.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TracksTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val songs = List(SONG_COUNT) { index -> song(index) }

    private lateinit var selection: SongSelectionState

    // The composition's view, so a test can dispatch its own insets (Robolectric reports none).
    private lateinit var view: View

    private var songClicks = 0
    private var shuffleClicks = 0
    private var playClicks = 0

    @Test
    fun sortHeaderButtonsInvokeShuffleAndPlay() {
        setTab()

        composeRule.onNodeWithContentDescription("Shuffle all tracks").performClick()
        assertEquals("Shuffle must fire once", 1, shuffleClicks)
        assertEquals("Play must not fire from Shuffle", 0, playClicks)

        composeRule.onNodeWithContentDescription("Play all tracks").performClick()
        assertEquals("Shuffle must still have fired once", 1, shuffleClicks)
        assertEquals("Play must fire once", 1, playClicks)
    }

    @Test
    fun songListKeepsTheBaselineProfileTag() {
        setTab()

        composeRule.onNodeWithTag(SONG_LIST_TEST_TAG).assertExists()
        assertEquals("The :baselineprofile journey resolves this exact resource id", "song_list", SONG_LIST_TEST_TAG)
    }

    @Test
    fun lastRowScrollsFullyAboveTheMiniPlayer() {
        setTab(miniPlayerInset = MINI_PLAYER_INSET, hostHeight = HOST_HEIGHT)
        dispatchNavigationBar(NAV_BAR_HEIGHT)

        composeRule.onNodeWithTag(SONG_LIST_TEST_TAG).performScrollToIndex(SONG_COUNT - 1)

        val hostTop = composeRule.onNodeWithTag(HOST_TAG).getBoundsInRoot().top
        // The merged, clickable row node of the last song (its title and artist merge into it).
        val lastRow = composeRule.onNodeWithText(songs.last().title).getBoundsInRoot()
        val rowBottom = lastRow.bottom - hostTop
        // Two-sided: the row ends exactly at the pill's top, which sits above the navigation bar -- neither hidden
        // under the pill or the bar nor floating above them from a doubled inset.
        rowBottom.assertIsEqualTo(
            HOST_HEIGHT - MINI_PLAYER_INSET - NAV_BAR_HEIGHT,
            "last row bottom",
            tolerance = TOLERANCE,
        )
        assertTrue("Last row must be in view, top=${lastRow.top - hostTop}", lastRow.top - hostTop >= 0.dp)
    }

    @Test
    fun longPressStartsSelectionAndTapTogglesInSelectionMode() {
        setTab()

        val row0 = composeRule.onNodeWithText(songs[0].title)
        val row1 = composeRule.onNodeWithText(songs[1].title)

        row0.performTouchInput { longClick() }
        assertTrue("Long-press must select row 0", selection.isSelected(songs[0].id))
        row0.assertIsSelected()
        row1.assertIsNotSelected()

        row1.performClick()
        assertTrue("A tap in selection mode must select row 1", selection.isSelected(songs[1].id))
        row0.assertIsSelected()
        row1.assertIsSelected()

        row0.performClick()
        assertFalse("A second tap must deselect row 0", selection.isSelected(songs[0].id))
        assertTrue("Row 1 must stay selected", selection.isSelected(songs[1].id))
        row0.assertIsNotSelected()
        row1.assertIsSelected()

        assertEquals("No tap in selection mode may play a song", 0, songClicks)
    }

    @Test
    fun emptyLibraryShowsTheEmptyState() {
        setTab(songs = emptyList())

        composeRule.onNodeWithText("No music found on this device").assertIsDisplayed()
    }

    @Test
    fun scanningShowsProgress() {
        setTab(scanState = ScanState.Scanning)

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        composeRule.onNodeWithContentDescription("Scanning library").assertExists()
        composeRule.onNodeWithTag(SONG_LIST_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun rescanPrunesSelectedIdsMissingFromTheNewSongList() {
        val hostedSongs = mutableStateOf(songs)
        composeRule.setContent { PruningHost(songs = hostedSongs.value, scanState = ScanState.Done(0, 0)) }
        composeRule.runOnIdle {
            selection.toggle(songs[0].id)
            selection.toggle(songs[1].id)
        }

        // A rescan removed song "1".
        composeRule.runOnIdle { hostedSongs.value = songs.filterNot { it.id == songs[1].id } }

        composeRule.runOnIdle {
            assertEquals("The removed song's id must be pruned", 1, selection.count)
            assertEquals(setOf(songs[0].id), selection.selectedIds)
        }
    }

    @Test
    fun rescanToAnEmptyLibraryClearsTheSelection() {
        val hostedSongs = mutableStateOf(songs)
        composeRule.setContent { PruningHost(songs = hostedSongs.value, scanState = ScanState.Done(0, 0)) }
        composeRule.runOnIdle {
            selection.toggle(songs[0].id)
            selection.toggle(songs[1].id)
        }

        // A finished rescan found no music at all: an empty list after Done is real, not the stateIn seed.
        composeRule.runOnIdle { hostedSongs.value = emptyList() }

        composeRule.runOnIdle {
            assertEquals("Every selected id must be pruned", 0, selection.count)
            assertFalse("Selection mode must end", selection.isActive)
        }
    }

    @Test
    fun restoredSelectionSurvivesTheInitialEmptySongList() {
        // The ViewModel's stateIn seed is emptyList() and nothing has been scanned yet (Idle): a selection restored
        // after process death must not be pruned against that placeholder.
        val tester = StateRestorationTester(composeRule)
        tester.setContent { PruningHost(songs = emptyList(), scanState = ScanState.Idle) }
        composeRule.runOnIdle { selection.toggle(songs[0].id) }

        tester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals("The restored selection must survive", setOf(songs[0].id), selection.selectedIds)
        }
    }

    @Composable
    private fun PruningHost(songs: List<Song>, scanState: ScanState) {
        RollaMusicPlayerTheme(darkTheme = true) {
            selection = rememberSongSelectionState()
            TracksTabContent(
                songs = songs,
                scanState = scanState,
                selection = selection,
                userPlaylists = emptyList(),
                onSongClick = {},
                onShuffleClick = {},
                onPlayClick = {},
                onAddSongToPlaylist = { _, _ -> },
                onCreatePlaylistAndAddSong = { _, _ -> },
                onEditTagsClick = {},
            )
        }
    }

    private fun setTab(
        songs: List<Song> = this.songs,
        scanState: ScanState = ScanState.Done(added = 0, removed = 0),
        miniPlayerInset: Dp = 0.dp,
        hostHeight: Dp? = null,
    ) {
        composeRule.setContent {
            view = LocalView.current
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    val hostModifier = if (hostHeight != null) Modifier.height(hostHeight) else Modifier
                    Box(hostModifier.testTag(HOST_TAG)) {
                        selection = rememberSongSelectionState()
                        TracksTabContent(
                            songs = songs,
                            scanState = scanState,
                            selection = selection,
                            userPlaylists = emptyList(),
                            onSongClick = { songClicks++ },
                            onShuffleClick = { shuffleClicks++ },
                            onPlayClick = { playClicks++ },
                            onAddSongToPlaylist = { _, _ -> },
                            onCreatePlaylistAndAddSong = { _, _ -> },
                            onEditTagsClick = {},
                        )
                    }
                }
            }
        }
    }

    /** Sends a bottom navigation bar of [height] to the composition's view, where Compose's insets listener sees it. */
    private fun dispatchNavigationBar(height: Dp) {
        val heightPx = with(composeRule.density) { height.roundToPx() }
        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, heightPx))
                    .build(),
            )
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val SONG_COUNT = 30
        const val HOST_TAG = "tracks_host"
        val HOST_HEIGHT = 400.dp
        val MINI_PLAYER_INSET = 72.dp
        val NAV_BAR_HEIGHT = 48.dp
        val TOLERANCE = 0.5.dp

        fun song(index: Int) = Song(
            id = "$index",
            title = "Song $index",
            artist = "Artist $index",
            album = "Album",
            albumId = 1L,
            durationMs = 200_000L,
            trackNumber = index + 1,
            year = 2001,
            contentUri = "content://media/external/audio/media/$index",
            // Empty so rows render ArtworkPlaceholder and Coil never starts a load under Robolectric.
            artworkUri = "",
        )
    }
}
