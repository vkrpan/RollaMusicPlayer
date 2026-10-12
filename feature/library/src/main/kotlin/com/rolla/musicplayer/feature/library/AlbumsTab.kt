@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.ui.AlbumCard

/** Test handle for the Albums tab's grid. */
internal const val ALBUM_GRID_TEST_TAG = "album_grid"

/** A layout count, not a visual token: spec §8.2 fixes the Albums tab as a 2-column grid. */
private const val ALBUM_GRID_COLUMNS = 2

/**
 * The Home pager's Albums tab (spec §8.2). Home owns the permission gate and the library sync and passes [scanState]
 * down; this tab owns the grid. A card tap opens that album's detail through [onAlbumClick] with the album's id.
 */
@Composable
fun AlbumsTab(
    scanState: ScanState,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsTabViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    AlbumsTabContent(albums = albums, scanState = scanState, onAlbumClick = onAlbumClick, modifier = modifier)
}

/**
 * Stateless Albums tab content; tests and previews host this. A scan in progress wins over everything. [albums] is
 * null until Room answers, and then nothing but the header renders, so "No albums found" never flashes on first open
 * (ruling 6).
 */
@Composable
internal fun AlbumsTabContent(
    albums: List<Album>?,
    scanState: ScanState,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // No Shuffle / Play here: the Albums header carries the sort label only (spec §8.2).
        SortHeader(label = "Name")
        when {
            scanState is ScanState.Scanning -> TabScanning()
            albums == null -> Unit
            albums.isEmpty() -> TabMessage("No albums found")
            else -> AlbumGrid(albums = albums, onAlbumClick = onAlbumClick)
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    // The grid scrolls under the floating mini-player and the navigation bar, so its bottom padding is both
    // (spec §7.3): the last row can always be scrolled fully into view above the pill.
    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_GRID_COLUMNS),
        modifier = modifier.fillMaxSize().testTag(ALBUM_GRID_TEST_TAG),
        contentPadding = PaddingValues(
            start = RollaDimens.featureCardRowStart,
            end = RollaDimens.featureCardRowStart,
            bottom = homeListBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
    ) {
        // Keyed by id, never by title: two albums can share a title (ruling 12).
        items(items = albums, key = { album -> album.id }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
        }
    }
}

private val PreviewAlbums = List(6) { index ->
    Album(
        id = index.toLong(),
        title = "Sample Album ${index + 1}",
        artist = "Sample Artist",
        songCount = 10,
        artworkUri = "",
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Albums Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Albums Tab - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewAlbumsTabContent() {
    RollaMusicPlayerTheme {
        AlbumsTabContent(albums = PreviewAlbums, scanState = ScanState.Done(added = 0, removed = 0), onAlbumClick = {})
    }
}
