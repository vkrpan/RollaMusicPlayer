@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FAKE_POSITION_MS = 0L
private const val FAKE_DURATION_MS = 200_000L

/**
 * Smoke coverage for [NowPlayingScreen] (Flow 1's terminal hop: expanded Now Playing), following
 * `.claude/skills/add-ui-testing-compose/SKILL.md`. [NowPlayingScreen] itself is the largest
 * hostable stateless composable here: its `NowPlayingContent` child is `private`, so it cannot be
 * reached directly from this androidTest source set, and [NowPlayingScreen] still requires a real
 * `SharedTransitionScope`/`AnimatedContentScope` pair (the artwork shared-element transition) --
 * so, exactly as in [MiniPlayerSmokeTest], this hosts it inside [SharedTransitionLayout] +
 * [AnimatedContent] rather than faking those scopes. Kept smoke-sized: title text plus the two
 * primary transport callbacks (play/pause, favorite).
 */
@RunWith(AndroidJUnit4::class)
class NowPlayingScreenSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSong = testSong()

    @Test
    fun title_isDisplayed() {
        setContent()

        composeTestRule.onNodeWithText(fakeSong.title).assertIsDisplayed()
    }

    @Test
    fun clickingPlayPause_invokesOnTogglePlayPause() {
        var toggled = false
        setContent(onTogglePlayPause = { toggled = true })

        composeTestRule.onNodeWithContentDescription("Play").performScrollTo().performClick()

        assertTrue(toggled)
    }

    @Test
    fun clickingFavorite_invokesOnToggleFavorite() {
        var toggled = false
        setContent(onToggleFavorite = { toggled = true })

        composeTestRule.onNodeWithContentDescription("Add to favourites").performScrollTo().performClick()

        assertTrue(toggled)
    }

    // UnusedContentLambdaTargetStateParameter: the Unit target state is a sentinel -- this
    // AnimatedContent exists solely to mint the AnimatedContentScope NowPlayingScreen requires;
    // there is no state-driven content to key off it.
    @Suppress("LongParameterList", "LongMethod", "UnusedContentLambdaTargetStateParameter")
    private fun setContent(
        onTogglePlayPause: () -> Unit = {},
        onToggleFavorite: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RollaMusicPlayerTheme {
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit, label = "nowPlayingSmokeTest") {
                        NowPlayingScreen(
                            song = fakeSong,
                            isPlaying = false,
                            positionMs = MutableStateFlow(FAKE_POSITION_MS),
                            durationMs = MutableStateFlow(FAKE_DURATION_MS),
                            shuffleMode = ShuffleMode.OFF,
                            repeatMode = RepeatMode.OFF,
                            isFavorite = false,
                            onNavigateUp = {},
                            onEqualizerClick = {},
                            onTogglePlayPause = onTogglePlayPause,
                            onPrevious = {},
                            onNext = {},
                            onSeekTo = {},
                            onToggleShuffle = {},
                            onCycleRepeat = {},
                            onToggleFavorite = onToggleFavorite,
                            onAddToPlaylist = {},
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                        )
                    }
                }
            }
        }
    }
}

private fun testSong() = Song(
    id = "1",
    title = "Bohemian Rhapsody",
    artist = "Queen",
    album = "A Night at the Opera",
    albumId = 1L,
    durationMs = FAKE_DURATION_MS,
    trackNumber = 1,
    year = 1975,
    contentUri = "content://media/external/audio/media/1",
    artworkUri = "",
)
