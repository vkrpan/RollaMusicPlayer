@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The two-line row anatomy AlbumRow, ArtistRow and FolderRow share (spec §8.1):
 * - a [RollaDimens.listRowHeight] row
 * - the 48 dp [thumbnail] at [RollaDimens.listThumbStart]
 * - [title] and [subtitle] at [RollaDimens.listTextStart], ending [RollaDimens.listTrailingEnd] before the row's end
 *   (the A–Z rail space)
 * - a hairline from the text column to the rail
 *
 * The whole row is one merged clickable node.
 */
@Composable
internal fun ListRowScaffold(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
) {
    // The hairline overlays the row's bottom edge so the row pitch stays exactly listRowHeight.
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RollaDimens.listRowHeight)
                .clickable(onClick = onClick)
                .padding(end = RollaDimens.listTrailingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(RollaDimens.listThumbStart))
            thumbnail()
            Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
            RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        }
        InsetDivider(
            startInset = RollaDimens.listTextStart,
            endInset = RollaDimens.listDividerEnd,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
