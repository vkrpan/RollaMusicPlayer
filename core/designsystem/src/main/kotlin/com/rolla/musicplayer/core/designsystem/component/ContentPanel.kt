package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.rolla.musicplayer.core.designsystem.theme.panelTop

/**
 * The edge-to-edge surfaceContainer panel that holds each tab's content. Its top corners are rounded, and it runs to
 * the bottom of the screen (spec §7.2).
 */
@Composable
fun ContentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.panelTop)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}
