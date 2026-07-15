@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Album

private val CardWidth = 150.dp
private val ArtworkIconSize = 40.dp
private val TextGap = 8.dp

/**
 * Square feature card for an [Album], per `ui-style-guide.md` §6 ("Feature Cards") / §5: a
 * ~150dp-wide column with square artwork (28dp `extraLarge` radius, music-note placeholder on
 * [MaterialTheme.colorScheme.surfaceContainerHigh] when the album has none) on top, then the album
 * title and a song-count subtitle below. Intended for horizontal `LazyRow`s (e.g. an artist's
 * albums on the artist detail screen in `:feature:library`) -- mirrors [AlbumRow]'s anatomy but as
 * a card rather than a list row, since there is no divider or trailing affordance to make a row
 * read naturally in a horizontal scroller.
 */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(CardWidth).clickable(onClick = onClick)) {
        AlbumCardArtwork(
            artworkUri = album.artworkUri,
            // Decorative within the merged clickable card: the title/count Texts below already
            // carry the content (same reasoning as SmartPlaylistCard's null'd artwork).
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(TextGap))
        Text(
            text = album.title,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = songCountLabel(album.songCount),
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "1 song" / "N songs". */
private fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"

@Composable
private fun AlbumCardArtwork(
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
            .clip(MaterialTheme.shapes.extraLarge)
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
                modifier = Modifier.size(ArtworkIconSize),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Album Card - Light")
@Preview(name = "Album Card - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAlbumCard() {
    RollaMusicPlayerTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlbumCard(
                album = Album(
                    id = 1L,
                    title = "A Night at the Opera",
                    artist = "Queen",
                    songCount = 12,
                    artworkUri = "",
                ),
                onClick = {},
            )
            AlbumCard(
                album = Album(
                    id = 2L,
                    title = "The Wall",
                    artist = "Pink Floyd",
                    songCount = 1,
                    artworkUri = "",
                ),
                onClick = {},
            )
        }
    }
}
