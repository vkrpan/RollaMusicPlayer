@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.playlists

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.CircleIconButton
import com.rolla.musicplayer.core.designsystem.component.FastScrollContainer
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.buildFastScrollSections
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost

/**
 * The Favourites list's test handle. Deliberately not the Tracks list's `song_list`: the baseline-profile journey
 * resolves that id, so no other list may carry it.
 */
internal const val FAVOURITES_LIST_TEST_TAG = "favourites_list"

/**
 * The Home pager's Favourites tab (spec §8.2): the favourite tracks with the sort header's Shuffle / Play, row tap to
 * play from that track, and the shared per-track options sheet. There is no multi-select here (ruling 1): batch
 * editing stays on Tracks.
 */
@Composable
fun FavouritesTab(
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavouritesTabViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val onSongClick = remember(viewModel) { viewModel::play }
    val onShuffleClick = remember(viewModel) { viewModel::shuffleAll }
    val onPlayClick = remember(viewModel) { viewModel::playAll }
    val onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist }
    val onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong }
    FavouritesTabContent(
        songs = songs,
        userPlaylists = userPlaylists,
        onSongClick = onSongClick,
        onShuffleClick = onShuffleClick,
        onPlayClick = onPlayClick,
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
        modifier = modifier,
    )
}

/**
 * Stateless Favourites tab content; tests and previews host this. It fills the ContentPanel that Home provides.
 * [songs] is null until Room answers (ruling 6): the body renders nothing then, so "No favourite tracks yet" never
 * flashes on first open. Play and Shuffle stay visible but disabled while the list is empty or loading (ruling 7).
 */
// The stateless half of FavouritesTab: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList")
@Composable
internal fun FavouritesTabContent(
    songs: List<Song>?,
    userPlaylists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        FavouritesSortHeader(
            enabled = !songs.isNullOrEmpty(),
            onShuffleClick = onShuffleClick,
            onPlayClick = onPlayClick,
        )
        FavouritesBody(songs = songs, onSongClick = onSongClick, onMoreClick = { song -> optionsSong = song })
    }
    SongOptionsHost(
        song = optionsSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
    )
}

@Composable
private fun FavouritesSortHeader(
    enabled: Boolean,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SortHeader(label = "Name", modifier = modifier) {
        CircleIconButton(
            icon = RollaIcons.Shuffle,
            contentDescription = "Shuffle favourite tracks",
            onClick = onShuffleClick,
            enabled = enabled,
        )
        CircleIconButton(
            icon = RollaIcons.Play,
            contentDescription = "Play favourite tracks",
            onClick = onPlayClick,
            enabled = enabled,
        )
    }
}

/** Nothing while [songs] is null (not loaded yet), the empty state for an empty list, otherwise the list. */
@Composable
private fun FavouritesBody(songs: List<Song>?, onSongClick: (Song) -> Unit, onMoreClick: (Song) -> Unit) {
    when {
        songs == null -> Unit
        songs.isEmpty() -> EmptyFavouritesContent()
        else -> FavouritesList(songs = songs, onSongClick = onSongClick, onMoreClick = onMoreClick)
    }
}

@Composable
private fun EmptyFavouritesContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No favourite tracks yet",
            style = MaterialTheme.typography.emptyState,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = RollaDimens.screenEdge),
        )
    }
}

@Composable
private fun FavouritesList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The list scrolls under the floating mini-player and the navigation bar (spec §7.3), like the Tracks list.
    val listState = rememberLazyListState()
    val sections = remember(songs) { buildFastScrollSections(songs.map { it.title }) }
    FastScrollContainer(listState = listState, sections = sections, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(FAVOURITES_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = homeListBottomPadding()),
        ) {
            items(items = songs, key = { song -> song.id }) { song ->
                SongListItem(song = song, onClick = { onSongClick(song) }, onMoreClick = { onMoreClick(song) })
            }
        }
    }
}

private val PreviewFavourites = List(6) { index ->
    Song(
        id = "$index",
        title = "Favourite Track ${index + 1}",
        artist = "Sample Artist",
        album = "Sample Album",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = index + 1,
        year = 2001,
        contentUri = "",
        artworkUri = "",
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Favourites Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Favourites Tab - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewFavouritesTabContent() {
    RollaMusicPlayerTheme {
        FavouritesTabContent(
            songs = PreviewFavourites,
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
