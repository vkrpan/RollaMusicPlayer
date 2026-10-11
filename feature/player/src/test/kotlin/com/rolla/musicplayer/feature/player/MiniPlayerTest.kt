@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A 360 dp screen and NATIVE graphics: the pill is a row holding text, measured here against the One UI geometry.
// The host matches MiniPlayerSmokeTest: SharedTransitionLayout + AnimatedContent around the production MiniPlayer.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The height is read from a Box wrapping the production MiniPlayer, so it is the whole footprint, margins included:
    // the merged body node sits inside the margin padding and would not see a vertical margin.
    // Mutation: bring back the 4 dp vertical padding (68 dp) or the old 64 dp min height → RED.
    @Test
    fun pillIsMiniPlayerHeightTallWithSideMargins() {
        setMiniPlayer()
        val footprint = composeRule.onNodeWithTag(PILL_HOST, useUnmergedTree = true).getBoundsInRoot()
        footprint.height.assertIsEqualTo(RollaDimens.miniPlayerHeight, "pill footprint height", tolerance = 0.5.dp)
        val body = composeRule.onNodeWithContentDescription(OPEN_PLAYER, substring = true).getBoundsInRoot()
        body.left.assertIsEqualTo(RollaDimens.miniPlayerSideMargin, "pill start", tolerance = 0.5.dp)
    }

    // Reduced motion turns the marquee off, so a title wider than the pill must ellipsize, not be cut mid-glyph. The
    // setting is written before setContent, where rememberReducedMotion() reads it. Were it not picked up, the marquee
    // would measure the title unbounded and nothing would be ellipsized, so this cannot pass vacuously.
    // Mutation: overflow = TextOverflow.Clip unconditionally → RED.
    @Test
    fun longTitleEllipsizesUnderReducedMotion() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        setMiniPlayer(song = SONG.copy(title = LONG_TITLE))
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(LONG_TITLE, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("title overflows the pill", layout.hasVisualOverflow)
        assertTrue("first line is ellipsized", layout.isLineEllipsized(0))
    }

    // Mutation: keep the old 48 dp artwork or 8 dp start padding → RED.
    @Test
    fun artIsA38DpCircleAtTheMeasuredStart() {
        setMiniPlayer()
        val art = composeRule.onNodeWithTag(MINI_PLAYER_ART_TAG, useUnmergedTree = true).getBoundsInRoot()
        art.width.assertIsEqualTo(RollaDimens.miniPlayerArt, "art size", tolerance = 0.5.dp)
        art.left.assertIsEqualTo(
            RollaDimens.miniPlayerSideMargin + RollaDimens.miniPlayerArtStart,
            "art start",
            tolerance = 0.5.dp,
        )
    }

    // Mutation: leave the Queue button onClick as {} → RED.
    @Test
    fun queueButtonCallsOnQueue() {
        var queued = 0
        setMiniPlayer(onQueue = { queued++ })
        composeRule.onNodeWithContentDescription("Queue").performClick()
        assertEquals(1, queued)
    }

    // Mutation: change the wording or delete the semantics block → RED (baseline-profile contract). Dropping only
    // mergeDescendants is an equivalent mutant: the body is clickable, and clickable nodes merge their descendants.
    @Test
    fun bodyKeepsTheOpenPlayerDescription() {
        setMiniPlayer()
        composeRule.onNodeWithContentDescription("Now playing: Song title by Artist. Open player.").assertExists()
    }

    // UnusedContentLambdaTargetStateParameter: the Unit target state only mints the AnimatedVisibilityScope.
    @Suppress("UnusedContentLambdaTargetStateParameter")
    private fun setMiniPlayer(song: Song = SONG, onQueue: () -> Unit = {}) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit, label = "miniPlayerTest") {
                        // As MiniPlayerHostTest.setRealPill: the tag sits on a wrapping Box, whose bounds are the
                        // pill's whole footprint, margins included.
                        Box(modifier = Modifier.testTag(PILL_HOST)) {
                            MiniPlayer(
                                song = song,
                                isPlaying = false,
                                onPrevious = {},
                                onTogglePlayPause = {},
                                onNext = {},
                                onQueue = onQueue,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val OPEN_PLAYER = "Open player."
        const val PILL_HOST = "pill_host"
        const val LONG_TITLE = "An exceptionally long song title that cannot possibly fit inside the mini player pill"
        val SONG = Song(
            id = "1",
            title = "Song title",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180_000L,
            trackNumber = 1,
            year = 2024,
            contentUri = "content://media/1",
            artworkUri = "",
        )
    }
}
