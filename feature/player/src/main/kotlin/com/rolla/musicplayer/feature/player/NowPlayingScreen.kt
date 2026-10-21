@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.component.ArtworkPlaceholder
import com.rolla.musicplayer.core.designsystem.component.OneUiDropdownMenu
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiMenuItem
import com.rolla.musicplayer.core.designsystem.component.OneUiSeekBar
import com.rolla.musicplayer.core.designsystem.component.nowPlayingBackground
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingArtist
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingGradient
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingTitle
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AddToPlaylistSheetHost
import com.rolla.musicplayer.core.ui.PlaylistNameDialog
import kotlinx.coroutines.flow.StateFlow

internal const val NOW_PLAYING_ARTWORK_TAG = "now_playing_artwork"
internal const val NOW_PLAYING_TRANSPORT_TAG = "now_playing_transport"

// LongParameterList: six navigation callbacks, the two transition scopes, modifier and viewModel.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun NowPlayingRoute(
    onNavigateUp: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEditTagsClick: (songId: Long) -> Unit,
    onAlbumClick: (albumId: Long) -> Unit,
    onArtistClick: (artistName: String) -> Unit,
    onSettingsClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()
    val overflowTargets by viewModel.overflowTargets.collectAsStateWithLifecycle()
    val onTogglePlayPause = remember(viewModel) { viewModel::togglePlayPause }
    val onPrevious = remember(viewModel) { viewModel::previous }
    val onNext = remember(viewModel) { viewModel::next }
    val onSeekTo = remember(viewModel) { viewModel::seekTo }
    val onCycleRepeat = remember(viewModel) { viewModel::cycleRepeatMode }
    val onToggleFavorite = remember(viewModel) { viewModel::toggleFavorite }
    val onToggleShuffle = remember(viewModel) { viewModel::toggleShuffle }
    val onVolumeClick = remember(viewModel) { viewModel::showVolumePanel }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    NowPlayingScreen(
        song = currentSong,
        isPlaying = isPlaying,
        positionMs = viewModel.positionMs,
        durationMs = viewModel.durationMs,
        shuffleMode = shuffleMode,
        repeatMode = repeatMode,
        isFavorite = isFavorite,
        overflowTargets = overflowTargets,
        onNavigateUp = onNavigateUp,
        onVolumeClick = onVolumeClick,
        onEqualizerClick = onEqualizerClick,
        onEditTagsClick = onEditTagsClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onSettingsClick = onSettingsClick,
        onQueueClick = { showQueue = true },
        onTogglePlayPause = onTogglePlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onSeekTo = onSeekTo,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
        onToggleFavorite = onToggleFavorite,
        onAddToPlaylist = { showAddToPlaylistSheet = true },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
    )

    if (showQueue) {
        QueueSheetRoute(onDismissRequest = { showQueue = false })
    }

    if (showAddToPlaylistSheet && !showCreatePlaylistDialog) {
        AddToPlaylistSheetHost(
            userPlaylists = viewModel.userPlaylists,
            onPlaylistSelected = { playlistId ->
                viewModel.addCurrentSongToPlaylist(playlistId)
                showAddToPlaylistSheet = false
            },
            onCreateNewPlaylist = { showCreatePlaylistDialog = true },
            onDismissRequest = { showAddToPlaylistSheet = false },
        )
    }

    if (showCreatePlaylistDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylistAndAddCurrentSong(name)
                showCreatePlaylistDialog = false
                showAddToPlaylistSheet = false
            },
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
fun NowPlayingScreen(
    song: Song?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    overflowTargets: NowPlayingOverflowTargets,
    onNavigateUp: () -> Unit,
    onVolumeClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEditTagsClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        // Full bleed (ruling 9): the gradient sits on the Scaffold itself, so it draws behind the status and
        // navigation bars; the container is transparent so it shows through.
        modifier = modifier.nowPlayingBackground(MaterialTheme.colorScheme.nowPlayingGradient),
        containerColor = Color.Transparent,
        // The top bar pads the status bar itself. The app shell neither pads this route's bottom nor shows the pill
        // on it (MiniPlayerVisibility hides it), so without this inset the transport row would sit under the nav bar.
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            NowPlayingTopBar(
                overflowTargets = overflowTargets,
                onNavigateUp = onNavigateUp,
                onVolumeClick = onVolumeClick,
                onEqualizerClick = onEqualizerClick,
                onEditTagsClick = onEditTagsClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onSettingsClick = onSettingsClick,
            )
        },
    ) { innerPadding ->
        NowPlayingContent(
            song = song,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            isFavorite = isFavorite,
            onQueueClick = onQueueClick,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeekTo = onSeekTo,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun NowPlayingContent(
    song: Song?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    onQueueClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val metadataAlphaState = animatedContentScope.transition.animateFloat(
        transitionSpec = { if (reducedMotion) snap() else tween(durationMillis = 220) },
        label = "metadata_alpha",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val boundsTransform = remember(reducedMotion) { artworkBoundsTransform(reducedMotion) }
    val artworkModifier = with(sharedTransitionScope) {
        Modifier
            .size(RollaDimens.npArtSize)
            .testTag(NOW_PLAYING_ARTWORK_TAG)
            .sharedElement(
                state = rememberSharedContentState(key = NowPlayingTransitionKey.ARTWORK),
                animatedVisibilityScope = animatedContentScope,
                boundsTransform = boundsTransform,
                renderInOverlayDuringTransition = true,
            )
            .clip(MaterialTheme.shapes.extraLarge)
    }
    // The artwork lands npArtTop below the screen top: the top bar above already takes the status bar and headerHeight.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val artTopGap = (RollaDimens.npArtTop - statusBarTop - RollaDimens.headerHeight).coerceAtLeast(0.dp)

    Column(
        // The weighted spacer below works inside verticalScroll because the scroll lifts only the maximum height:
        // fillMaxSize's minimum (the viewport) reaches this Column, and a Column with an unbounded maximum shares the
        // slack up to its minimum among its weights. So the spacer pushes the controls to the bottom; when the content
        // is taller (large font scales) it collapses to zero and the column scrolls. Do not drop fillMaxSize.
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RollaDimens.npSeekInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(artTopGap))
        NowPlayingArtwork(artworkUri = song?.artworkUri.orEmpty(), modifier = artworkModifier)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = metadataAlphaState.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NowPlayingTitleArtist(title = song?.title.orEmpty(), artist = song?.artist.orEmpty())
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = metadataAlphaState.value },
        ) {
            NowPlayingActionRow(
                isFavorite = isFavorite,
                onQueueClick = onQueueClick,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
            )
            NowPlayingSeekBar(positionMs = positionMs, durationMs = durationMs, onSeekTo = onSeekTo)
            Spacer(Modifier.height(RollaDimens.npTransportTopGap))
            NowPlayingTransportRow(
                isPlaying = isPlaying,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                onToggleShuffle = onToggleShuffle,
                onPrevious = onPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onCycleRepeat = onCycleRepeat,
            )
            Spacer(Modifier.height(RollaDimens.npBottomGap))
        }
    }
}

@Composable
private fun NowPlayingTitleArtist(title: String, artist: String) {
    val reducedMotion = rememberReducedMotion()
    Spacer(Modifier.height(RollaDimens.npTitleTopGap))
    Text(
        text = title,
        style = MaterialTheme.typography.nowPlayingTitle,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        // A long title scrolls (One UI). Under reduced motion there is no marquee, so it ellipsizes instead of being
        // cut mid-glyph; the marquee measures its text unbounded, so it never needs an ellipsis.
        overflow = if (reducedMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = if (reducedMotion) Modifier else Modifier.basicMarquee(),
    )
    Spacer(Modifier.height(RollaDimens.npArtistTopGap))
    Text(
        text = artist,
        style = MaterialTheme.typography.nowPlayingArtist,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// The top bar owns every ⋮ target and callback, so it takes them all; a holder type would only add indirection.
@Suppress("LongParameterList")
@Composable
private fun NowPlayingTopBar(
    overflowTargets: NowPlayingOverflowTargets,
    onNavigateUp: () -> Unit,
    onVolumeClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEditTagsClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = RollaDimens.headerHeight)
            .padding(horizontal = RollaDimens.topBarEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiIconButton(icon = RollaIcons.ChevronDown, contentDescription = "Close player", onClick = onNavigateUp)
        Spacer(Modifier.weight(1f))
        OneUiIconButton(icon = RollaIcons.Volume, contentDescription = "Volume", onClick = onVolumeClick)
        OneUiIconButton(icon = RollaIcons.EqualizerBars, contentDescription = "Equaliser", onClick = onEqualizerClick)
        NowPlayingOverflowMenu(
            targets = overflowTargets,
            onEditTagsClick = onEditTagsClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@Composable
private fun NowPlayingOverflowMenu(
    targets: NowPlayingOverflowTargets,
    onEditTagsClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OneUiIconButton(icon = RollaIcons.More, contentDescription = "More options", onClick = { expanded = true })
        OneUiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // A null target disables its item rather than navigating to a bogus id. A song that left the library has
            // no song id or album id (both come from the library row), so only Go to artist and Settings stay enabled.
            OverflowMenuItem("Edit tags", targets.songId) { id ->
                expanded = false
                onEditTagsClick(id)
            }
            OverflowMenuItem("Go to album", targets.albumId) { id ->
                expanded = false
                onAlbumClick(id)
            }
            OverflowMenuItem("Go to artist", targets.artistName) { name ->
                expanded = false
                onArtistClick(name)
            }
            OverflowMenuItem("Settings", Unit) {
                expanded = false
                onSettingsClick()
            }
        }
    }
}

@Composable
private fun <T : Any> OverflowMenuItem(label: String, target: T?, onClick: (T) -> Unit) {
    OneUiMenuItem(text = label, onClick = { if (target != null) onClick(target) }, enabled = target != null)
}

@Composable
private fun NowPlayingArtwork(
    artworkUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri.ifEmpty { null })
            .crossfade(true)
            .build()
    }
    Box(modifier = modifier) {
        // Under the image: shows while it loads, when there is none, and when it fails to decode.
        ArtworkPlaceholder(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            large = true,
            glyphSize = RollaDimens.npArtGlyph,
        )
        AsyncImage(
            model = request,
            // Decorative: NowPlayingTitleArtist announces the same title right below this, so a
            // contentDescription here would make TalkBack read the title twice back-to-back.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NowPlayingActionRow(
    isFavorite: Boolean,
    onQueueClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OneUiIconButton(
            icon = RollaIcons.Queue,
            contentDescription = "Queue",
            onClick = onQueueClick,
            iconSize = RollaDimens.npActionGlyph,
        )
        // onSurface in both states (spec §8.4): the filled glyph alone marks a favourite, never primary.
        OneUiIconButton(
            icon = if (isFavorite) RollaIcons.HeartFilled else RollaIcons.Heart,
            contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
            onClick = onToggleFavorite,
            iconSize = RollaDimens.npActionGlyph,
        )
        OneUiIconButton(
            icon = RollaIcons.Add,
            contentDescription = "Add to playlist",
            onClick = onAddToPlaylist,
            iconSize = RollaDimens.npActionGlyph,
        )
    }
}

@Suppress("LongMethod") // drag-then-commit state plus the seekbar and its labels in one touch box
@Composable
private fun NowPlayingSeekBar(
    positionMs: StateFlow<Long>,
    durationMs: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionMsValue by positionMs.collectAsStateWithLifecycle()
    val durationMsValue by durationMs.collectAsStateWithLifecycle()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = when {
        isDragging -> dragFraction
        durationMsValue > 0L -> positionMsValue.toFloat() / durationMsValue.toFloat()
        else -> 0f
    }
    // The labels sit at the bottom of the seekbar's 48 dp touch box (spec §8.4). They are also padded to start below
    // the thumb, so at large font scales the box grows downward instead of the labels overlapping the track
    // (Review Focus 4). At font scale 1 the padded labels are shorter than the box and land exactly at its bottom.
    Box(modifier = modifier.fillMaxWidth().heightIn(min = RollaDimens.minTouchTarget)) {
        OneUiSeekBar(
            value = fraction,
            onValueChange = { f ->
                isDragging = true
                dragFraction = f
            },
            onValueChangeFinished = {
                onSeekTo((dragFraction * durationMsValue).toLong())
                isDragging = false
            },
            // BaselineProfileGenerator waits on this description; keep it.
            contentDescription = "Playback position",
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        )
        SeekBarTimeLabels(
            positionMs = positionMsValue,
            durationMs = durationMsValue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = (RollaDimens.minTouchTarget + RollaDimens.npSeekThumb) / 2),
        )
    }
}

@Composable
private fun SeekBarTimeLabels(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = positionMs.toTimeString(),
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = durationMs.toTimeString(),
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun NowPlayingTransportRow(
    isPlaying: Boolean,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shuffleOn = shuffleMode == ShuffleMode.ON
    val activeTint = MaterialTheme.colorScheme.accentText
    val idleTint = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NOW_PLAYING_TRANSPORT_TAG),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The description names the control and stateDescription carries its state, so TalkBack reads both.
        OneUiIconButton(
            icon = RollaIcons.Shuffle,
            contentDescription = "Shuffle",
            onClick = onToggleShuffle,
            modifier = Modifier.semantics { stateDescription = if (shuffleOn) "Shuffle on" else "Shuffle off" },
            tint = if (shuffleOn) activeTint else idleTint,
            iconSize = RollaDimens.npTransportGlyph,
        )
        OneUiIconButton(
            icon = RollaIcons.SkipPrevious,
            contentDescription = "Previous",
            onClick = onPrevious,
            iconSize = RollaDimens.npTransportGlyph,
        )
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause)
        OneUiIconButton(
            icon = RollaIcons.SkipNext,
            contentDescription = "Next",
            onClick = onNext,
            iconSize = RollaDimens.npTransportGlyph,
        )
        OneUiIconButton(
            icon = when (repeatMode) {
                RepeatMode.OFF -> RollaIcons.PlayOrder
                RepeatMode.ALL -> RollaIcons.Repeat
                RepeatMode.ONE -> RollaIcons.RepeatOne
            },
            contentDescription = "Repeat",
            onClick = onCycleRepeat,
            modifier = Modifier.semantics {
                stateDescription = when (repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                }
            },
            tint = if (repeatMode == RepeatMode.OFF) idleTint else activeTint,
            iconSize = RollaDimens.npTransportGlyph,
        )
    }
}

// A bare glyph in a 64 dp circular touch target (spec §8.4): no fill, unlike the old 72 dp primary circle.
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(RollaDimens.npPlayTouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) RollaIcons.Pause else RollaIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(RollaDimens.npPlayGlyph),
        )
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_SECOND = 1_000L

private fun Long.toTimeString(): String {
    val minutes = this / MILLIS_PER_MINUTE
    val seconds = (this % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND
    return "%d:%02d".format(minutes, seconds)
}
