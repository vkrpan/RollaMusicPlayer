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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FAKE_DURATION_MS = 200_000L

/**
 * Smoke coverage for the stateless [MiniPlayer] (Flow 1's mini-player hop -> expand to Now
 * Playing), following `.claude/skills/add-ui-testing-compose/SKILL.md`. [MiniPlayer] requires a
 * real `SharedTransitionScope`/`AnimatedVisibilityScope` pair -- it drives the artwork
 * shared-element transition into Now Playing -- so this hosts it inside [SharedTransitionLayout] +
 * [AnimatedContent] rather than faking those scopes, mirroring the shapes MainActivity's NavHost
 * supplies at the real call site (`AnimatedContentScope` is an `AnimatedVisibilityScope` subtype).
 */
@RunWith(AndroidJUnit4::class)
class MiniPlayerSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSong = testSong()

    @Test
    fun titleAndArtist_areDisplayed() {
        setContent()

        composeTestRule.onNodeWithText(fakeSong.title).assertIsDisplayed()
        composeTestRule.onNodeWithText(fakeSong.artist).assertIsDisplayed()
    }

    @Test
    fun clickingBody_invokesOnBodyClick() {
        var bodyClicked = false
        setContent(onBodyClick = { bodyClicked = true })

        composeTestRule
            .onNodeWithContentDescription("Now playing: ${fakeSong.title} by ${fakeSong.artist}. Open player.")
            .performClick()

        assertTrue(bodyClicked)
    }

    @Test
    fun clickingPlayPause_invokesOnTogglePlayPause() {
        var toggled = false
        setContent(onTogglePlayPause = { toggled = true })

        composeTestRule.onNodeWithContentDescription("Play").performClick()

        assertTrue(toggled)
    }

    // UnusedContentLambdaTargetStateParameter: the Unit target state is a sentinel -- this
    // AnimatedContent exists solely to mint the AnimatedVisibilityScope MiniPlayer requires;
    // there is no state-driven content to key off it.
    @Suppress("LongParameterList", "UnusedContentLambdaTargetStateParameter")
    private fun setContent(
        onBodyClick: () -> Unit = {},
        onTogglePlayPause: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RollaMusicPlayerTheme {
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit, label = "miniPlayerSmokeTest") {
                        MiniPlayer(
                            song = fakeSong,
                            isPlaying = false,
                            onPrevious = {},
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = {},
                            onQueue = {},
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            onBodyClick = onBodyClick,
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
