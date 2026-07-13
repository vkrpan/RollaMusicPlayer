package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Song

private val ThumbnailSize = 56.dp
private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 16.dp
private val ArtworkToTextGap = 12.dp
private val DividerStartPadding = 84.dp

/**
 * A single song row. [onLongClick] and [selectionModeActive] together drive library-style
 * multi-select: a long press is the caller cue to enter selection mode (see LibraryScreen),
 * [selectionModeActive] then swaps the trailing "more options" icon for a [Checkbox] mirroring
 * [selected] on every row (not just the one that started the gesture), and [onClick] is left to the
 * caller to redefine as "toggle selection" for as long as selection mode stays active. All three
 * new parameters default to their single-song, non-selectable behavior so every existing call site
 * (Songs list, playlist detail, etc.) keeps compiling and rendering exactly as before.
 *
 * [trackNumber] swaps the leading artwork thumbnail for a fixed-width numeric label -- intended for
 * album detail, where every row already shares the same album art, so the disc track number is the
 * more useful leading affordance (see AlbumDetailScreen in `:feature:library`). Defaults to null,
 * which keeps every existing call site's leading-artwork rendering byte-identical.
 */
@Suppress("LongParameterList")
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    onMoreClick: () -> Unit = {},
    selected: Boolean = false,
    selectionModeActive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        SongListItemContent(
            song = song,
            onClick = onClick,
            trackNumber = trackNumber,
            onMoreClick = onMoreClick,
            selected = selected,
            selectionModeActive = selectionModeActive,
            onLongClick = onLongClick,
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = DividerStartPadding),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItemContent(
    song: Song,
    onClick: () -> Unit,
    trackNumber: Int?,
    onMoreClick: () -> Unit,
    selected: Boolean,
    selectionModeActive: Boolean,
    onLongClick: (() -> Unit)?,
) {
    val rowBackground = if (selectionModeActive && selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .selectionSemantics(selectionModeActive = selectionModeActive, selected = selected)
            .padding(start = RowHorizontalPadding, top = RowVerticalPadding, bottom = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongItemLeading(song = song, trackNumber = trackNumber)
        Spacer(modifier = Modifier.width(ArtworkToTextGap))
        SongInfo(title = song.title, artist = song.artist, modifier = Modifier.weight(1f))
        SongItemTrailing(
            selectionModeActive = selectionModeActive,
            selected = selected,
            song = song,
            onMoreClick = onMoreClick,
        )
    }
}

/**
 * Adds selected/stateDescription semantics only while [selectionModeActive] is true -- outside
 * selection mode a row is not part of any selectable set, so it must not carry `selected`
 * semantics at all (TalkBack would otherwise announce every ordinary row as "not selected").
 */
private fun Modifier.selectionSemantics(selectionModeActive: Boolean, selected: Boolean): Modifier =
    if (selectionModeActive) {
        semantics {
            this.selected = selected
            stateDescription = if (selected) "Selected" else "Not selected"
        }
    } else {
        this
    }

/** Trailing slot: a selection [Checkbox] while [selectionModeActive], else the usual more-options button. */
@Composable
private fun SongItemTrailing(
    selectionModeActive: Boolean,
    selected: Boolean,
    song: Song,
    onMoreClick: () -> Unit,
) {
    if (selectionModeActive) {
        Checkbox(checked = selected, onCheckedChange = null)
    } else {
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options for ${song.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SongInfo(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Leading slot: a [TrackNumberLabel] when [trackNumber] is non-null (album detail rows, where the
 * shared album art makes the disc number the more useful affordance), else the [ArtworkThumbnail].
 */
@Composable
private fun SongItemLeading(song: Song, trackNumber: Int?) {
    if (trackNumber != null) {
        TrackNumberLabel(trackNumber = trackNumber)
    } else {
        ArtworkThumbnail(artworkUri = song.artworkUri, contentDescription = song.album)
    }
}

@Composable
private fun ArtworkThumbnail(
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
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Leading track-number slot, swapped in for [ArtworkThumbnail] when the row is given a non-null
 * `trackNumber` (see [SongListItem]). Sized to the same [ThumbnailSize] width so title/artist text
 * still lines up with plain, artwork-led rows elsewhere.
 */
@Composable
private fun TrackNumberLabel(trackNumber: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(ThumbnailSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = trackNumber.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun previewSong(id: String, title: String, trackNumber: Int): Song = Song(
    id = id,
    title = title,
    artist = "Queen",
    album = "A Night at the Opera",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = trackNumber,
    year = 1975,
    contentUri = "content://media/$id",
    artworkUri = "",
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Song List Item - Track Number - Light")
@Preview(name = "Song List Item - Track Number - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSongListItemTrackNumber() {
    RollaMusicPlayerTheme {
        Column {
            SongListItem(
                song = previewSong("1", "Bohemian Rhapsody", trackNumber = 1),
                onClick = {},
                trackNumber = 1,
            )
            SongListItem(
                song = previewSong("2", "You're My Best Friend", trackNumber = 2),
                onClick = {},
                trackNumber = 2,
            )
        }
    }
}
