@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow

/** Screen inputs a test may change after setContent; a change recomposes the same screen. */
internal class NowPlayingTestInputs(
    shuffle: ShuffleMode = ShuffleMode.OFF,
    repeat: RepeatMode = RepeatMode.OFF,
    favorite: Boolean = false,
) {
    var shuffle by mutableStateOf(shuffle)
    var repeat by mutableStateOf(repeat)
    var favorite by mutableStateOf(favorite)
}

/** Theme colors read inside the composition (accentText is a composable extension). */
internal data class NowPlayingTestColors(val accentText: Color, val primary: Color, val onSurface: Color)

internal val NOW_PLAYING_TEST_SONG = Song(
    id = "1",
    title = "Song title",
    artist = "Artist",
    album = "Album",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2024,
    contentUri = "content://media/1",
    artworkUri = "",
)

/**
 * Hosts the production [NowPlayingScreen] in the dark theme, as NowPlayingScreenSmokeTest does:
 * SharedTransitionLayout + AnimatedContent only mint the transition scopes. [fontScale] overrides the font scale.
 */
// UnusedContentLambdaTargetStateParameter: the Unit target state only mints the AnimatedContentScope.
@Suppress("LongParameterList", "LongMethod", "UnusedContentLambdaTargetStateParameter")
internal fun ComposeContentTestRule.setNowPlayingScreen(
    inputs: NowPlayingTestInputs = NowPlayingTestInputs(),
    targets: NowPlayingOverflowTargets = NowPlayingOverflowTargets(),
    fontScale: Float = 1f,
    onColors: (NowPlayingTestColors) -> Unit = {},
    onEditTags: (Long) -> Unit = {},
    onAlbum: (Long) -> Unit = {},
    onArtist: (String) -> Unit = {},
    onSettings: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onVolume: () -> Unit = {},
    onQueue: () -> Unit = {},
) {
    setContent {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            RollaMusicPlayerTheme(darkTheme = true) {
                val scheme = MaterialTheme.colorScheme
                onColors(NowPlayingTestColors(scheme.accentText, scheme.primary, scheme.onSurface))
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit, label = "nowPlayingTest") {
                        NowPlayingScreen(
                            song = NOW_PLAYING_TEST_SONG,
                            isPlaying = false,
                            positionMs = MutableStateFlow(0L),
                            durationMs = MutableStateFlow(NOW_PLAYING_TEST_SONG.durationMs),
                            shuffleMode = inputs.shuffle,
                            repeatMode = inputs.repeat,
                            isFavorite = inputs.favorite,
                            overflowTargets = targets,
                            onNavigateUp = {},
                            onVolumeClick = onVolume,
                            onEqualizerClick = onEqualizer,
                            onEditTagsClick = onEditTags,
                            onAlbumClick = onAlbum,
                            onArtistClick = onArtist,
                            onSettingsClick = onSettings,
                            onQueueClick = onQueue,
                            onTogglePlayPause = {},
                            onPrevious = {},
                            onNext = {},
                            onSeekTo = {},
                            onToggleShuffle = {},
                            onCycleRepeat = {},
                            onToggleFavorite = {},
                            onAddToPlaylist = {},
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                        )
                    }
                }
            }
        }
    }
    waitForIdle()
}
