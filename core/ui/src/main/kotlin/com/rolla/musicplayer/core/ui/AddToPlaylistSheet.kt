@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Playlist
import kotlinx.coroutines.flow.StateFlow

private val ThumbnailSize = 56.dp
private val ThumbnailIconSize = 24.dp
private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 16.dp
private val ArtworkToTextGap = 12.dp

// Inset to start after the leading icon (RowHorizontalPadding + ThumbnailSize + ArtworkToTextGap),
// matching PlaylistRow/SongListItem's hairline divider so the "New playlist…" row reads as its own
// row instead of fusing into the first playlist below it.
private val DividerStartPadding = RowHorizontalPadding + ThumbnailSize + ArtworkToTextGap
private val DividerThickness = 0.5.dp

/**
 * Shared bottom sheet listing the user's existing playlists plus a "New playlist…" entry point,
 * used from Library, Playlist Detail (smart playlists), and Now Playing to add a song to a
 * playlist. Presentational only — the caller decides what [onPlaylistSelected] and
 * [onCreateNewPlaylist] actually do (persist the add, show a follow-up [PlaylistNameDialog], etc).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onPlaylistSelected: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit,
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
        LazyColumn {
            item {
                NewPlaylistRow(onClick = onCreateNewPlaylist)
            }
            items(items = playlists, key = { playlist -> playlist.id }) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistSelected(playlist.id) })
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

@Composable
private fun NewPlaylistRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewPlaylistIcon()
            Spacer(modifier = Modifier.width(ArtworkToTextGap))
            Text(
                text = "New playlist…",
                style = MaterialTheme.typography.songTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = DividerStartPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = DividerThickness,
        )
    }
}

@Composable
private fun NewPlaylistIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ThumbnailSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ThumbnailIconSize),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Add To Playlist Sheet - Light")
@Preview(name = "Add To Playlist Sheet - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAddToPlaylistSheet() {
    RollaMusicPlayerTheme {
        Column {
            NewPlaylistRow(onClick = {})
            PlaylistRow(
                playlist = Playlist(
                    id = 1L,
                    name = "Workout Mix",
                    songCount = 24,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                onClick = {},
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
            )
        }
    }
}
