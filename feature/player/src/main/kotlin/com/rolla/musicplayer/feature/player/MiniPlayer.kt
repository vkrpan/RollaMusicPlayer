@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerContainer
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Song

private val PillHeight = 64.dp
private val PillHorizontalMargin = 8.dp
private val PillVerticalPadding = 4.dp
private val PillStartPadding = 8.dp
private val PillEndPadding = 4.dp
private val ArtworkSize = 48.dp
private val ArtworkToTextGap = 12.dp
private val IconSize = 24.dp

@Composable
fun MiniPlayerRoute(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onBodyClick: () -> Unit = {},
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val onPrevious = remember(viewModel) { viewModel::previous }
    val onTogglePlayPause = remember(viewModel) { viewModel::togglePlayPause }
    val onNext = remember(viewModel) { viewModel::next }
    val onQueue = remember { {} }
    currentSong?.let { song ->
        MiniPlayer(
            song = song,
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onQueue = onQueue,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
            onBodyClick = onBodyClick,
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    onBodyClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // No navigation-bar padding here: the app's mini-player host pads the pill above the bar (its single
            // owner) and measures this composable's height, without the bar, as the content's mini-player inset.
            .padding(horizontal = PillHorizontalMargin, vertical = PillVerticalPadding)
            // min, not a fixed height: at large fontScale the title/artist column needs more than
            // PillHeight to lay out without clipping -- clip(CircleShape) + background below both
            // follow the row's actual measured bounds, so the pill just grows taller instead.
            .heightIn(min = PillHeight)
            .background(MaterialTheme.colorScheme.miniPlayerContainer, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onBodyClick)
            // mergeDescendants folds the (non-actionable) artwork + title/artist text into one
            // purpose-labeled stop; the transport IconButtons below remain separately focusable --
            // semantics merging does not swallow descendant nodes that carry their own actions (see
            // RecentSearchesSection.kt's RecentSearchRow for the same idiom without nested actions).
            .semantics(mergeDescendants = true) {
                contentDescription = "Now playing: ${song.title} by ${song.artist}. Open player."
            }
            .padding(start = PillStartPadding, end = PillEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerArtwork(
            artworkUri = song.artworkUri,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.size(ArtworkSize),
        )
        Spacer(modifier = Modifier.width(ArtworkToTextGap))
        MiniPlayerInfo(title = song.title, artist = song.artist, modifier = Modifier.weight(1f))
        MiniPlayerControls(
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onQueue = onQueue,
        )
    }
}

@Composable
private fun MiniPlayerInfo(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun MiniPlayerArtwork(
    artworkUri: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(artworkUri) {
        ImageRequest.Builder(context).data(artworkUri.ifEmpty { null }).crossfade(true).build()
    }
    val reducedMotion = isReducedMotion()
    val boundsTransform = remember(reducedMotion) { artworkBoundsTransform(reducedMotion) }
    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .sharedElement(
                    state = rememberSharedContentState(key = NowPlayingTransitionKey.ARTWORK),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    renderInOverlayDuringTransition = true,
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (artworkUri.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MiniPlayerControlButton(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "Previous track",
            onClick = onPrevious,
        )
        MiniPlayerControlButton(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlayPause,
        )
        MiniPlayerControlButton(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "Next track",
            onClick = onNext,
        )
        MiniPlayerControlButton(
            imageVector = Icons.Default.QueueMusic,
            contentDescription = "Queue",
            onClick = onQueue,
        )
    }
}

@Composable
private fun MiniPlayerControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(IconSize),
        )
    }
}
