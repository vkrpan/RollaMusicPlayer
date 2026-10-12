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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album

/**
 * Model-aware row for an [Album] (spec §8.1): a [RollaDimens.listRowHeight] row with the 48 dp rounded-square album
 * art (or a note placeholder) at [RollaDimens.listThumbStart], the title and "Artist, N tracks" at
 * [RollaDimens.listTextStart], and the text ending [RollaDimens.listTrailingEnd] before the row's end (the A–Z rail
 * space). There is no trailing overflow: the whole row is one merged clickable node, and [onClick] opens the album.
 */
@Composable
fun AlbumRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The hairline overlays the row's bottom edge so the row pitch stays exactly listRowHeight.
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RollaDimens.listRowHeight)
                .clickable(onClick = onClick)
                .padding(end = RollaDimens.listTrailingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(RollaDimens.listThumbStart))
            // Decorative within the merged row: the row's texts already name the album.
            ArtworkThumbnail(album.artworkUri, null)
            Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
            RowTexts(
                title = album.title,
                subtitle = "${album.artist}, ${tracksCountLabel(album.songCount)}",
                modifier = Modifier.weight(1f),
            )
        }
        InsetDivider(
            startInset = RollaDimens.listTextStart,
            endInset = RollaDimens.listDividerEnd,
            modifier = Modifier.align(Alignment.BottomStart),
        )
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
