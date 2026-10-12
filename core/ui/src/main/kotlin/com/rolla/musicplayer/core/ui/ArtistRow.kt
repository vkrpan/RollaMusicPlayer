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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Artist

/**
 * Model-aware row for an [Artist] (spec §8.1): a [RollaDimens.listRowHeight] row with a circular 48 dp avatar (the
 * artist's representative cover, or a Person placeholder) at [RollaDimens.listThumbStart], the name and
 * "N albums, M tracks" at [RollaDimens.listTextStart], and the text ending [RollaDimens.listTrailingEnd] before the
 * row's end (the A–Z rail space). The whole row is one merged clickable node.
 */
@Suppress("LongMethod") // One flat row anatomy (32 lines); splitting it would scatter the row geometry.
@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            ArtworkThumbnail(
                artworkUri = artist.artworkUri,
                contentDescription = null,
                shape = CircleShape,
                placeholderGlyph = RollaIcons.Person,
                modifier = Modifier.testTag(ARTIST_AVATAR_TAG),
            )
            Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
            RowTexts(
                title = artist.name,
                subtitle = "${albumsCountLabel(artist.albumCount)}, ${tracksCountLabel(artist.songCount)}",
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
