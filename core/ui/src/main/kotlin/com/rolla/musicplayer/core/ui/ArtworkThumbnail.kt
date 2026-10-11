package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.component.ArtworkPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The 48 dp list thumbnail: [ArtworkPlaceholder] underneath, with the Coil image drawn on top when there is art.
 * Shared by [SongListItem] and the player's queue rows.
 */
@Composable
fun ArtworkThumbnail(
    artworkUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(RollaDimens.listThumb).clip(MaterialTheme.shapes.small)) {
        ArtworkPlaceholder(modifier = Modifier.size(RollaDimens.listThumb), shape = MaterialTheme.shapes.small)
        if (artworkUri.isNotEmpty()) {
            val context = LocalContext.current
            val imageRequest = remember(artworkUri) {
                ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
