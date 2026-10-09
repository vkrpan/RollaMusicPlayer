@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.ArtworkPlaceholder
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.rowTrailing
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Playlist

/**
 * Model-aware list row for a user-created [Playlist] (spec §8.1): a [RollaDimens.singleLineRowHeight] row with a
 * placeholder thumbnail at [RollaDimens.listThumbStart], the name at [RollaDimens.listTextStart], and a trailing
 * "N tracks" count ending [RollaDimens.listTrailingEnd] before the row's end (the A–Z rail space, ruling 5). No
 * per-row overflow action; the whole row is one merged clickable node.
 */
@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The hairline overlays the row's bottom edge so the row pitch stays exactly singleLineRowHeight.
    Box(modifier = modifier) {
        PlaylistRowContent(playlist = playlist, onClick = onClick)
        InsetDivider(
            startInset = RollaDimens.listTextStart,
            endInset = RollaDimens.listDividerEnd,
            modifier = Modifier.align(Alignment.BottomStart),
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
            .heightIn(min = RollaDimens.singleLineRowHeight)
            .clickable(onClick = onClick)
            .padding(end = RollaDimens.listTrailingEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(RollaDimens.listThumbStart))
        ArtworkPlaceholder(modifier = Modifier.size(RollaDimens.listThumb), shape = MaterialTheme.shapes.small)
        Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(RollaDimens.listTrailingGap))
        Text(
            text = tracksCountLabel(playlist.songCount),
            style = MaterialTheme.typography.rowTrailing,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
