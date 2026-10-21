@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.component.OneUiBottomSheet
import com.rolla.musicplayer.core.designsystem.component.SheetActionRow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared One UI sheet titled "Add to playlist" (Phase 6a ruling 11): a "New playlist" action row, then the user's
 * playlists as [PlaylistRow]s with no rail space (there is no rail here). Used from the track options sheet, Playlist
 * Detail (smart playlists) and Now Playing. Presentational only: the caller decides what [onPlaylistSelected] and
 * [onCreateNewPlaylist] do (persist the add, show a follow-up [PlaylistNameDialog], etc).
 */
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onPlaylistSelected: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneUiBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, title = "Add to playlist") {
        LazyColumn {
            item {
                NewPlaylistEntry(onClick = onCreateNewPlaylist)
            }
            items(items = playlists, key = { playlist -> playlist.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onPlaylistSelected(playlist.id) },
                    reserveRailSpace = false,
                )
            }
        }
    }
}

/**
 * [AddToPlaylistSheet] wrapper that collects [userPlaylists] itself. Call sites show the sheet
 * conditionally; collecting the flow in their own (Route-level) scope would subscribe the whole
 * screen to playlist-list changes even while no sheet is visible. Hosting the collection here
 * keeps that invalidation scoped to the sheet.
 */
@Composable
fun AddToPlaylistSheetHost(
    userPlaylists: StateFlow<List<Playlist>>,
    onPlaylistSelected: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by userPlaylists.collectAsStateWithLifecycle()
    AddToPlaylistSheet(
        playlists = playlists,
        onPlaylistSelected = onPlaylistSelected,
        onCreateNewPlaylist = onCreateNewPlaylist,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    )
}

/**
 * The sheet's "New playlist" entry: a single-line action row (no ellipsis: One UI sheets don't flag the follow-up
 * dialog), then a hairline from the text start so it reads apart from the first playlist below it.
 */
@Composable
private fun NewPlaylistEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SheetActionRow(icon = RollaIcons.Add, label = "New playlist", onClick = onClick)
        InsetDivider(startInset = RollaDimens.listTextStart)
    }
}

// The sheet's content without the ModalBottomSheet window, which previews cannot show.
@Suppress("UnusedPrivateMember")
@Preview(name = "Add To Playlist Sheet - Light")
@Preview(name = "Add To Playlist Sheet - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAddToPlaylistSheet() {
    RollaMusicPlayerTheme {
        Column {
            NewPlaylistEntry(onClick = {})
            PlaylistRow(
                playlist = Playlist(
                    id = 1L,
                    name = "Workout Mix",
                    songCount = 24,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                onClick = {},
                reserveRailSpace = false,
            )
            PlaylistRow(
                playlist = Playlist(
                    id = 2L,
                    name = "Chill Evenings",
                    songCount = 8,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                onClick = {},
                reserveRailSpace = false,
            )
        }
    }
}
