@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.FastScrollContainer
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.buildFastScrollSections
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.folderName
import com.rolla.musicplayer.core.ui.FolderRow

/** Test handle for the Folders tab's list. Never "song_list": only the Tracks list carries that (baseline profile). */
internal const val FOLDER_LIST_TEST_TAG = "folder_list"

/**
 * The Home pager's Folders tab (spec §8.2). Home owns the permission gate and the library sync and passes [scanState]
 * down; this tab owns the list. A row tap opens that folder's FolderDetail through [onFolderClick] with the folder's
 * path, which is how FolderDetail routes; `""` is the root ("Internal storage").
 */
@Composable
fun FoldersTab(
    scanState: ScanState,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoldersTabViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    FoldersTabContent(folders = folders, scanState = scanState, onFolderClick = onFolderClick, modifier = modifier)
}

/**
 * Stateless Folders tab content; tests and previews host this. A scan in progress wins over everything. [folders] is
 * null until Room answers, and then nothing but the header renders, so "No folders found" never flashes on first open.
 * The header has no Shuffle or Play, matching the reference (ruling 9).
 */
@Composable
internal fun FoldersTabContent(
    folders: List<Folder>?,
    scanState: ScanState,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SortHeader(label = "Name")
        when {
            scanState is ScanState.Scanning -> TabScanning()
            folders == null -> Unit
            folders.isEmpty() -> TabMessage("No folders found")
            else -> FolderList(folders = folders, onFolderClick = onFolderClick)
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onFolderClick: (String) -> Unit, modifier: Modifier = Modifier) {
    // The list scrolls under the floating mini-player and the navigation bar, so its bottom padding is both
    // (spec §7.3): the last row can always be scrolled fully into view above the pill.
    val listState = rememberLazyListState()
    // Sections come from the displayed name, the order the repository sorts by (4b ruling 2, Phase 5 ruling 4).
    val sections = remember(folders) { buildFastScrollSections(folders.map { it.name }) }
    FastScrollContainer(listState = listState, sections = sections, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(FOLDER_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = homeListBottomPadding()),
        ) {
            // Keyed by path, the folder's identity and unique; names repeat across parents (ruling 15).
            items(items = folders, key = { folder -> folder.path }) { folder ->
                FolderRow(folder = folder, onClick = { onFolderClick(folder.path) })
            }
        }
    }
}

private val PreviewFolders = listOf("", "Music", "Music/Chill", "Download/Music", "Podcasts", "Recordings")
    .map { path -> Folder(path = path, name = folderName(path), songCount = 12, artworkUri = "") }

@Suppress("UnusedPrivateMember")
@Preview(name = "Folders Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Folders Tab - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewFoldersTabContent() {
    RollaMusicPlayerTheme {
        FoldersTabContent(
            folders = PreviewFolders,
            scanState = ScanState.Done(added = 0, removed = 0),
            onFolderClick = {},
        )
    }
}
