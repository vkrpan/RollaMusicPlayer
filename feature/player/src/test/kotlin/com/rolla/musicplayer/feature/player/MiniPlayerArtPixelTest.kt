@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.rolla.musicplayer.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerArtPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerContainer
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.HardwarePixelCopyRule
import com.rolla.musicplayer.core.testing.captureWindow
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The pill's artwork disc while its image loads and after a load fails. Coil's singleton loader is swapped for one
// whose only interceptor decides the outcome, so nothing touches a real file. Captured with PixelCopy, as
// NowPlayingScreenPixelTest: the art's sharedElement records into a RenderNode that a software Canvas cannot draw.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniPlayerArtPixelTest {

    @get:Rule(order = 0)
    val pixelCopyRule = HardwarePixelCopyRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var placeholder = Color.Unspecified
    private var container = Color.Unspecified

    @After
    fun restore() {
        Coil.setImageLoader(ImageLoader(ApplicationProvider.getApplicationContext()))
    }

    // The device defect's last frame: back from Now Playing the pill is composed afresh as the shared element's target
    // and only it is drawn, so a grey "loading" disc replaced the flying artwork for a frame. While loading, the disc
    // shows the pill behind it. Mutation: restore `.background(miniPlayerArtPlaceholder)` after the clip → RED.
    @Test
    fun loadingArtworkDrawsNoPlaceholder() {
        setPill(Interceptor { awaitCancellation() })
        val pixels = artCentre()
        assertNotEquals(placeholder.compositeOver(container), container)
        assertTrue("loading art shows the pill: $pixels", pixels.all { isColorNear(container, it) })
    }

    // A song whose artwork cannot be loaded still shows the grey disc. Mutation: onState sets
    // `showPlaceholder = artworkUri.isEmpty()` (drops the Error case) → RED.
    @Test
    fun failedArtworkDrawsThePlaceholder() {
        setPill(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("no art")) })
        val pixels = artCentre()
        // The placeholder token is translucent: the disc is it composited over the pill.
        val disc = placeholder.compositeOver(container)
        assertTrue("failed art shows the placeholder: $pixels", pixels.all { isColorNear(disc, it) })
    }

    // UnusedContentLambdaTargetStateParameter: the Unit target state only mints the AnimatedVisibilityScope.
    @Suppress("UnusedContentLambdaTargetStateParameter")
    private fun setPill(interceptor: Interceptor) {
        Coil.setImageLoader(
            ImageLoader.Builder(ApplicationProvider.getApplicationContext())
                .components { add(interceptor) }
                .build(),
        )
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                placeholder = MaterialTheme.colorScheme.miniPlayerArtPlaceholder
                container = MaterialTheme.colorScheme.miniPlayerContainer
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit, label = "miniPlayerArtPixelTest") {
                        MiniPlayer(
                            song = SONG,
                            isPlaying = false,
                            onPrevious = {},
                            onTogglePlayPause = {},
                            onNext = {},
                            onQueue = {},
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The middle quarter of the art disc: well inside the circle, away from its anti-aliased edge. */
    private fun artCentre(): List<Int> {
        val bitmap = composeRule.captureWindow()
        val art = composeRule.onNodeWithTag(MINI_PLAYER_ART_TAG, useUnmergedTree = true).getBoundsInRoot()
        val quarterW = (art.right - art.left) / 4
        val quarterH = (art.bottom - art.top) / 4
        val centre = DpRect(art.left + quarterW, art.top + quarterH, art.right - quarterW, art.bottom - quarterH)
        return composeRule.pixelsIn(bitmap, centre)
    }

    private companion object {
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
            artworkUri = "content://media/external/audio/albumart/1",
        )
    }
}
