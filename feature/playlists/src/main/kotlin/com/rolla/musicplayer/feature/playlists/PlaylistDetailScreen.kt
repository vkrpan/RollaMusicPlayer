@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.playlists

import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AddToPlaylistSheetHost
import com.rolla.musicplayer.core.ui.PlaylistNameDialog
import com.rolla.musicplayer.core.ui.SongListItem

private val SurfaceHorizontalMargin = 8.dp
private val SurfaceVerticalMargin = 8.dp
private val ControlButtonSize = 48.dp
private val ControlIconSize = 24.dp
private val HeaderHorizontalPadding = 16.dp
private val HeaderVerticalPadding = 12.dp
private val EmptyStateHorizontalPadding = 32.dp

// Reorder motion specs (see ui-style-guide.md §9: 200-300ms, spring for interactive elements).
// Non-bouncy + medium-low stiffness settles quickly without overshoot — a reorder needs to read
// as responsive, not as a playful card animation.
private val ReorderPlacementSpring: FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

// Subtle "picked up" feedback for the actively dragged row. Kept restrained per the One
// UI-inspired, content-forward design system — this is a lift, not a bounce.
private const val DRAG_LIFT_SCALE = 1.04f
private val DragLiftElevation = 6.dp
private val DragLiftSpringScale: FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)
private val DragLiftSpringElevation: FiniteAnimationSpec<Dp> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * Stateful entry point for a single playlist detail screen: a user-created playlist or one of
 * the built-in smart playlists (Favourites, Recently played, Most played, Recently added). Same
 * Route/Screen split as every other screen in the codebase (see LibraryRoute in feature:library).
 */
@Suppress("LongMethod")
@Composable
fun PlaylistDetailRoute(
    onNavigateUp: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    PlaylistDetailScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onPlayAll = remember(viewModel) { viewModel::playAll },
        onShuffleAll = remember(viewModel) { viewModel::shuffleAll },
        onSongClick = remember(viewModel) { viewModel::playSong },
        onRemoveSong = remember(viewModel) { viewModel::removeSong },
        onReorder = remember(viewModel) { viewModel::reorder },
        onRenamePlaylist = remember(viewModel) { viewModel::renamePlaylist },
        onDeletePlaylist = remember(viewModel) { viewModel::deletePlaylist },
        onAddToPlaylist = { song -> addToPlaylistSong = song },
    )

    val songPendingPlaylistPick = addToPlaylistSong
    if (songPendingPlaylistPick != null && !showCreatePlaylistDialog) {
        AddToPlaylistSheetHost(
            userPlaylists = viewModel.userPlaylists,
            onPlaylistSelected = { playlistId ->
                viewModel.addSongToPlaylist(songPendingPlaylistPick.id, playlistId)
                addToPlaylistSong = null
            },
            onCreateNewPlaylist = { showCreatePlaylistDialog = true },
            onDismissRequest = { addToPlaylistSong = null },
        )
    }

    if (showCreatePlaylistDialog && songPendingPlaylistPick != null) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylistAndAddSong(name, songPendingPlaylistPick.id)
                showCreatePlaylistDialog = false
                addToPlaylistSong = null
            },
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }
}

@Suppress("LongParameterList")
@Composable
fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    onNavigateUp: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            PlaylistDetailTopBar(
                title = uiState.title,
                isUserPlaylist = uiState.isUserPlaylist,
                onNavigateUp = onNavigateUp,
                onRenamePlaylist = onRenamePlaylist,
                onDeletePlaylist = {
                    onDeletePlaylist()
                    onNavigateUp()
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        PlaylistDetailContent(
            uiState = uiState,
            onPlayAll = onPlayAll,
            onShuffleAll = onShuffleAll,
            onSongClick = onSongClick,
            onRemoveSong = onRemoveSong,
            onReorder = onReorder,
            onAddToPlaylist = onAddToPlaylist,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailTopBar(
    title: String,
    isUserPlaylist: Boolean,
    onNavigateUp: () -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            if (isUserPlaylist) {
                PlaylistDetailOverflowMenu(
                    title = title,
                    onRenamePlaylist = onRenamePlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

/**
 * Overflow menu for a user-created playlist: rename and delete. Never rendered for smart
 * playlists (Favourites, Recently played, etc.) — those can't be renamed or deleted.
 */
@Suppress("LongMethod")
@Composable
private fun PlaylistDetailOverflowMenu(
    title: String,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showMenu = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = {
                showMenu = false
                showRenameDialog = true
            },
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                showMenu = false
                showDeleteDialog = true
            },
        )
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = title,
            onConfirm = { name ->
                onRenamePlaylist(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        DeletePlaylistDialog(
            title = title,
            onConfirm = {
                onDeletePlaylist()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun DeletePlaylistDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete \"$title\"?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "This playlist will be permanently deleted. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
private fun PlaylistDetailContent(
    uiState: PlaylistDetailUiState,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SurfaceHorizontalMargin, vertical = SurfaceVerticalMargin),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            SortControlHeader(
                songCount = uiState.songs.size,
                onShuffleAll = onShuffleAll,
                onPlayAll = onPlayAll,
            )
            when {
                uiState.isLoading -> LoadingContent()
                uiState.songs.isEmpty() -> EmptyPlaylistContent(title = uiState.title)
                else -> PlaylistSongList(
                    songs = uiState.songs,
                    isUserPlaylist = uiState.isUserPlaylist,
                    onSongClick = onSongClick,
                    onRemoveSong = onRemoveSong,
                    onReorder = onReorder,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        }
    }
}

@Composable
private fun SortControlHeader(
    songCount: Int,
    onShuffleAll: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderHorizontalPadding, vertical = HeaderVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$songCount songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        PlaybackControls(onShuffleAll = onShuffleAll, onPlayAll = onPlayAll)
    }
}

@Composable
private fun PlaybackControls(
    onShuffleAll: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        ControlButton(
            icon = Icons.Default.Shuffle,
            contentDescription = "Shuffle all songs",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = MaterialTheme.colorScheme.onSurface,
            onClick = onShuffleAll,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ControlButton(
            icon = Icons.Default.PlayArrow,
            contentDescription = "Play all songs",
            containerColor = MaterialTheme.colorScheme.primary,
            iconTint = MaterialTheme.colorScheme.onPrimary,
            onClick = onPlayAll,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(ControlButtonSize),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(ControlIconSize),
            tint = iconTint,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyPlaylistContent(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No songs in \"$title\" yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EmptyStateHorizontalPadding),
        )
    }
}

/**
 * Long-press drag-to-reorder state for [PlaylistSongList]. Adapted from the standard Compose
 * reorderable-list pattern: the dragged item's visual offset is tracked here (relative to its
 * measured [LazyListItemInfo.offset]) while hit-testing against currently visible items decides
 * when to swap positions. Only wired up for user playlists — smart playlists never construct one.
 *
 * The dragged row is identified by its stable lazy-list key (the song id), not its index: after
 * a swap every row between the old and new slot gets a new index, so an index-based comparison
 * would invalidate all of them, whereas the key follows the one dragged row through the move.
 */
private class DragDropState(
    private val state: LazyListState,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set
    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }

    fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                draggingItemKey = it.key
                draggingItemInitialOffset = it.offset
            }
    }

    fun onDragInterrupted() {
        draggingItemKey = null
        draggingItemDraggedDelta = 0f
        draggingItemInitialOffset = 0
    }

    fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y
        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = state.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) && item.key != draggingItemKey
        }
        if (targetItem != null) {
            onMove(draggingItem.index, targetItem.index)
        }
    }
}

@Composable
private fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * TalkBack-reachable equivalent of the long-press drag reorder ([DragDropState] /
 * `detectDragGesturesAfterLongPress` above) -- a raw pointer-drag gesture has no accessibility
 * affordance of its own, so without this a user playlist's order would be permanently unreachable
 * under TalkBack (audit CRITICAL). "Move up"/"Move down" perform the exact same
 * remove-then-insert-then-persist steps the drag's `onMove`/`onDragEnd` pair performs, just as a
 * single atomic step instead of a per-frame stream. Never attached to smart playlists (see the
 * isUserPlaylist gate at the call site); "Move up" is omitted for the first row and "Move down" for
 * the last, since [orderedSongs]'s own bounds make both directions self-evidently correct without
 * re-deriving them from [isUserPlaylist].
 */
private fun reorderCustomActions(
    orderedSongs: SnapshotStateList<Song>,
    index: Int,
    onReorder: (List<String>) -> Unit,
): List<CustomAccessibilityAction> = buildList {
    if (index > 0) {
        add(
            CustomAccessibilityAction(label = "Move up") {
                orderedSongs.add(index - 1, orderedSongs.removeAt(index))
                onReorder(orderedSongs.map { it.id })
                true
            },
        )
    }
    if (index < orderedSongs.lastIndex) {
        add(
            CustomAccessibilityAction(label = "Move down") {
                orderedSongs.add(index + 1, orderedSongs.removeAt(index))
                onReorder(orderedSongs.map { it.id })
                true
            },
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun PlaylistSongList(
    songs: List<Song>,
    isUserPlaylist: Boolean,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reducedMotion = isReducedMotion()

    // Live drag-preview snapshot. Resets whenever the upstream `songs` list changes — since a
    // persisted reorder round-trips back through `uiState.songs` in the same order that was
    // already shown locally, this reset never causes a visible jump.
    val orderedSongs = remember(songs) { songs.toMutableStateList() }

    // dragDropState and the pointerInput block below outlive `orderedSongs` (they're keyed on
    // stable values), so both must read the *current* snapshot list through these delegates —
    // capturing `orderedSongs` directly would mutate/persist a detached copy if the upstream
    // songs change mid-drag.
    val currentOrderedSongs by rememberUpdatedState(orderedSongs)
    val currentOnReorder by rememberUpdatedState(onReorder)

    val dragDropState = remember(listState) {
        DragDropState(
            state = listState,
            onMove = { from, to -> currentOrderedSongs.add(to, currentOrderedSongs.removeAt(from)) },
        )
    }

    val dragModifier = if (isUserPlaylist) {
        Modifier.pointerInput(dragDropState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                onDragEnd = {
                    dragDropState.onDragInterrupted()
                    currentOnReorder(currentOrderedSongs.map { it.id })
                },
                onDragCancel = { dragDropState.onDragInterrupted() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(Offset(0f, dragAmount.y))
                },
            )
        }
    } else {
        Modifier
    }

    // Placement animation for non-dragged rows sliding into their new slot. Reduced motion is
    // expressed the same way as elsewhere in the codebase (see artworkBoundsTransform in
    // feature:player's NowPlayingTransitionKey.kt): swap spring() for snap(), rather than
    // omitting the modifier — keeps the reflow logic uniform regardless of the motion setting.
    val placementSpec = if (reducedMotion) snap() else ReorderPlacementSpring

    LazyColumn(state = listState, modifier = modifier.then(dragModifier)) {
        itemsIndexed(items = orderedSongs, key = { _, song -> song.id }) { index, song ->
            // derivedStateOf so a draggingItemKey write only invalidates the (at most two) rows
            // whose boolean actually flips, not every visible row. draggingItemKey stays null for
            // smart playlists (the drag modifier is never installed), so no isUserPlaylist check.
            val isDragging by remember(song.id, dragDropState) {
                derivedStateOf { dragDropState.draggingItemKey == song.id }
            }

            // Drag-lift micro-interaction: a subtle scale + shadow bump while a row is picked
            // up, animated in on drag-start and back out on release/cancel. Both values are
            // read only inside graphicsLayer below (not via `by`), so the animation frames
            // never invalidate composition for this row — same deferred-read pattern already
            // used for draggingItemOffset.
            val liftScale = animateFloatAsState(
                targetValue = if (isDragging) DRAG_LIFT_SCALE else 1f,
                animationSpec = if (reducedMotion) snap() else DragLiftSpringScale,
                label = "dragLiftScale",
            )
            val liftElevation = animateDpAsState(
                targetValue = if (isDragging) DragLiftElevation else 0.dp,
                animationSpec = if (reducedMotion) snap() else DragLiftSpringElevation,
                label = "dragLiftElevation",
            )

            SongListItem(
                song = song,
                onClick = { onSongClick(song) },
                onMoreClick = { if (isUserPlaylist) onRemoveSong(song) else onAddToPlaylist(song) },
                // The default "More options for X" is a lie here -- this button performs a direct
                // action (remove, or open the add-to-playlist sheet), never a menu (TalkBack audit
                // HIGH).
                moreContentDescription = if (isUserPlaylist) {
                    "Remove ${song.title} from playlist"
                } else {
                    "Add ${song.title} to a playlist"
                },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = liftScale.value
                        scaleY = liftScale.value
                        shadowElevation = liftElevation.value.toPx()
                        if (isDragging) {
                            translationY = dragDropState.draggingItemOffset
                        }
                    }
                    .then(if (isDragging) Modifier else Modifier.animateItem(placementSpec = placementSpec))
                    .then(
                        if (isUserPlaylist) {
                            Modifier.semantics {
                                customActions = reorderCustomActions(orderedSongs, index, onReorder)
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

private fun previewSongs(): List<Song> = listOf(
    Song(
        id = "1",
        title = "Morning Light",
        artist = "The Wanderers",
        album = "Sunrise",
        albumId = 1L,
        durationMs = 210_000L,
        trackNumber = 1,
        year = 2021,
        contentUri = "content://media/1",
        artworkUri = "",
    ),
    Song(
        id = "2",
        title = "City Lights",
        artist = "Nova Sound",
        album = "Neon",
        albumId = 2L,
        durationMs = 185_000L,
        trackNumber = 2,
        year = 2022,
        contentUri = "content://media/2",
        artworkUri = "",
    ),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlist Detail - Light")
@Preview(name = "Playlist Detail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPlaylistDetailScreen() {
    RollaMusicPlayerTheme {
        PlaylistDetailScreen(
            uiState = PlaylistDetailUiState(
                title = "Workout Mix",
                songs = previewSongs(),
                isUserPlaylist = true,
                isLoading = false,
            ),
            onNavigateUp = {},
            onPlayAll = {},
            onShuffleAll = {},
            onSongClick = {},
            onRemoveSong = {},
            onReorder = {},
            onRenamePlaylist = {},
            onDeletePlaylist = {},
            onAddToPlaylist = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlist Detail Empty - Light")
@Preview(name = "Playlist Detail Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPlaylistDetailScreenEmpty() {
    RollaMusicPlayerTheme {
        PlaylistDetailScreen(
            uiState = PlaylistDetailUiState(
                title = "Favourites",
                songs = emptyList(),
                isUserPlaylist = false,
                isLoading = false,
            ),
            onNavigateUp = {},
            onPlayAll = {},
            onShuffleAll = {},
            onSongClick = {},
            onRemoveSong = {},
            onReorder = {},
            onRenamePlaylist = {},
            onDeletePlaylist = {},
            onAddToPlaylist = {},
        )
    }
}
