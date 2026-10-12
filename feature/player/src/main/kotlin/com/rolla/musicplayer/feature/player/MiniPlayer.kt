@file:Suppress("FunctionNaming")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerArtGlyph
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerArtPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerContainer
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerSubtitle
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerTitle
import com.rolla.musicplayer.core.model.Song

internal const val MINI_PLAYER_ART_TAG = "mini_player_art"

@Suppress("LongParameterList") // the plan's fixed Route contract: two scopes, two callbacks, modifier, viewModel
@Composable
fun MiniPlayerRoute(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBodyClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val onPrevious = remember(viewModel) { viewModel::previous }
    val onTogglePlayPause = remember(viewModel) { viewModel::togglePlayPause }
    val onNext = remember(viewModel) { viewModel::next }
    currentSong?.let { song ->
        MiniPlayer(
            song = song,
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onQueue = onQueueClick,
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
            // No vertical margin either: the measured pill sits directly on the navigation bar.
            .padding(horizontal = RollaDimens.miniPlayerSideMargin)
            // min, not a fixed height: at large fontScale the title/artist column needs more than
            // miniPlayerHeight to lay out without clipping -- clip(CircleShape) + background below both
            // follow the row's actual measured bounds, so the pill just grows taller instead.
            .heightIn(min = RollaDimens.miniPlayerHeight)
            .background(MaterialTheme.colorScheme.miniPlayerContainer, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onBodyClick)
            // mergeDescendants folds the (non-actionable) artwork + title/artist text into one
            // purpose-labeled stop; the transport buttons below remain separately focusable --
            // semantics merging does not swallow descendant nodes that carry their own actions (see
            // RecentSearchesSection.kt's RecentSearchRow for the same idiom without nested actions).
            // The wording is the :baselineprofile contract; do not change it.
            .semantics(mergeDescendants = true) {
                contentDescription = "Now playing: ${song.title} by ${song.artist}. Open player."
            }
            .padding(start = RollaDimens.miniPlayerArtStart, end = RollaDimens.miniPlayerEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerArtwork(
            artworkUri = song.artworkUri,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.size(RollaDimens.miniPlayerArt).testTag(MINI_PLAYER_ART_TAG),
        )
        Spacer(modifier = Modifier.width(RollaDimens.miniPlayerArtTextGap))
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
    val reducedMotion = rememberReducedMotion()
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.miniPlayerTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            // A long title scrolls (One UI). Under reduced motion there is no marquee, so it ellipsizes instead of
            // being cut mid-glyph; the marquee measures its text unbounded, so it never needs an ellipsis.
            overflow = if (reducedMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier.then(if (reducedMotion) Modifier else Modifier.basicMarquee()),
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.miniPlayerSubtitle,
            // White, not onSurfaceVariant (spec §8.3).
            color = MaterialTheme.colorScheme.onSurface,
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
    val artPx = with(LocalDensity.current) { RollaDimens.miniPlayerArt.roundToPx() }
    val request = remember(artworkUri, artPx) {
        ImageRequest.Builder(context).data(artworkUri.ifEmpty { null }).size(artPx).crossfade(true).build()
    }
    val reducedMotion = rememberReducedMotion()
    val boundsTransform = remember(reducedMotion) { artworkBoundsTransform(reducedMotion) }
    val placeholderColor = MaterialTheme.colorScheme.miniPlayerArtPlaceholder
    // The grey disc marks "no artwork" (no uri, or a load that failed), never "still loading". A cover already in the
    // artwork cache can resolve in the same frame (AlbumArtworkInterceptor peeks synchronously), but a cache miss still
    // decodes on IO and returns frames later, and the pill is composed afresh when it comes back from Now Playing, as
    // the shared element's target: only it is drawn, so a loading placeholder would replace the flying artwork with a
    // grey disc on the first frame.
    var showPlaceholder by remember(request) { mutableStateOf(artworkUri.isEmpty()) }
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
                .drawBehind { if (showPlaceholder) drawRect(placeholderColor) },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { state -> showPlaceholder = artworkUri.isEmpty() || state is AsyncImagePainter.State.Error },
            )
            if (artworkUri.isEmpty()) {
                Icon(
                    imageVector = RollaIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.miniPlayerArtGlyph,
                    modifier = Modifier.size(RollaDimens.placeholderGlyph),
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
    // OneUiIconButton defaults: a 24 dp glyph in a 48 dp touch target, tinted onSurface.
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OneUiIconButton(icon = RollaIcons.SkipPrevious, contentDescription = "Previous track", onClick = onPrevious)
        OneUiIconButton(
            icon = if (isPlaying) RollaIcons.Pause else RollaIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlayPause,
        )
        OneUiIconButton(icon = RollaIcons.SkipNext, contentDescription = "Next track", onClick = onNext)
        OneUiIconButton(icon = RollaIcons.Queue, contentDescription = "Queue", onClick = onQueue)
    }
}
