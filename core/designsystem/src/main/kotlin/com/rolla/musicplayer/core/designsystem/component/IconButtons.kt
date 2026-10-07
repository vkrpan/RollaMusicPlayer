package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The circle buttons in list headers (Shuffle / Play, spec §6). The circle is surfaceContainerHigh and is drawn at the
 * measured 34 dp. The button occupies 48 dp of layout space (7 dp of slack per side), so callers outside SortHeader
 * must account for that footprint.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(RollaDimens.circleButtonSize)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(RollaDimens.circleButtonGlyph),
        )
    }
}

/**
 * A plain 48 dp icon button tinted onSurface, for top-bar actions and transport controls. The glyph is drawn at
 * [iconSize] inside the 48 dp touch target. When disabled it renders at [DISABLED_CONTENT_ALPHA] and ignores input.
 */
// Mirrors M3 IconButton's own inputs plus the glyph's tint and size; a holder type would only add indirection.
@Suppress("LongParameterList")
@Composable
fun OneUiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = RollaDimens.iconSize,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA),
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
