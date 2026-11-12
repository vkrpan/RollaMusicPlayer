package com.rolla.musicplayer.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.LocalNavigationBarPadded
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiDropdownMenu
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiMenuItem
import com.rolla.musicplayer.core.designsystem.component.OneUiTabRow
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTopBar
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.chipLabel
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.permissions.MediaPermissionGate
import com.rolla.musicplayer.core.permissions.MediaPermissionState
import com.rolla.musicplayer.core.permissions.rememberMediaPermissionState
import com.rolla.musicplayer.core.ui.SongSelectionState
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import com.rolla.musicplayer.feature.library.AlbumsTab
import com.rolla.musicplayer.feature.library.ArtistsTab
import com.rolla.musicplayer.feature.library.FoldersTab
import com.rolla.musicplayer.feature.library.ScanState
import com.rolla.musicplayer.feature.library.TracksTab
import com.rolla.musicplayer.feature.playlists.FavouritesTab
import com.rolla.musicplayer.feature.playlists.PlaylistsTab
import com.rolla.musicplayer.feature.playlists.SmartPlaylistKind
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkActions
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkResult
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkViewModel
import kotlinx.coroutines.launch

/**
 * Home (spec §7.2): the "Rolla Music" header, the Favourites / Playlists / Tracks / Albums / Artists / Folders tab row
 * and the pager hosting those six tabs. It opens on Tracks. [MediaPermissionGate] wraps the tab row and pager only, so
 * the header (Search, Settings) stays usable while access is denied, and its first grant runs the library sync for
 * every tab through [HomeViewModel.onPermissionGranted] (Phase 2 ruling 2; the gate keys it on the status, so it runs
 * once per grant, never on recomposition). The [permission] state is hoisted here (Phase 6b ruling 8), so the header
 * also knows when access is denied and hides + and the selection bar. Losing access also drops the saved create-dialog
 * flag, so a dialog requested before access was lost never pops after a re-grant. Album, artist and folder taps leave
 * Home through [onAlbumClick] (album id), [onArtistClick] (artist name) and [onFolderClick] (folder path); the Tracks
 * tab's Search button through [onFindTracksClick].
 *
 * The selection bar's ⋮ is [SelectionArtworkActions] (Set / Remove artwork for the selection, per-track artwork Task 8,
 * rulings P2 and P23). Its [artworkViewModel] lives here, scoped like Home itself, so its isWorking is read even while
 * the bar is not composed: it is [HomeScreen]'s selectionBusy, which freezes the selection and locks the bar until the
 * write ends. Its messages go to Home's snackbar from this route's scope, so a message outlives the bar that sent it.
 */
// Eleven navigation callbacks plus the injectable ViewModels and permission state: each callback is a distinct exit
// from Home. LongMethod: HomeScreen's wiring plus the selection actions' snackbar and busy state, one flat call.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun HomeRoute(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageTagsClick: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    onFindTracksClick: () -> Unit,
    onEditTagsForSelection: (List<Long>) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    artworkViewModel: SelectionArtworkViewModel = hiltViewModel(),
    permission: MediaPermissionState = rememberMediaPermissionState(),
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val artworkState by artworkViewModel.uiState.collectAsStateWithLifecycle()
    val selection = rememberSongSelectionState()
    val createDialog = rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    HomeScreen(
        pagerState = rememberHomePagerState(),
        selection = selection,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onManageTagsClick = onManageTagsClick,
        onCreatePlaylistClick = { createDialog.value = true },
        onEditTagsForSelection = { editTagsForSelection(selection, onEditTagsForSelection) },
        accessGranted = permission.isGranted,
        onAccessLost = { createDialog.value = false },
        pagerContainer = { pager -> MediaPermissionGate(permission, viewModel::onPermissionGranted, content = pager) },
        selectionActions = { ids, onResult ->
            SelectionArtworkActions(
                selectedIds = ids,
                onResult = onResult,
                messages = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                viewModel = artworkViewModel,
            )
        },
        selectionBusy = artworkState.isWorking,
        snackbarHostState = snackbarHostState,
    ) { tab ->
        HomeTabPage(
            tab = tab,
            selection = selection,
            scanState = scanState,
            createDialog = createDialog,
            onEditTagsClick = onEditTagsClick,
            onFindTracksClick = onFindTracksClick,
            onPlaylistClick = onPlaylistClick,
            onSmartPlaylistClick = onSmartPlaylistClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onFolderClick = onFolderClick,
        )
    }
}

/** One Home page per [tab]: the tab-to-page dispatch, in one place (Phase 6b ruling 11). */
// The page state each tab reads (selection, scan state, the create dialog) plus Home's navigation exits. The create
// dialog's flag is HomeRoute's saveable state, shared with Home's + and its access-lost reset; only Playlists reads it.
@Suppress("LongParameterList")
@Composable
private fun HomeTabPage(
    tab: HomeTab,
    selection: SongSelectionState,
    scanState: ScanState,
    createDialog: MutableState<Boolean>,
    onEditTagsClick: (Song) -> Unit,
    onFindTracksClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
) {
    when (tab) {
        HomeTab.FAVOURITES -> FavouritesTab(onEditTagsClick = onEditTagsClick)
        HomeTab.PLAYLISTS -> PlaylistsTab(
            showCreateDialog = createDialog.value,
            onDismissCreateDialog = { createDialog.value = false },
            onPlaylistClick = onPlaylistClick,
            onSmartPlaylistClick = onSmartPlaylistClick,
        )
        HomeTab.TRACKS -> TracksTab(
            selection = selection,
            scanState = scanState,
            onEditTagsClick = onEditTagsClick,
            onFindTracksClick = onFindTracksClick,
        )
        HomeTab.ALBUMS -> AlbumsTab(scanState = scanState, onAlbumClick = onAlbumClick)
        HomeTab.ARTISTS -> ArtistsTab(scanState = scanState, onArtistClick = onArtistClick)
        HomeTab.FOLDERS -> FoldersTab(scanState = scanState, onFolderClick = onFolderClick)
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
 * selection and both tab clicks and swipes are disabled. While [accessGranted] is false (the gate shows its prompt
 * instead of the pages), the header hides + and losing access clears the selection and calls [onAccessLost]. The
 * header owns the status-bar inset; the area below it (pages or prompt) ends at the navigation bar's top, so the pages'
 * lists pad for the mini-player pill alone (ruling P26).
 *
 * The selection bar ends with "Edit tags" and [selectionActions] (ruling P2), which gets the selection as MediaStore
 * ids and a result callback: no failed ids clears the selection, otherwise only the failed tracks stay selected. While
 * [selectionBusy] (the actions are writing), the selection is frozen (rows cannot toggle, ruling P23), ✕, Back and
 * "Edit tags" are disabled, the tabs and pager stay locked, and the bar stays even if the selection empties (a guard:
 * only the result, rescan pruning or access loss can empty it), so the actions stay composed to deliver their result.
 * The title steps down to a smaller style when "N selected" would not fit (ruling P24). [snackbarHostState]'s host
 * sits at the bottom of the area below the header, padded by homeListBottomPadding() so it clears the bar and the
 * pill.
 */
// Hoisted pager / selection state, five header events, the access flag and its loss callback, two content slots, and
// the selection actions with their busy flag and snackbar host. LongMethod: the access effect, Back and one column.
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun HomeScreen(
    pagerState: PagerState,
    selection: SongSelectionState,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageTagsClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onEditTagsForSelection: () -> Unit,
    modifier: Modifier = Modifier,
    accessGranted: Boolean = true,
    onAccessLost: () -> Unit = {},
    pagerContainer: @Composable (@Composable () -> Unit) -> Unit = { it() },
    selectionActions: @Composable (selectedIds: List<Long>, onResult: (SelectionArtworkResult) -> Unit) -> Unit =
        { _, _ -> },
    selectionBusy: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    pageContent: @Composable (HomeTab) -> Unit,
) {
    // Ruling 8: rows hidden behind the permission prompt cannot be acted on, so losing access clears the selection
    // (a restored selection would otherwise come back with access). Until it clears, the bar and Back stay off.
    // onAccessLost lets the caller drop its own page state the same way (HomeRoute's saved create-dialog flag, which
    // would otherwise pop the dialog after a re-grant).
    val currentOnAccessLost by rememberUpdatedState(onAccessLost)
    LaunchedEffect(accessGranted) {
        if (!accessGranted) {
            selection.clear()
            currentOnAccessLost()
        }
    }
    // Ruling P23 (spec §5.5): the selection stays as it is until the write ends, so rows cannot toggle while busy.
    // SideEffect, not LaunchedEffect: the lock applies with the composition that sees busy, not a frame later.
    SideEffect { selection.isLocked = selectionBusy }
    val selecting = accessGranted && (selection.isActive || selectionBusy)
    // While busy, Back is swallowed rather than passed on: leaving Home would cancel the write mid-way.
    BackHandler(enabled = selecting) { if (!selectionBusy) selection.clear() }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (selecting) {
            SelectionBar(
                selection = selection,
                busy = selectionBusy,
                onEditTagsClick = onEditTagsForSelection,
                actions = selectionActions,
            )
        } else {
            HomeHeader(
                // Ruling 8: with access denied PlaylistsTab is not composed, so + would open nothing.
                showCreatePlaylist = accessGranted && pagerState.settledPage == HomeTab.PLAYLISTS.ordinal,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onManageTagsClick = onManageTagsClick,
            )
        }
        HomeBelowHeader(snackbarHostState = snackbarHostState, modifier = Modifier.weight(1f)) {
            pagerContainer {
                HomeTabsAndPager(
                    pagerState = pagerState,
                    locked = selection.isActive || selectionBusy,
                    pageContent = pageContent,
                )
            }
        }
    }
}

/**
 * Everything below the header, the pages or the permission prompt, ends at the navigation bar's top, so the black
 * background shows behind the bar, as on the reference (rulings P22, P26). Lists inside pad for the pill alone
 * (LocalNavigationBarPadded); the pill itself is placed by the app shell's MiniPlayerHost above the bar. Home's
 * snackbar sits at the bottom of this area, above the pill by the same padding (the insets table's Home row).
 */
@Composable
private fun HomeBelowHeader(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
        CompositionLocalProvider(LocalNavigationBarPadded provides true) {
            content()
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = homeListBottomPadding())
                    .testTag(HOME_SNACKBAR_HOST_TAG),
            )
        }
    }
}

/**
 * "N selected" with ✕, then "Edit tags" and the [actions] slot. While [busy], ✕ and "Edit tags" are disabled. The
 * actions get the selection as MediaStore ids (a malformed id is dropped, as [editTagsForSelection] does) and report
 * their result through [applySelectionArtworkResult].
 */
@Composable
private fun SelectionBar(
    selection: SongSelectionState,
    busy: Boolean,
    onEditTagsClick: () -> Unit,
    actions: @Composable (selectedIds: List<Long>, onResult: (SelectionArtworkResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIds = remember(selection.selectedIds) { selection.selectedIds.mapNotNull { it.toLongOrNull() } }
    OneUiDetailTopBar(
        title = "${selection.count} selected",
        onNavigateUp = selection::clear,
        navigationIcon = RollaIcons.Close,
        navigationContentDescription = "Close selection",
        navigationEnabled = !busy,
        // Ruling P24: at large font "N selected" steps down to chipLabel (same bold weight) only when screenTitle
        // would not fit beside "Edit tags" and ⋮.
        compactTitleStyle = MaterialTheme.typography.chipLabel,
        modifier = modifier,
    ) {
        OneUiTextButton(text = "Edit tags", onClick = onEditTagsClick, enabled = !busy)
        actions(selectedIds) { result -> applySelectionArtworkResult(selection, result) }
    }
}

/** Home's snackbar host, for tests that measure where it sits (the insets table's Home row). */
internal const val HOME_SNACKBAR_HOST_TAG = "home_snackbar_host"

/** Spec §5.5: none failed → the selection clears; some failed → only the failed tracks stay selected. */
internal fun applySelectionArtworkResult(selection: SongSelectionState, result: SelectionArtworkResult) {
    if (result.failedIds.isEmpty()) selection.clear() else selection.retainAll(result.failedIds)
}

/**
 * "Rolla Music" with + (on Playlists only, once the pager settles there), Search and the overflow menu (Manage tags,
 * Settings).
 */
// One flag plus one callback per header action (+, Search, and the menu's two items) and the modifier.
@Suppress("LongParameterList")
@Composable
private fun HomeHeader(
    showCreatePlaylist: Boolean,
    onCreatePlaylistClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageTagsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    OneUiTopBar(title = "Rolla Music", modifier = modifier) {
        AnimatedVisibility(
            visible = showCreatePlaylist,
            enter = if (reducedMotion) fadeIn(animationSpec = snap()) else fadeIn(),
            exit = if (reducedMotion) fadeOut(animationSpec = snap()) else fadeOut(),
        ) {
            // The + draws larger than the header's other glyphs on the reference (Phase 7 calibration).
            OneUiIconButton(
                icon = RollaIcons.Add,
                contentDescription = "Create playlist",
                onClick = onCreatePlaylistClick,
                iconSize = RollaDimens.headerActionIconLarge,
            )
        }
        OneUiIconButton(icon = RollaIcons.Search, contentDescription = "Search", onClick = onSearchClick)
        OverflowMenu(onManageTagsClick = onManageTagsClick, onSettingsClick = onSettingsClick)
    }
}

/** The header's ⋮ menu: "Manage tags" (custom tags phase 3), then "Settings". */
@Composable
private fun OverflowMenu(onManageTagsClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OneUiIconButton(icon = RollaIcons.More, contentDescription = "More options", onClick = { expanded = true })
        OneUiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OneUiMenuItem(
                text = "Manage tags",
                onClick = {
                    expanded = false
                    onManageTagsClick()
                },
            )
            OneUiMenuItem(
                text = "Settings",
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
            )
        }
    }
}

/**
 * The tab row and the pager. While [locked] (a selection is active, or its artwork is being written even if the
 * selection emptied), tab clicks and swipes do nothing.
 */
// LongMethod: the tab row, the gap and the pager with its inset, drag lock and panel, in one flat column.
@Suppress("LongMethod")
@Composable
private fun HomeTabsAndPager(
    pagerState: PagerState,
    locked: Boolean,
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
            enabled = !locked,
        )
        Spacer(modifier = Modifier.height(RollaDimens.panelTopGap))
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !locked,
            key = { HomeTab.entries[it].name },
            modifier = Modifier
                .weight(1f)
                .then(if (locked) Modifier.swallowHorizontalDrags() else Modifier),
        ) { page ->
            ContentPanel { pageContent(HomeTab.entries[page]) }
        }
    }
}

/**
 * Claims horizontal drags over the pager while selecting, so a sideways swipe is a no-op instead of a tap (device
 * defect D2). With `userScrollEnabled = false` nothing consumed a horizontal drag, and a swipe that stayed inside a
 * full-width row reached the row's `combinedClickable` as a tap and toggled it.
 *
 * Gesture semantics (Compose Foundation 1.7.3):
 * - Pointer events run the Main pass child-first, so a row's tap detector sees each move before this ancestor does.
 *   Once horizontal movement passes touch slop, this detector consumes the change (and every later one) in Main.
 *   `waitForUpOrCancellation`, which drives both `clickable` and `combinedClickable`, then sees the consumption in its
 *   Final-pass check (or in Main on the next event) and cancels the press, so no click fires on release.
 * - The slop check is horizontal-only: a vertical drag never crosses it. The Tracks `LazyColumn` (a child) claims a
 *   vertical drag in Main first, and this detector bails when it sees the drag already consumed, so the list still
 *   scrolls while selecting.
 * - A tap never moves past touch slop, so it is never claimed and still toggles its row.
 */
private fun Modifier.swallowHorizontalDrags(): Modifier =
    pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume() } }
