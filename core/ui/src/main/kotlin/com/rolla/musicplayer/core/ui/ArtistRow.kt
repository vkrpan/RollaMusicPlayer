@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Artist

/**
 * Model-aware row for an [Artist] (spec §8.1): a circular 48 dp avatar (the artist's representative cover, or a Person
 * placeholder), the name and "N albums, M tracks". The anatomy is [ListRowScaffold]'s; [onClick] opens the artist.
 */
@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRowScaffold(
        title = artist.name,
        subtitle = "${albumsCountLabel(artist.albumCount)}, ${tracksCountLabel(artist.songCount)}",
        onClick = onClick,
        modifier = modifier,
    ) {
        ArtworkThumbnail(
            artworkUri = artist.artworkUri,
            contentDescription = null,
            shape = CircleShape,
            placeholderGlyph = RollaIcons.Person,
            modifier = Modifier.testTag(ARTIST_AVATAR_TAG),
        )
    }
}

internal const val ARTIST_AVATAR_TAG = "artist_avatar"

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
                artist = Artist(id = 2L, name = "Pink Floyd", albumCount = 1, songCount = 1),
                onClick = {},
            )
        }
    }
}
