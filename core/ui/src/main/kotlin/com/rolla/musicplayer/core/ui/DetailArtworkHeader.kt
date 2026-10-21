@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName

internal const val DETAIL_ARTWORK_TEST_TAG = "detail_artwork"

/**
 * The hero at the top of AlbumDetail/ArtistDetail (spec §8.7): the artwork at detailArtworkSize (a circle for an
 * artist, the extraLarge rounded square otherwise), then one ellipsized subtitle line. The title lives in the top bar.
 */
@Composable
fun DetailArtworkHeader(
    artworkUri: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    placeholderGlyph: ImageVector = RollaIcons.MusicNote,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = RollaDimens.panelTopGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkThumbnail(
            artworkUri = artworkUri,
            contentDescription = null, // Decorative: the title in the top bar names it (a11y audit precedent).
            shape = if (circular) CircleShape else MaterialTheme.shapes.extraLarge,
            placeholderGlyph = placeholderGlyph,
            // Now Playing's large placeholder at the same 168 dp, not the list thumbnail's (controller ruling P4).
            large = true,
            placeholderGlyphSize = RollaDimens.npArtGlyph,
            modifier = Modifier.size(RollaDimens.detailArtworkSize).testTag(DETAIL_ARTWORK_TEST_TAG),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = RollaDimens.panelTopGap, start = RollaDimens.screenEdge, end = RollaDimens.screenEdge),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Detail Artwork Header - Light")
@Preview(name = "Detail Artwork Header - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDetailArtworkHeader() {
    RollaMusicPlayerTheme {
        Column {
            DetailArtworkHeader(artworkUri = "", subtitle = "Queen · 12 tracks")
            DetailArtworkHeader(
                artworkUri = "",
                subtitle = "3 albums, 27 tracks",
                circular = true,
                placeholderGlyph = RollaIcons.Person,
            )
        }
    }
}
