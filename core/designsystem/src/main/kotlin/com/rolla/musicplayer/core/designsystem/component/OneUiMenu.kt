package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.designsystem.theme.songTitle

/** The One UI dropdown menu (spec §8.7): surfaceContainerHigh, the medium shape. */
@Composable
fun OneUiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

/**
 * A menu item in songTitle (spec §8.7), drawn in the menu's onSurface. [destructive] draws the label in destructiveText
 * instead (never error as text), for an item that deletes something, such as the Tracks selection's "Remove artwork".
 */
@Composable
fun OneUiMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    // Through the item's colours, not the Text, so a disabled item still fades (to DISABLED_CONTENT_ALPHA, M3's 0.38).
    val colors = if (destructive) {
        val danger = MaterialTheme.colorScheme.destructiveText
        MenuDefaults.itemColors(textColor = danger, disabledTextColor = danger.copy(alpha = DISABLED_CONTENT_ALPHA))
    } else {
        MenuDefaults.itemColors()
    }
    DropdownMenuItem(
        text = { Text(text = text, style = MaterialTheme.typography.songTitle) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}
