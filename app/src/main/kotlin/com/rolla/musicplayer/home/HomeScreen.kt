package com.rolla.musicplayer.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTabRow
import com.rolla.musicplayer.core.designsystem.component.OneUiTopBar
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.permissions.MediaPermissionGate
import com.rolla.musicplayer.core.ui.SongSelectionState
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import com.rolla.musicplayer.feature.library.TracksTab
import com.rolla.musicplayer.feature.playlists.PlaylistsTab
import com.rolla.musicplayer.feature.playlists.SmartPlaylistKind
import kotlinx.coroutines.launch

/**
 * Home (spec §7.2): the "Rolla Music" header, the Playlists / Tracks tab row and the pager hosting both tabs. It opens
 * on Tracks. [MediaPermissionGate] wraps the tab row and pager only, so the header (Search, Settings) stays usable
 * while access is denied, and its first grant runs the library sync for every tab.
 */
// Six navigation callbacks plus the injectable ViewModel: each is a distinct exit from Home.
@Suppress("LongParameterList")
@Composable
fun HomeRoute(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    onEditTagsForSelection: (List<Long>) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val pagerState = rememberHomePagerState()
    val selection = rememberSongSelectionState()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    val onGranted = remember(viewModel) { viewModel::onPermissionGranted }
    HomeScreen(
        pagerState = pagerState,
        selection = selection,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onCreatePlaylistClick = { showCreateDialog = true },
        onEditTagsForSelection = { editTagsForSelection(selection, onEditTagsForSelection) },
        pagerContainer = { pager -> MediaPermissionGate(onGranted = onGranted, content = pager) },
    ) { tab ->
        when (tab) {
            HomeTab.TRACKS -> TracksTab(selection = selection, scanState = scanState, onEditTagsClick = onEditTagsClick)
            HomeTab.PLAYLISTS -> PlaylistsTab(
                showCreateDialog = showCreateDialog,
                onDismissCreateDialog = { showCreateDialog = false },
                onPlaylistClick = onPlaylistClick,
                onSmartPlaylistClick = onSmartPlaylistClick,
            )
        }
    }
}

/** Home's pager: opens on Tracks and is saveable, so the selected tab survives rotation and process death. */
@Composable
internal fun rememberHomePagerState(): PagerState =
    rememberPagerState(initialPage = HomeTab.TRACKS.ordinal) { HomeTab.entries.size }

/**
 * The selection bar's batch "Edit tags": hands [onEditTags] the selected ids as MediaStore row ids, then clears the
 * selection. Song.id is the MediaStore row id, so toLongOrNull() only drops a malformed id instead of crashing; an
 * all-malformed selection navigates nowhere (an empty batch has nothing to edit) and stays selected.
 */
internal fun editTagsForSelection(selection: SongSelectionState, onEditTags: (List<Long>) -> Unit) {
    val ids = selection.selectedIds.mapNotNull { it.toLongOrNull() }
    if (ids.isNotEmpty()) {
        onEditTags(ids)
        selection.clear()
    }
}

/**
 * Stateless Home: the header (or the selection bar while [selection] is active), then [pagerContainer] wrapping the
 * tab row and the pager, whose pages render [pageContent] inside a [ContentPanel]. While selecting, back clears the
 * selection and both tab clicks and swipes are disabled. The header owns the status-bar inset; the pages' lists pad
 * for the navigation bar themselves.
 */
// Hoisted pager / selection state, four header events, and two content slots.
@Suppress("LongParameterList")
@Composable
internal fun HomeScreen(
    pagerState: PagerState,
    selection: SongSelectionState,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onEditTagsForSelection: () -> Unit,
    modifier: Modifier = Modifier,
    pagerContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
    pageContent: @Composable (HomeTab) -> Unit,
) {
    BackHandler(enabled = selection.isActive) { selection.clear() }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (selection.isActive) {
            SelectionBar(selection = selection, onEditTagsClick = onEditTagsForSelection)
        } else {
            HomeHeader(
                showCreatePlaylist = pagerState.settledPage == HomeTab.PLAYLISTS.ordinal,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            pagerContainer {
                HomeTabsAndPager(pagerState = pagerState, selection = selection, pageContent = pageContent)
            }
        }
    }
}

@Composable
private fun SelectionBar(selection: SongSelectionState, onEditTagsClick: () -> Unit, modifier: Modifier = Modifier) {
    OneUiDetailTopBar(
        title = "${selection.count} selected",
        onNavigateUp = selection::clear,
        navigationIcon = RollaIcons.Close,
        navigationContentDescription = "Close selection",
        modifier = modifier,
    ) {
        TextButton(
            onClick = onEditTagsClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.accentText),
        ) {
            Text("Edit tags")
        }
    }
}

/** "Rolla Music" with + (on Playlists only, once the pager settles there), Search and the overflow menu. */
@Composable
private fun HomeHeader(
    showCreatePlaylist: Boolean,
    onCreatePlaylistClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    OneUiTopBar(title = "Rolla Music", modifier = modifier) {
        AnimatedVisibility(
            visible = showCreatePlaylist,
            enter = if (reducedMotion) fadeIn(animationSpec = snap()) else fadeIn(),
            exit = if (reducedMotion) fadeOut(animationSpec = snap()) else fadeOut(),
        ) {
            OneUiIconButton(
                icon = RollaIcons.Add,
                contentDescription = "Create playlist",
                onClick = onCreatePlaylistClick,
            )
        }
        OneUiIconButton(icon = RollaIcons.Search, contentDescription = "Search", onClick = onSearchClick)
        OverflowMenu(onSettingsClick = onSettingsClick)
    }
}

@Composable
private fun OverflowMenu(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OneUiIconButton(icon = RollaIcons.More, contentDescription = "More options", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
            )
        }
    }
}

@Composable
private fun HomeTabsAndPager(
    pagerState: PagerState,
    selection: SongSelectionState,
    pageContent: @Composable (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val titles = remember { HomeTab.entries.map { it.title } }
    Column(modifier = modifier.fillMaxSize()) {
        OneUiTabRow(
            titles = titles,
            pagerState = pagerState,
            onTabClick = { index ->
                scope.launch {
                    if (reducedMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
                }
            },
            // Locked while selecting, like the swipe below: the tabs report disabled and ignore taps (no dead tabs).
            enabled = !selection.isActive,
        )
        Spacer(modifier = Modifier.height(RollaDimens.panelTopGap))
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !selection.isActive,
            key = { HomeTab.entries[it].name },
            modifier = Modifier.weight(1f),
        ) { page ->
            ContentPanel { pageContent(HomeTab.entries[page]) }
        }
    }
}
