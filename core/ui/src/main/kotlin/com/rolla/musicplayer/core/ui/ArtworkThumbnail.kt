package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.layout.Box
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
 * The 48 dp list thumbnail: [ArtworkPlaceholder] underneath, with the Coil image drawn on top when there is art.
 * Shared by [SongListItem], [AlbumRow], [ArtistRow] (a circle with a person glyph), [FolderRow] (with a folder badge)
 * and the player's queue rows. [DetailArtworkHeader] sizes it up to the hero and passes [large] with a
 * [placeholderGlyphSize] to match, so its placeholder is Now Playing's (Phase 6a controller ruling P4); list callers
 * keep the defaults.
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
        // A blank URI is missing art, as AlbumCard treats it (Phase 6a ruling 9): no doomed load over the placeholder.
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
