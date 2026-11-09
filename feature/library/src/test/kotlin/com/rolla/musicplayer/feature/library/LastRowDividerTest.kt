package com.rolla.musicplayer.feature.library

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.folderName
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 7 calibration: One UI draws no hairline under a list's last row (the reference's Playlists and Folders), while
 * every other row keeps its divider. Each test renders a three-row list and probes the hairline's centre row at the
 * bottom of the second-to-last row (the divider: present) and of the last row (absent), mid-way along the divider,
 * in one composition. 3 px/dp, so the 1 dp hairline has a solid middle pixel row. Rendering: view.draw (drawRoot).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LastRowDividerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var divider = Color.Unspecified

    // Trivial stub (never draw a divider) → the second-to-last row has none → RED.
    // Mutation: TracksTab passes showDivider = true for every row (always show) → the last row has one → RED.
    @Test
    fun tracksDrawNoDividerUnderTheLastRow() {
        setContent {
            TracksTabContent(
                songs = List(ROWS) { song(it) },
                scanState = ScanState.Done(added = 0, removed = 0),
                selection = rememberSongSelectionState(),
                userPlaylists = emptyList(),
                onSongClick = {},
                onShuffleClick = {},
                onPlayClick = {},
                onAddSongToPlaylist = { _, _ -> },
                onCreatePlaylistAndAddSong = { _, _ -> },
                onEditTagsClick = {},
            )
        }
        assertDividerOnlyBetweenRows(rowTitles = List(ROWS) { "Song $it" })
    }

    // Mutation: FoldersTab passes showDivider = true for every row (always show) → RED. It also covers
    // ListRowScaffold, which FolderRow, AlbumRow and ArtistRow share: drop its `if (showDivider)` → RED.
    @Test
    fun foldersDrawNoDividerUnderTheLastRow() {
        val folders = listOf("Alpha", "Bravo", "Charlie").map { path ->
            Folder(path = path, name = folderName(path), songCount = 1)
        }
        setContent { FoldersTabContent(folders = folders, scanState = ScanState.Done(1, 0), onFolderClick = {}) }
        assertDividerOnlyBetweenRows(rowTitles = folders.map { it.name })
    }

    // Mutation: ArtistsTab passes showDivider = true for every row (always show) → RED; ArtistRow does not pass its
    // showDivider on to ListRowScaffold → RED.
    @Test
    fun artistsDrawNoDividerUnderTheLastRow() {
        val artists = listOf("Alpha", "Bravo", "Charlie").mapIndexed { index, name ->
            Artist(id = index.toLong(), name = name, albumCount = 1, songCount = 1)
        }
        setContent { ArtistsTabContent(artists = artists, scanState = ScanState.Done(1, 0), onArtistClick = {}) }
        assertDividerOnlyBetweenRows(rowTitles = artists.map { it.name })
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                divider = MaterialTheme.colorScheme.outlineVariant
                content()
            }
        }
    }

    /** The second-to-last row ends on a divider pixel; the last row does not. The merged title node is the row. */
    private fun assertDividerOnlyBetweenRows(rowTitles: List<String>) {
        val bitmap = composeRule.drawRoot()
        val secondToLast = hairlinePixel(bitmap, rowTitles[rowTitles.lastIndex - 1])
        val last = hairlinePixel(bitmap, rowTitles.last())
        assertTrue(
            "the second-to-last row keeps its divider: ${Integer.toHexString(secondToLast)}",
            isColorNear(divider, secondToLast),
        )
        assertFalse("the last row has no divider: ${Integer.toHexString(last)}", isColorNear(divider, last))
    }

    /** The pixel on the hairline's centre row under the row titled [title], 40 dp along the divider. */
    private fun hairlinePixel(bitmap: Bitmap, title: String): Int {
        val row = composeRule.onNodeWithText(title).getBoundsInRoot()
        return with(composeRule.density) {
            val x = (row.left + RollaDimens.listTextStart + 40.dp).roundToPx()
            val y = (row.bottom - RollaDimens.dividerThickness / 2).toPx().toInt()
            bitmap.getPixel(x, y)
        }
    }

    private companion object {
        const val ROWS = 3

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
            artworkUri = "",
        )
    }
}
