package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/** The One UI hairline between rows: outlineVariant, inset from the start (and optionally the end). */
@Composable
fun InsetDivider(startInset: Dp, modifier: Modifier = Modifier, endInset: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset, end = endInset),
        thickness = RollaDimens.dividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
