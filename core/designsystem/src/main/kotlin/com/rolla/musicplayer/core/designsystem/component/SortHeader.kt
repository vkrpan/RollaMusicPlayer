package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sortLabel

/**
 * The static "⇅ Name" list header with an optional trailing slot (usually two CircleIconButtons). The label is
 * deliberately not clickable: there is no sort menu (spec §3), and a clickable label would be a fake control.
 */
// Flat declarative layout (icon, gap, label, trailing row); splitting it would only add indirection.
@Suppress("LongMethod")
@Composable
fun SortHeader(label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.sortHeaderHeight)
            .padding(start = RollaDimens.sortHeaderStart, end = RollaDimens.sortHeaderEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = RollaIcons.Sort,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RollaDimens.iconSize),
        )
        Spacer(modifier = Modifier.width(RollaDimens.sortLabelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.sortLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(RollaDimens.circleButtonLayoutGap),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}
