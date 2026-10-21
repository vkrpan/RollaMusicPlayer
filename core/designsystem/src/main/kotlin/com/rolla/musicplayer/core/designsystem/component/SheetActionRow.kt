package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.designsystem.theme.songTitle

/**
 * One action in a One UI sheet: a [RollaDimens.singleLineRowHeight] row with the 24 dp [icon] centred in a thumbnail-
 * sized box, so the [label] starts at listTextStart like every list row's text. [destructive] tints both
 * destructiveText.
 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.destructiveText else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.singleLineRowHeight)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.listThumbStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(RollaDimens.listThumb), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
        Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
        Text(
            text = label,
            style = MaterialTheme.typography.songTitle,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
