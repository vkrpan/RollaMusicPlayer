@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album

/**
 * Model-aware row for an [Album] (spec §8.1): the 48 dp rounded-square album art (or a note placeholder), the title
 * and "Artist, N tracks". The anatomy is [ListRowScaffold]'s; there is no trailing overflow, and [onClick] opens the
 * album.
 */
@Composable
fun AlbumRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRowScaffold(
        title = album.title,
        subtitle = "${album.artist}, ${tracksCountLabel(album.songCount)}",
        onClick = onClick,
        modifier = modifier,
    ) {
        // Decorative within the merged row: the row's texts already name the album.
        ArtworkThumbnail(album.artworkUri, null)
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
