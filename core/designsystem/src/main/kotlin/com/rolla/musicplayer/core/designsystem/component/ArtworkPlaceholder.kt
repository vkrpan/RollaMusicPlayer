package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderGlyph
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge

/**
 * Missing-artwork stand-in: a gray rounded box with a centered note glyph. It is decorative (no contentDescription);
 * the row or card that owns it describes the item.
 */
@Composable
fun ArtworkPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    large: Boolean = false,
    glyphSize: Dp = RollaDimens.placeholderGlyph,
) {
    val container = if (large) {
        MaterialTheme.colorScheme.artworkPlaceholderLarge
    } else {
        MaterialTheme.colorScheme.artworkPlaceholder
    }
    Box(modifier = modifier.clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = RollaIcons.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.artworkPlaceholderGlyph,
            modifier = Modifier.size(glyphSize),
        )
    }
}
