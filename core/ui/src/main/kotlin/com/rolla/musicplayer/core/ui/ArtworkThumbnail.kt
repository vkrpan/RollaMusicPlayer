package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.component.ArtworkPlaceholder
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The 48 dp list thumbnail of a track: [ArtworkPlaceholder] underneath, with the Coil image drawn on top when there is
 * art. Shared by [SongListItem], the player's queue rows and the tag editor's header picture (`TagEditorScreen`).
 * Albums, artists and folders carry no picture and use [PlaceholderThumbnail] instead (per-track artwork spec goal 3).
 */
// Each parameter is a distinct look a caller decides; the two placeholder knobs only forward to ArtworkPlaceholder.
@Suppress("LongParameterList")
@Composable
fun ArtworkThumbnail(
    artworkUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    placeholderGlyph: ImageVector = RollaIcons.MusicNote,
    large: Boolean = false,
    placeholderGlyphSize: Dp = RollaDimens.placeholderGlyph,
) {
    PlaceholderThumbnail(
        modifier = modifier,
        shape = shape,
        placeholderGlyph = placeholderGlyph,
        large = large,
        placeholderGlyphSize = placeholderGlyphSize,
    ) {
        // A blank URI is missing art (Phase 6a ruling 9): no doomed load over the placeholder.
        if (artworkUri.isNotBlank()) {
            val context = LocalContext.current
            val imageRequest = remember(artworkUri) {
                ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * The 48 dp thumbnail frame with only the placeholder: the stand-in for albums ([AlbumRow]), artists ([ArtistRow], a
 * circle with a person glyph) and folders ([FolderRow]), which carry no picture (per-track artwork spec goal 3), and
 * [ArtworkThumbnail]'s base, which draws a track's image over it as [content]. [DetailArtworkHeader] sizes it up to
 * the hero and passes [large] with a [placeholderGlyphSize] to match, so its placeholder is Now Playing's (Phase 6a
 * controller ruling P4); list callers keep the defaults.
 */
// The same distinct looks as ArtworkThumbnail, plus the slot it fills.
@Suppress("LongParameterList")
@Composable
internal fun PlaceholderThumbnail(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    placeholderGlyph: ImageVector = RollaIcons.MusicNote,
    large: Boolean = false,
    placeholderGlyphSize: Dp = RollaDimens.placeholderGlyph,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // The box's clip is the only rounding: the placeholder underneath is drawn rectangular, so every caller's shape
    // (rounded square for tracks and albums, a circle for artists) is decided here.
    Box(modifier = modifier.size(RollaDimens.listThumb).clip(shape)) {
        ArtworkPlaceholder(
            modifier = Modifier.matchParentSize(),
            shape = RectangleShape,
            large = large,
            glyphSize = placeholderGlyphSize,
            glyph = placeholderGlyph,
        )
        content()
    }
}
