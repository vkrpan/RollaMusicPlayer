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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingTitle
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AddToPlaylistSheetHost
import com.rolla.musicplayer.core.ui.PlaylistNameDialog
import kotlinx.coroutines.flow.StateFlow

@Suppress("LongMethod", "LongParameterList")
@Composable
fun NowPlayingRoute(
    onNavigateUp: () -> Unit,
    onEqualizerClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    viewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()
    val onTogglePlayPause = remember(viewModel) { viewModel::togglePlayPause }
    val onPrevious = remember(viewModel) { viewModel::previous }
    val onNext = remember(viewModel) { viewModel::next }
    val onSeekTo = remember(viewModel) { viewModel::seekTo }
    val onCycleRepeat = remember(viewModel) { viewModel::cycleRepeatMode }
    val onToggleFavorite = remember(viewModel) { viewModel::toggleFavorite }
    val onToggleShuffle = remember(viewModel) {
        {
            viewModel.setShuffle(
                if (viewModel.shuffleMode.value == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON,
            )
        }
    }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    NowPlayingScreen(
        song = currentSong,
        isPlaying = isPlaying,
        positionMs = viewModel.positionMs,
        durationMs = viewModel.durationMs,
        shuffleMode = shuffleMode,
        repeatMode = repeatMode,
        isFavorite = isFavorite,
        onNavigateUp = onNavigateUp,
        onEqualizerClick = onEqualizerClick,
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
    onNavigateUp: () -> Unit,
    onEqualizerClick: () -> Unit,
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
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Top stays owned by NowPlayingTopBar (a TopAppBar, which reserves the status-bar inset
        // itself and dominates innerPadding.top regardless of this value). No bottomBar is set on
        // this route (the outer app Scaffold's bottomBar is hidden here), so without an explicit
        // bottom inset the transport row would render flush under the system nav bar.
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            NowPlayingTopBar(
                onNavigateUp = onNavigateUp,
                onEqualizerClick = onEqualizerClick,
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
    val reducedMotion = isReducedMotion()
    val metadataAlphaState = animatedContentScope.transition.animateFloat(
        transitionSpec = { if (reducedMotion) snap() else tween(durationMillis = 220) },
        label = "metadata_alpha",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val boundsTransform = remember(reducedMotion) { artworkBoundsTransform(reducedMotion) }
    val artworkModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(1f)
            .sharedElement(
                state = rememberSharedContentState(key = NowPlayingTransitionKey.ARTWORK),
                animatedVisibilityScope = animatedContentScope,
                boundsTransform = boundsTransform,
                renderInOverlayDuringTransition = true,
            )
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NowPlayingArtwork(
            artworkUri = song?.artworkUri.orEmpty(),
            contentDescription = song?.title.orEmpty(),
            modifier = artworkModifier,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = metadataAlphaState.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            NowPlayingTitleArtist(title = song?.title.orEmpty(), artist = song?.artist.orEmpty())
            Spacer(Modifier.height(16.dp))
            NowPlayingActionRow(
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NowPlayingSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NowPlayingTransportRow(
                isPlaying = isPlaying,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                onToggleShuffle = onToggleShuffle,
                onPrevious = onPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onCycleRepeat = onCycleRepeat,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NowPlayingTitleArtist(title: String, artist: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.nowPlayingTitle,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = artist,
        style = MaterialTheme.typography.artistName,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingTopBar(
    onNavigateUp: () -> Unit,
    onEqualizerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close player",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = { NowPlayingTopBarActions(onEqualizerClick = onEqualizerClick) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        modifier = modifier,
    )
}

@Composable
private fun NowPlayingTopBarActions(onEqualizerClick: () -> Unit) {
    IconButton(onClick = {}) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Volume",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    IconButton(onClick = onEqualizerClick) {
        Icon(
            imageVector = Icons.Default.Equalizer,
            contentDescription = "Equalizer",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    IconButton(onClick = {}) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NowPlayingArtwork(
    artworkUri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri.ifEmpty { null })
            .crossfade(true)
            .build()
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (artworkUri.isEmpty()) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingActionRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Queue",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favourite",
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Add to playlist",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Suppress("LongMethod")
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
    Column(modifier = modifier) {
        Slider(
            value = fraction,
            onValueChange = { f ->
                isDragging = true
                dragFraction = f
            },
            onValueChangeFinished = {
                onSeekTo((dragFraction * durationMsValue).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
            ),
        )
        SeekBarTimeLabels(positionMs = positionMsValue, durationMs = durationMsValue)
    }
}

@Composable
private fun SeekBarTimeLabels(positionMs: Long, durationMs: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = positionMs.toTimeString(),
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = durationMs.toTimeString(),
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleMode == ShuffleMode.ON) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause)
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                tint = if (repeatMode == RepeatMode.OFF) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp),
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
