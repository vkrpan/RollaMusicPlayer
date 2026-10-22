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
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost

/**
 * Test handle for FolderDetail's track list. Not `song_list`, which only the Tracks tab may carry (baseline profile).
 */
internal const val FOLDER_SONG_LIST_TEST_TAG = "folder_song_list"

/**
 * FolderDetail (spec §8.7), reached via `FolderDetail(folderPath)`. That route lives in `:app` and is owned by
 * navigation-agent. Back is [onNavigateUp]; "Edit tags" in the options sheet leaves through [onEditTagsClick].
 */
@Composable
fun FolderDetailRoute(
    onNavigateUp: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderDetailViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val onSongClick = remember(viewModel) { viewModel::play }
    val onShuffleClick = remember(viewModel) { viewModel::shuffleAll }
    val onPlayClick = remember(viewModel) { viewModel::playAll }
    val onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist }
    val onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong }
    FolderDetailContent(
        title = viewModel.title,
        songs = songs,
        userPlaylists = userPlaylists,
        onNavigateUp = onNavigateUp,
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
 * Stateless FolderDetail content; tests and previews host this.
 * - The bar owns the status-bar inset. The panel runs to the bottom edge, and the list pads by the pill and the
 *   navigation bar, so it scrolls under the pill (spec §7.3; ruling 7: no outer pill padding).
 * - [songs] is null until Room answers, and the body renders nothing then.
 * - An empty list means a rescan emptied the folder while it was open.
 */
// The stateless half of FolderDetailRoute: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun FolderDetailContent(
    title: String,
    songs: List<Song>?,
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
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OneUiDetailTopBar(title = title, onNavigateUp = onNavigateUp)
        ContentPanel {
            val enabled = !songs.isNullOrEmpty()
            SortHeader(label = "Name") {
                CircleIconButton(
                    icon = RollaIcons.Shuffle,
                    contentDescription = "Shuffle tracks in this folder",
                    onClick = onShuffleClick,
                    enabled = enabled,
                )
                CircleIconButton(
                    icon = RollaIcons.Play,
                    contentDescription = "Play tracks in this folder",
                    onClick = onPlayClick,
                    enabled = enabled,
                )
            }
            when {
                songs == null -> Unit
                songs.isEmpty() -> TabMessage("No tracks in this folder")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(FOLDER_SONG_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = homeListBottomPadding()),
                ) {
                    items(items = songs, key = { song -> song.id }) { song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongClick(song) },
                            onMoreClick = { optionsSong = song },
                        )
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
        // The list the rows render, so the sheet closes when its track leaves it (ruling 10, controller ruling P6).
        currentSongs = songs,
    )
}

private val PreviewFolderSongs = List(6) { index ->
    Song(
        id = "$index",
        title = "Folder Track ${index + 1}",
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
@Preview(name = "Folder Detail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Folder Detail - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewFolderDetailContent() {
    RollaMusicPlayerTheme {
        FolderDetailContent(
            title = "Chill",
            songs = PreviewFolderSongs,
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
