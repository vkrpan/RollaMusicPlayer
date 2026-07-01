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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Playlist

private val ThumbnailSize = 56.dp
private val ThumbnailIconSize = 24.dp
private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 16.dp
private val ArtworkToTextGap = 12.dp
private val NameToCountGap = 12.dp
private val DividerStartPadding = 84.dp

/**
 * Model-aware list row for a user-created [Playlist]. Mirrors [SongListItem]'s anatomy
 * (56dp thumbnail, title, hairline divider) per `ui-style-guide.md` §6 "Playlist Row" —
 * a plain name + trailing song count, no per-row overflow action.
 */
@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PlaylistRowContent(playlist = playlist, onClick = onClick)
        HorizontalDivider(
            modifier = Modifier.padding(start = DividerStartPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun PlaylistRowContent(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistThumbnail()
        Spacer(modifier = Modifier.width(ArtworkToTextGap))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(NameToCountGap))
        Text(
            text = "${playlist.songCount} tracks",
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistThumbnail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ThumbnailSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ThumbnailIconSize),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlist Row - Light")
@Preview(name = "Playlist Row - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPlaylistRow() {
    RollaMusicPlayerTheme {
        Column {
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
                    name = "Empty Playlist",
                    songCount = 0,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                onClick = {},
            )
        }
    }
}
