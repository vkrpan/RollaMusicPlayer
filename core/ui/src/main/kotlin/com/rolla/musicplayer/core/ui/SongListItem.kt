package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.component.OneUiCheckbox
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.artistDisplayName

/**
 * A single song row (spec §8.1). [onLongClick] and [selectionModeActive] together drive library-style
 * multi-select: a long press is the caller cue to enter selection mode (see TracksTab / SongSelectionState),
 * [selectionModeActive] then swaps the trailing "more options" icon for a [OneUiCheckbox] mirroring
 * [selected] on every row (not just the one that started the gesture), and [onClick] is left to the
 * caller to redefine as "toggle selection" for as long as selection mode stays active. All three
 * parameters default to their single-song, non-selectable behavior.
 *
 * The leading slot is always the song's own [ArtworkThumbnail]; album detail rows show it like every other track list
 * (per-track artwork spec goal 2a).
 *
 * Geometry comes from [RollaDimens]: a [RollaDimens.listRowHeight] pitch, the thumbnail at
 * [RollaDimens.listThumbStart], text at [RollaDimens.listTextStart], and the trailing 48 dp target ending
 * [RollaDimens.listOverflowEnd] before the row's end (the A–Z rail space, ruling 5).
 */
@Suppress("LongParameterList")
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: () -> Unit = {},
    selected: Boolean = false,
    selectionModeActive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    // The hairline overlays the row's bottom edge so the row pitch stays exactly listRowHeight. The list passes
    // showDivider = false for its last row: One UI draws no line under a list's last row (Phase 7 calibration).
    Box(modifier = modifier) {
        SongListItemContent(
            song = song,
            onClick = onClick,
            onMoreClick = onMoreClick,
            selected = selected,
            selectionModeActive = selectionModeActive,
            onLongClick = onLongClick,
        )
        if (showDivider) {
            InsetDivider(
                startInset = RollaDimens.listTextStart,
                endInset = RollaDimens.listDividerEnd,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItemContent(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    selected: Boolean,
    selectionModeActive: Boolean,
    onLongClick: (() -> Unit)?,
) {
    val rowBackground = rowBackgroundFor(selectionHighlighted = selectionModeActive && selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.listRowHeight)
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .selectionSemantics(selectionModeActive = selectionModeActive, selected = selected)
            // End inset on the content, not the background, so the highlight still runs edge to edge.
            .padding(end = RollaDimens.listOverflowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(RollaDimens.listThumbStart))
        SongItemLeading(song = song)
        Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
        SongInfo(title = song.title, artist = artistDisplayName(song.artist), modifier = Modifier.weight(1f))
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

/** Trailing slot: a selection [OneUiCheckbox] while [selectionModeActive], else the usual more-options button. */
@Composable
private fun SongItemTrailing(
    selectionModeActive: Boolean,
    selected: Boolean,
    song: Song,
    onMoreClick: () -> Unit,
) {
    if (selectionModeActive) {
        SelectionCheckbox(selected = selected)
    } else {
        OneUiIconButton(
            icon = RollaIcons.More,
            contentDescription = "More options for ${song.title}",
            onClick = onMoreClick,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = RollaDimens.overflowGlyph,
        )
    }
}

/**
 * The row owns the click, so the checkbox takes no onCheckedChange (and so has no 48 dp minimum of its own). The
 * minTouchTarget box keeps its centre where the ⋮ glyph's is, so neither the glyph nor the text column moves when
 * selection mode toggles. The row's selected/stateDescription semantics carry the state for TalkBack; the testTag is
 * not announced.
 */
@Composable
private fun SelectionCheckbox(selected: Boolean) {
    Box(
        modifier = Modifier.size(RollaDimens.minTouchTarget).testTag(SONG_SELECTION_CHECKBOX_TAG),
        contentAlignment = Alignment.Center,
    ) {
        OneUiCheckbox(checked = selected, onCheckedChange = null)
    }
}

/** Test tag on the selection-mode checkbox's touch-target box. */
internal const val SONG_SELECTION_CHECKBOX_TAG = "song_selection_checkbox"

/**
 * The multi-select highlight fill behind a selected row; transparent outside selection mode. surfaceContainerHigh
 * keeps the onSurfaceVariant subtitle above AA (ruling 6; primaryContainer failed in dark).
 */
@Composable
private fun rowBackgroundFor(selectionHighlighted: Boolean): Color =
    if (selectionHighlighted) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        Color.Transparent
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

/** Leading slot: the song's own [ArtworkThumbnail]. */
@Composable
private fun SongItemLeading(song: Song) {
    // Decorative within the merged row: announcing the album name here made TalkBack read
    // artwork-description + title + artist as one verbose run (same reasoning as MiniPlayer's
    // null'd artwork). The row's own text carries the content.
    ArtworkThumbnail(
        artworkUri = song.artworkUri,
        contentDescription = null,
        modifier = Modifier.testTag(SONG_ROW_ARTWORK_TEST_TAG),
    )
}

/** Test tag on a song row's leading artwork thumbnail. */
const val SONG_ROW_ARTWORK_TEST_TAG = "song_row_artwork"

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
@Preview(name = "Song List Item - Light")
@Preview(name = "Song List Item - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSongListItem() {
    RollaMusicPlayerTheme {
        Column {
            SongListItem(
                song = previewSong("1", "Bohemian Rhapsody", trackNumber = 1),
                onClick = {},
            )
            SongListItem(
                song = previewSong("2", "You're My Best Friend", trackNumber = 2),
                onClick = {},
                showDivider = false,
            )
        }
    }
}
