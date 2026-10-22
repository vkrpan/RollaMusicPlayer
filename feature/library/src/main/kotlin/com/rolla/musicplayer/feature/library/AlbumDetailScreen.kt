@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.CircleIconButton
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.DetailArtworkHeader
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost
import com.rolla.musicplayer.core.ui.tracksCountLabel

/**
 * Test handle for AlbumDetail's track list. Not `song_list`, which only the Tracks tab may carry (baseline profile).
 */
internal const val ALBUM_SONG_LIST_TEST_TAG = "album_song_list"

/**
 * AlbumDetail (spec §8.7), reached via `AlbumDetail(albumId: Long)`. That route lives in `:app` and is owned by
 * navigation-agent. Back is [onNavigateUp]; "Edit tags" in the options sheet leaves through [onEditTagsClick].
 */
@Composable
fun AlbumDetailRoute(
    onNavigateUp: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    AlbumDetailContent(
        uiState = uiState,
        userPlaylists = userPlaylists,
        onNavigateUp = onNavigateUp,
        onSongClick = remember(viewModel) { viewModel::onSongClick },
        onShuffleClick = remember(viewModel) { viewModel::onShuffleClick },
        onPlayClick = remember(viewModel) { viewModel::onPlayClick },
        onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist },
        onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong },
        onEditTagsClick = onEditTagsClick,
    )
}

/**
 * Stateless AlbumDetail (spec §8.7); tests and previews host this.
 * - The bar owns the status-bar inset. The panel runs to the bottom edge, and the list pads by the pill and the
 *   navigation bar, so it scrolls under the pill (ruling 5: no outer pill padding).
 * - The hero and the sort row are the list's first two items, so they scroll with the tracks (ruling 5).
 * - Nothing renders while loading; a null album after loading is the not-found state (the album's last track was
 *   removed).
 * - An album with no tracks is the transient window between the album and track queries, which Room does not
 *   re-emit atomically.
 * - The options sheet's [SongOptionsHost] gets the same list the rows render, so it closes when its track leaves
 *   (ruling 10). The open track is plain `remember` state: a saved one could outlive the list it came from.
 */
// The stateless half of AlbumDetailRoute: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun AlbumDetailContent(
    uiState: AlbumDetailUiState,
    userPlaylists: List<Playlist>,
    onNavigateUp: () -> Unit,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val album = uiState.album
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OneUiDetailTopBar(title = album?.title.orEmpty(), onNavigateUp = onNavigateUp)
        ContentPanel {
            when {
                uiState.isLoading -> Unit
                album == null -> TabMessage("Album not available")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(ALBUM_SONG_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = homeListBottomPadding()),
                ) {
                    item(key = "hero", contentType = "hero") {
                        DetailArtworkHeader(
                            artworkUri = album.artworkUri,
                            subtitle = "${album.artist} · ${tracksCountLabel(uiState.songs.size)}",
                        )
                    }
                    item(key = "sort", contentType = "sort") {
                        val enabled = uiState.songs.isNotEmpty()
                        SortHeader(label = "Track number") {
                            CircleIconButton(
                                icon = RollaIcons.Shuffle,
                                contentDescription = "Shuffle album",
                                onClick = onShuffleClick,
                                enabled = enabled,
                            )
                            CircleIconButton(
                                icon = RollaIcons.Play,
                                contentDescription = "Play album",
                                onClick = onPlayClick,
                                enabled = enabled,
                            )
                        }
                    }
                    if (uiState.songs.isEmpty()) {
                        item(key = "empty", contentType = "empty") { TabMessage("No tracks in this album") }
                    } else {
                        items(items = uiState.songs, key = { song -> song.id }, contentType = { "song" }) { song ->
                            SongListItem(
                                song = song,
                                onClick = { onSongClick(song) },
                                trackNumber = song.trackNumber,
                                onMoreClick = { optionsSong = song },
                            )
                        }
                    }
                }
            }
        }
    }
    SongOptionsHost(
        song = optionsSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
        currentSongs = uiState.songs,
    )
}

private val PreviewAlbum = Album(
    id = 1L,
    title = "A Night at the Opera",
    artist = "Queen",
    songCount = 6,
    artworkUri = "",
)

private val PreviewAlbumSongs = List(6) { index ->
    Song(
        id = "$index",
        title = "Album Track ${index + 1}",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = index + 1,
        year = 1975,
        contentUri = "",
        artworkUri = "",
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Album Detail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Album Detail - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewAlbumDetailContent() {
    RollaMusicPlayerTheme {
        AlbumDetailContent(
            uiState = AlbumDetailUiState(isLoading = false, album = PreviewAlbum, songs = PreviewAlbumSongs),
            userPlaylists = emptyList(),
            onNavigateUp = {},
            onSongClick = {},
            onShuffleClick = {},
            onPlayClick = {},
            onAddSongToPlaylist = { _, _ -> },
            onCreatePlaylistAndAddSong = { _, _ -> },
            onEditTagsClick = {},
        )
    }
}
