@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rolla.musicplayer.core.designsystem.component.OneUiBottomSheet
import com.rolla.musicplayer.core.designsystem.component.SheetActionRow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song

/**
 * The per-track options flow: the options sheet for [song] (null when closed), then the shared "Add to playlist"
 * sheet and its "New playlist" dialog. [onDismiss] closes the options sheet.
 *
 * [onRemoveFromPlaylist], when given (user playlists only, Phase 6a ruling 6), adds a destructive "Remove from
 * playlist" row; the sheet is the deliberate step, so there is no extra confirm. [currentSongs], when given, is the
 * list the sheet was opened from: once it no longer holds the open track (a rescan or tag edit removed it), the
 * options sheet and the add-to-playlist flow that follows it close instead of acting on a ghost (ruling 10).
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
    onRemoveFromPlaylist: ((Song) -> Unit)? = null,
    currentSongs: List<Song>? = null,
) {
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    // Ruling 10: a rescan or tag edit that removes the track closes its sheet instead of leaving it acting on a ghost.
    LaunchedEffect(song?.id, addToPlaylistSong?.id, currentSongs) {
        if (currentSongs.lacks(song)) onDismiss()
        if (currentSongs.lacks(addToPlaylistSong)) addToPlaylistSong = null
    }
    if (song != null) {
        SongOptionsSheetFor(
            song = song,
            onDismiss = onDismiss,
            onAddToPlaylistClick = { addToPlaylistSong = it },
            onEditTagsClick = onEditTagsClick,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
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

/** True when this list is known and [song] is open but no longer in it (ruling 10). */
private fun List<Song>?.lacks(song: Song?): Boolean = this != null && song != null && none { it.id == song.id }

/** Wires [SongOptionsSheet]'s rows for [song]: every action closes the sheet first, then acts on the track. */
@Composable
private fun SongOptionsSheetFor(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    onRemoveFromPlaylist: ((Song) -> Unit)?,
) {
    SongOptionsSheet(
        song = song,
        onAddToPlaylistClick = {
            onDismiss()
            onAddToPlaylistClick(song)
        },
        onEditTagsClick = {
            onDismiss()
            onEditTagsClick(song)
        },
        onRemoveClick = onRemoveFromPlaylist?.let { remove ->
            {
                onDismiss()
                remove(song)
            }
        },
        onDismissRequest = onDismiss,
    )
}

/** The playlist picker for [song]; "New playlist" swaps it for the name dialog, whose dismiss returns to it. */
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
 * The per-track options sheet, shown from a track row's trailing "more options" button: a [OneUiBottomSheet] titled
 * with the track (one ellipsized heading line) holding Add to playlist, Edit tags and, when [onRemoveClick] is given,
 * the destructive Remove from playlist. It is private to [SongOptionsHost], through which every list shares it.
 */
@Composable
private fun SongOptionsSheet(
    song: Song,
    onAddToPlaylistClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onRemoveClick: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    OneUiBottomSheet(onDismissRequest = onDismissRequest, title = song.title) {
        SheetActionRow(icon = RollaIcons.PlaylistAdd, label = "Add to playlist", onClick = onAddToPlaylistClick)
        SheetActionRow(icon = RollaIcons.Edit, label = "Edit tags", onClick = onEditTagsClick)
        if (onRemoveClick != null) {
            SheetActionRow(
                icon = RollaIcons.Delete,
                label = "Remove from playlist",
                onClick = onRemoveClick,
                destructive = true,
            )
        }
    }
}
