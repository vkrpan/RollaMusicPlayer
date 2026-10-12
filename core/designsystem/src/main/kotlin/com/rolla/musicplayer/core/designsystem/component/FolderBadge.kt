@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.folderBadgeFill
import com.rolla.musicplayer.core.designsystem.theme.folderBadgeStroke

/**
 * The small folder mark on a folder thumbnail's bottom-end corner (spec §8.1): a white outline over a black fill in
 * both themes, because it sits on artwork rather than on a theme surface. Decorative: the row's text names the folder.
 */
@Composable
fun FolderBadge(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(RollaDimens.folderBadge)) {
        Icon(
            imageVector = RollaIcons.FolderBadgeFill,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.folderBadgeFill,
            modifier = Modifier.matchParentSize(),
        )
        Icon(
            imageVector = RollaIcons.FolderBadge,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.folderBadgeStroke,
            modifier = Modifier.matchParentSize(),
        )
    }
}
