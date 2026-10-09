@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

private const val COLLAGE_CELLS_PER_SIDE = 2
private const val COLLAGE_CELL_COUNT = COLLAGE_CELLS_PER_SIDE * COLLAGE_CELLS_PER_SIDE

/**
 * One UI feature card (spec §8.1): a [RollaDimens.featureCardSize] square art box in the featureCard shape, with the
 * label and count centered below. Art is a placeholder note, a single image, or a 2×2 collage of the first four.
 * Blank URIs (a song with no album art has "") count as no art, matching the smart-playlist preview filter.
 * The whole card is one TalkBack node ("<label>, <count>").
 */
@Composable
fun FeatureCard(
    label: String,
    countLabel: String,
    artworkUris: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // clickable merges the texts into one node. The clip rounds the ripple; the 20 dp bottom corners only reach
    // glyphs on a count line wider than ~124 dp, which "N tracks" never is.
    Column(
        modifier = modifier
            .width(RollaDimens.featureCardSize)
            .clip(MaterialTheme.shapes.featureCard)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeatureCardArt(artworkUris = artworkUris, modifier = Modifier.testTag(FEATURE_CARD_ART_TAG))
        Spacer(Modifier.height(RollaDimens.featureCardLabelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.featureCardLabel,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = countLabel,
            style = MaterialTheme.typography.featureCardCount,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Test tag on the feature card's square art box. */
internal const val FEATURE_CARD_ART_TAG = "feature_card_art"

/** Test tag on each artwork image in the art box (the single image, or one collage cell). */
internal const val FEATURE_CARD_ART_IMAGE_TAG = "feature_card_art_image"

/** Test tag on a collage cell that has no image and shows its own placeholder. */
internal const val FEATURE_CARD_ART_CELL_PLACEHOLDER_TAG = "feature_card_art_cell_placeholder"

@Composable
private fun FeatureCardArt(artworkUris: List<String>, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.featureCard
    val uris = remember(artworkUris) { artworkUris.filter { it.isNotBlank() } }
    Box(modifier = modifier.size(RollaDimens.featureCardSize).clip(shape)) {
        ArtworkPlaceholder(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            large = true,
            glyphSize = RollaDimens.featureCardGlyph,
        )
        when {
            uris.size >= 2 -> Collage(uris.take(COLLAGE_CELL_COUNT), Modifier.matchParentSize())
            uris.size == 1 -> CroppedArtwork(uris.first(), Modifier.matchParentSize())
        }
    }
}

/**
 * A 2×2 grid of cropped artwork filled in reading order (ported from the old SmartPlaylistCollage). With only 2 or 3
 * images each trailing cell shows its own small note placeholder, so the card's large centred glyph is never left
 * half-covered by the images around it.
 */
@Composable
private fun Collage(uris: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(COLLAGE_CELLS_PER_SIDE) { rowIndex ->
            Row(modifier = Modifier.weight(1f)) {
                repeat(COLLAGE_CELLS_PER_SIDE) { columnIndex ->
                    val uri = uris.getOrNull(rowIndex * COLLAGE_CELLS_PER_SIDE + columnIndex)
                    val cellModifier = Modifier.weight(1f).fillMaxSize()
                    if (uri != null) {
                        CroppedArtwork(uri, cellModifier)
                    } else {
                        ArtworkPlaceholder(
                            modifier = cellModifier.testTag(FEATURE_CARD_ART_CELL_PLACEHOLDER_TAG),
                            shape = RectangleShape,
                            large = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CroppedArtwork(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(uri) { ImageRequest.Builder(context).data(uri).crossfade(true).build() }
    AsyncImage(
        model = request,
        // Decorative: the card's merged label + count describe it.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.testTag(FEATURE_CARD_ART_IMAGE_TAG),
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Feature Card - Light")
@Preview(name = "Feature Card - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFeatureCard() {
    RollaMusicPlayerTheme {
        FeatureCard(label = "Favourite tracks", countLabel = "0 tracks", artworkUris = emptyList(), onClick = {})
    }
}
