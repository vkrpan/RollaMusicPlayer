@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.ui.ArtistRow

/** Test handle for the Artists tab's list. */
internal const val ARTIST_LIST_TEST_TAG = "artist_list"

/**
 * The Home pager's Artists tab (spec §8.2). Home owns the permission gate and the library sync and passes [scanState]
 * down; this tab owns the list. A row tap opens that artist's detail through [onArtistClick] with the artist's name,
 * which is how ArtistDetail routes.
 */
@Composable
fun ArtistsTab(
    scanState: ScanState,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistsTabViewModel = hiltViewModel(),
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    ArtistsTabContent(artists = artists, scanState = scanState, onArtistClick = onArtistClick, modifier = modifier)
}

/**
 * Stateless Artists tab content; tests and previews host this. A scan in progress wins over everything. [artists] is
 * null until Room answers, and then nothing but the header renders, so "No artists found" never flashes on first open
 * (ruling 6).
 */
@Composable
internal fun ArtistsTabContent(
    artists: List<Artist>?,
    scanState: ScanState,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SortHeader(label = "Name")
        when {
            scanState is ScanState.Scanning -> TabScanning()
            artists == null -> Unit
            artists.isEmpty() -> TabMessage("No artists found")
            else -> ArtistList(artists = artists, onArtistClick = onArtistClick)
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onArtistClick: (String) -> Unit, modifier: Modifier = Modifier) {
    // The list scrolls under the floating mini-player and the navigation bar, so its bottom padding is both
    // (spec §7.3): the last row can always be scrolled fully into view above the pill.
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(ARTIST_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = homeListBottomPadding()),
    ) {
        // Keyed by name, the GROUP BY key and so unique; never by the synthetic FNV id, whose collision would
        // crash the list (ruling 12). "Queen" and "queen" are two artists with two keys.
        items(items = artists, key = { artist -> artist.name }) { artist ->
            ArtistRow(artist = artist, onClick = { onArtistClick(artist.name) })
        }
    }
}

private val PreviewArtists = List(8) { index ->
    Artist(id = index.toLong(), name = "Sample Artist ${index + 1}", albumCount = 2, songCount = 12)
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Artists Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Artists Tab - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewArtistsTabContent() {
    RollaMusicPlayerTheme {
        ArtistsTabContent(
            artists = PreviewArtists,
            scanState = ScanState.Done(added = 0, removed = 0),
            onArtistClick = {},
        )
    }
}
