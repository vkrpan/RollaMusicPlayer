@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.component.ArtworkPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.featureCard
import com.rolla.musicplayer.core.designsystem.theme.featureCardCount
import com.rolla.musicplayer.core.designsystem.theme.featureCardLabel
import com.rolla.musicplayer.core.model.Album

/**
 * Album card for the Albums grid and the artist detail strip (spec §8.1): a square art box in the featureCard shape
 * (art over a placeholder), then a left-aligned one-line title in featureCardLabel and a one-line [subtitle] in
 * featureCardCount. The card's width comes from the caller (the grid cell or the strip's fixed width).
 * - The art box is clipped, and so is the press ripple (Phase 6a ruling 9), but the column is not. Clipping the whole
 *   column would cut the first glyph of the left-aligned subtitle (Phase 4a ruling 8). So the click takes no
 *   indication, and the ripple is drawn by an overlay of the card's size that is clipped to featureCard on its own.
 */
// Two lines over detekt's 30: the ripple overlay is the card's own Box child (matchParentSize needs the BoxScope).
@Suppress("LongMethod")
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = album.artist,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier = modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)) {
        Column {
            AlbumCardArt(
                artworkUri = album.artworkUri,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag(ALBUM_CARD_ART_TAG),
            )
            Spacer(modifier = Modifier.height(RollaDimens.featureCardLabelGap))
            Text(
                text = album.title,
                style = MaterialTheme.typography.featureCardLabel,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.featureCardCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Drawn after the column, so the ripple sits over the art and text, as clickable's own indication would.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.featureCard)
                .indication(interactionSource, LocalIndication.current),
        )
    }
}

@Composable
private fun AlbumCardArt(artworkUri: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(MaterialTheme.shapes.featureCard)) {
        ArtworkPlaceholder(
            modifier = Modifier.matchParentSize().testTag(ALBUM_CARD_PLACEHOLDER_TAG),
            shape = RectangleShape,
            large = true,
            glyphSize = RollaDimens.featureCardGlyph,
        )
        if (artworkUri.isNotBlank()) {
            val context = LocalContext.current
            val request = remember(artworkUri) {
                ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                // Decorative: the card's merged title and subtitle describe it.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

internal const val ALBUM_CARD_ART_TAG = "album_card_art"
internal const val ALBUM_CARD_PLACEHOLDER_TAG = "album_card_placeholder"

@Suppress("UnusedPrivateMember")
@Preview(name = "Album Card - Light")
@Preview(name = "Album Card - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAlbumCard() {
    RollaMusicPlayerTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap)) {
            AlbumCard(
                album = Album(
                    id = 1L,
                    title = "A Night at the Opera",
                    artist = "Queen",
                    songCount = 12,
                    artworkUri = "",
                ),
                onClick = {},
                modifier = Modifier.width(RollaDimens.featureCardSize),
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
                modifier = Modifier.width(RollaDimens.featureCardSize),
                subtitle = tracksCountLabel(1),
            )
        }
    }
}
