@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Album

private val ThumbnailSize = 56.dp
private val ThumbnailIconSize = 24.dp
private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 16.dp
private val ArtworkToTextGap = 12.dp
private val DividerStartPadding = 84.dp

/**
 * Model-aware list row for an [Album]. Mirrors [SongListItem]'s anatomy per
 * `ui-style-guide.md` §6: a 56dp/12dp-radius leading artwork thumbnail (music-note placeholder on
 * [MaterialTheme.colorScheme.surfaceContainerHigh] when the album has none), a two-line
 * title + subtitle, and a hairline divider below. Unlike [SongListItem] there is no trailing
 * overflow action -- album detail hasn't shipped yet, so there is nothing for one to open; the
 * whole row is clickable via [onClick] instead.
 */
@Composable
fun AlbumRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AlbumRowContent(album = album, onClick = onClick)
        HorizontalDivider(
            modifier = Modifier.padding(start = DividerStartPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun AlbumRowContent(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtworkThumbnail(
            artworkUri = album.artworkUri,
            contentDescription = "Album artwork for ${album.title}",
        )
        Spacer(modifier = Modifier.width(ArtworkToTextGap))
        AlbumInfo(album = album, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AlbumInfo(album: Album, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = album.title,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.artist} · ${album.songCount} songs",
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumArtworkThumbnail(
    artworkUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageRequest = remember(artworkUri) {
        ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
    }
    Box(
        modifier = modifier
            .size(ThumbnailSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNotEmpty()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ThumbnailIconSize),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Album Row - Light")
@Preview(name = "Album Row - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAlbumRow() {
    RollaMusicPlayerTheme {
        Column {
            AlbumRow(
                album = Album(
                    id = 1L,
                    title = "A Night at the Opera",
                    artist = "Queen",
                    songCount = 12,
                    artworkUri = "",
                ),
                onClick = {},
            )
            AlbumRow(
                album = Album(
                    id = 2L,
                    title = "The Wall",
                    artist = "Pink Floyd",
                    songCount = 26,
                    artworkUri = "",
                ),
                onClick = {},
            )
        }
    }
}
