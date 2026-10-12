@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.FolderBadge
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.folderDisplayPath

/**
 * Model-aware row for a [Folder] (spec §8.1): the folder's first-song art (or a note placeholder) with a FolderBadge
 * in its bottom-end corner, the folder name, and its display path ("/Internal storage/Music"). The anatomy is
 * [ListRowScaffold]'s; [onClick] opens the folder.
 */
@Composable
fun FolderRow(folder: Folder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRowScaffold(
        title = folder.name,
        subtitle = folderDisplayPath(folder.path),
        onClick = onClick,
        modifier = modifier,
    ) {
        Box {
            // Decorative within the merged row: the row's texts already name the folder.
            ArtworkThumbnail(
                artworkUri = folder.artworkUri,
                contentDescription = null,
                modifier = Modifier.testTag(FOLDER_THUMB_TAG),
            )
            FolderBadge(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = RollaDimens.folderBadgeInset, bottom = RollaDimens.folderBadgeInset)
                    .testTag(FOLDER_BADGE_TAG),
            )
        }
    }
}

internal const val FOLDER_THUMB_TAG = "folder_thumb"
internal const val FOLDER_BADGE_TAG = "folder_badge"

@Suppress("UnusedPrivateMember")
@Preview(name = "Folder Row - Light")
@Preview(name = "Folder Row - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFolderRow() {
    RollaMusicPlayerTheme {
        Column {
            FolderRow(folder = Folder(path = "Music", name = "Music", songCount = 12, artworkUri = ""), onClick = {})
            FolderRow(
                folder = Folder(path = "", name = "Internal storage", songCount = 2, artworkUri = ""),
                onClick = {},
            )
        }
    }
}
