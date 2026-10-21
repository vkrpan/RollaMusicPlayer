package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** A menu item in songTitle (spec §8.7). */
@Composable
fun OneUiMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(text = text, style = MaterialTheme.typography.songTitle) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}
