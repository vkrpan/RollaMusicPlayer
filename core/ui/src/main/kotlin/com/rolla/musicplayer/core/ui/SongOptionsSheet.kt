@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song

/**
 * The per-track options flow: the options sheet for [song] (null when closed), then the shared "Add to playlist"
 * sheet and its "New playlist" dialog. [onDismiss] closes the options sheet.
 *
 * Hardcoded paddings are moved verbatim; Phase 6 restyles every sheet (spec §8.7).
 */
// Each parameter is a distinct event or piece of state the flow forwards.
@Suppress("LongParameterList")
@Composable
fun SongOptionsHost(
    song: Song?,
    userPlaylists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
) {
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    if (song != null) {
        SongOptionsSheet(
            song = song,
            onAddToPlaylistClick = {
                onDismiss()
                addToPlaylistSong = song
            },
            onEditTagsClick = {
                onDismiss()
                onEditTagsClick(song)
            },
            onDismissRequest = onDismiss,
        )
    }
    val songPendingPlaylistPick = addToPlaylistSong
    if (songPendingPlaylistPick != null) {
        AddToPlaylistFlow(
            song = songPendingPlaylistPick,
            userPlaylists = userPlaylists,
            onAddSongToPlaylist = onAddSongToPlaylist,
            onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
            onFinished = { addToPlaylistSong = null },
        )
    }
}

/** The playlist picker for [song]; "New playlist…" swaps it for the name dialog, whose dismiss returns to it. */
@Composable
private fun AddToPlaylistFlow(
    song: Song,
    userPlaylists: List<Playlist>,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onFinished: () -> Unit,
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    if (!showCreatePlaylistDialog) {
        AddToPlaylistSheet(
            playlists = userPlaylists,
            onPlaylistSelected = { playlistId ->
                onAddSongToPlaylist(song.id, playlistId)
                onFinished()
            },
            onCreateNewPlaylist = { showCreatePlaylistDialog = true },
            onDismissRequest = onFinished,
        )
    } else {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                onCreatePlaylistAndAddSong(name, song.id)
                showCreatePlaylistDialog = false
                onFinished()
            },
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }
}

/**
 * The per-track options sheet, shown from [SongListItem]'s trailing "more options" button: a
 * [ModalBottomSheet] holding a Column of two rows (Add to playlist, Edit tags) under the track
 * title. It is private to [SongOptionsHost], through which the Tracks and Favourites tabs share it.
 * Its hardcoded paddings are moved verbatim; Phase 6 restyles every sheet (spec §8.7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongOptionsSheet(
    song: Song,
    onAddToPlaylistClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column {
            SongOptionsTitle(title = song.title)
            SongOptionRow(
                icon = RollaIcons.PlaylistAdd,
                label = "Add to playlist",
                onClick = onAddToPlaylistClick,
            )
            SongOptionRow(
                icon = RollaIcons.Edit,
                label = "Edit tags",
                onClick = onEditTagsClick,
            )
        }
    }
}

/** The options sheet's heading: the track title, one line, ellipsized. */
@Composable
private fun SongOptionsTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.songTitle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SongOptionRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
