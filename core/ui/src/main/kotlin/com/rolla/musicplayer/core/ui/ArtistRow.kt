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
import androidx.compose.material.icons.filled.Person
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
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Artist

private val ThumbnailSize = 56.dp
private val ThumbnailIconSize = 24.dp
private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 16.dp
private val ArtworkToTextGap = 12.dp
private val DividerStartPadding = 84.dp

/**
 * Model-aware list row for an [Artist]. Mirrors [SongListItem]'s anatomy per
 * `ui-style-guide.md` §6: a 56dp/12dp-radius leading thumbnail, a two-line title + subtitle, and
 * a hairline divider below. [Artist] carries no artwork field of its own, so unlike
 * [SongListItem]/[AlbumRow] the thumbnail is always the person-glyph placeholder -- there is no
 * image to ever load here. No trailing overflow action -- artist detail hasn't shipped yet; the
 * whole row is clickable via [onClick] instead.
 */
@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ArtistRowContent(artist = artist, onClick = onClick)
        HorizontalDivider(
            modifier = Modifier.padding(start = DividerStartPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun ArtistRowContent(artist: Artist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistThumbnail()
        Spacer(modifier = Modifier.width(ArtworkToTextGap))
        ArtistInfo(artist = artist, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ArtistInfo(artist: Artist, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = artist.name,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${artist.albumCount} albums · ${artist.songCount} songs",
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistThumbnail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ThumbnailSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ThumbnailIconSize),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Artist Row - Light")
@Preview(name = "Artist Row - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewArtistRow() {
    RollaMusicPlayerTheme {
        Column {
            ArtistRow(
                artist = Artist(id = 1L, name = "Queen", albumCount = 15, songCount = 180),
                onClick = {},
            )
            ArtistRow(
                artist = Artist(id = 2L, name = "Pink Floyd", albumCount = 15, songCount = 165),
                onClick = {},
            )
        }
    }
}
