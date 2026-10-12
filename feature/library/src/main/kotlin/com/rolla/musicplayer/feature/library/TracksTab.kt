@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost
import com.rolla.musicplayer.core.ui.SongSelectionState
import com.rolla.musicplayer.core.ui.rememberSongSelectionState

/**
 * UiAutomator handle for the :baselineprofile module (see the generator's By.res lookup), resolved through
 * testTagsAsResourceId on the app's root. Changing it breaks the library-scroll journey.
 */
const val SONG_LIST_TEST_TAG = "song_list"

/**
 * The Home pager's Tracks tab (spec §7.2). Home owns the permission gate, the library sync (it passes [scanState]
 * down), the header and the selection bar; this tab owns the list, the sort header's Shuffle / Play and the
 * per-track options sheet. [selection] is hoisted to Home so its selection bar and back handling can read it.
 */
@Composable
fun TracksTab(
    selection: SongSelectionState,
    scanState: ScanState,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val onSongClick = remember(viewModel) { viewModel::play }
    val onShuffleClick = remember(viewModel) { viewModel::shuffleAll }
    val onPlayClick = remember(viewModel) { viewModel::playAll }
    val onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist }
    val onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong }
    TracksTabContent(
        songs = songs,
        scanState = scanState,
        selection = selection,
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
 * Stateless Tracks tab content; tests and previews host this. It fills the ContentPanel that Home provides. While
 * [selection] is active a tap toggles the row instead of playing it, and a long press always toggles.
 */
// The stateless half of TracksTab: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList")
@Composable
internal fun TracksTabContent(
    songs: List<Song>,
    scanState: ScanState,
    selection: SongSelectionState,
    userPlaylists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsSheetSong by remember { mutableStateOf<Song?>(null) }
    // Prune ids a rescan removed, so Home's batch "Edit tags" only ever sees known songs. The guard skips the
    // ViewModel's initial emptyList() (stateIn's seed, before Room's first emission): pruning against it would wipe a
    // selection restored after process death. An empty list counts only once a scan has finished (Done).
    LaunchedEffect(songs, scanState) {
        if (songs.isNotEmpty() || scanState is ScanState.Done) selection.retainAll(songs.mapTo(HashSet()) { it.id })
    }
    Column(modifier = modifier.fillMaxSize()) {
        TracksSortHeader(enabled = songs.isNotEmpty(), onShuffleClick = onShuffleClick, onPlayClick = onPlayClick)
        when {
            scanState is ScanState.Scanning -> TabScanning()
            songs.isEmpty() -> TabMessage("No music found on this device")
            else -> SongList(
                songs = songs,
                selection = selection,
                onSongClick = onSongClick,
                onMoreClick = { song -> optionsSheetSong = song },
            )
        }
    }
    SongOptionsHost(
        song = optionsSheetSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSheetSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
    )
}

/**
 * Shuffle / Play over the whole library. [enabled] is false while the list is empty: disabled, not hidden, so the
 * header does not jump when the library fills (ruling 7).
 */
@Composable
private fun TracksSortHeader(
    enabled: Boolean,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SortHeader(label = "Name", modifier = modifier) {
        CircleIconButton(
            icon = RollaIcons.Shuffle,
            contentDescription = "Shuffle all tracks",
            onClick = onShuffleClick,
            enabled = enabled,
        )
        CircleIconButton(
            icon = RollaIcons.Play,
            contentDescription = "Play all tracks",
            onClick = onPlayClick,
            enabled = enabled,
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    selection: SongSelectionState,
    onSongClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The list scrolls under the floating mini-player and the navigation bar, so its bottom padding is both
    // (spec §7.3): the last row can always be scrolled fully into view above the pill.
    val bottomPadding = homeListBottomPadding()
    // Both reads go through derivedStateOf, so a toggle recomposes only the row whose membership flipped. Entering
    // or leaving selection mode flips selectionActive and recomposes every visible row, which must swap its ⋮ for a
    // checkbox anyway.
    val selectionActive by remember(selection) { derivedStateOf { selection.isActive } }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(SONG_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        items(items = songs, key = { song -> song.id }) { song ->
            val isSelected by remember(song.id, selection) { derivedStateOf { selection.isSelected(song.id) } }
            SongListItem(
                song = song,
                onClick = { if (selection.isActive) selection.toggle(song.id) else onSongClick(song) },
                onMoreClick = { onMoreClick(song) },
                selected = isSelected,
                selectionModeActive = selectionActive,
                onLongClick = { selection.toggle(song.id) },
            )
        }
    }
}

private val PreviewSongs = List(8) { index ->
    Song(
        id = "$index",
        title = "Sample Track ${index + 1}",
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
@Preview(name = "Tracks Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Tracks Tab - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewTracksTabContent() {
    RollaMusicPlayerTheme {
        TracksTabContent(
            songs = PreviewSongs,
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
}
