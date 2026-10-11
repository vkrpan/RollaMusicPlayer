package com.rolla.musicplayer.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Pixel checks of the Now Playing tint rules (constraints: never primary as a glyph tint on Now Playing). xxhdpi makes
// the 1.6-unit line strokes several pixels wide, so each glyph has fully covered pixels that carry its exact tint.
// A glyph's tint is "present" when some pixel in its button matches it; the gradient behind never comes near a token.
// Captured through PixelCopy with Robolectric's hardware renderer (see captureWindow): the artwork's sharedElement
// draws a RenderNode, which the kit tests' software view.draw cannot (it throws on SDK 34, 30 and 28 alike).
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingScreenPixelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var colors: NowPlayingTestColors

    @Before
    fun useHardwarePixelCopy() {
        System.setProperty(PIXEL_COPY_RENDER_MODE, "hardware")
    }

    @After
    fun restorePixelCopy() {
        System.clearProperty(PIXEL_COPY_RENDER_MODE)
    }

    // Dark scheme: accentText #6094FF, primary #2F6FF0, so the two are distinguishable (asserted below).
    // Mutation: activeTint = MaterialTheme.colorScheme.primary → RED.
    @Test
    fun activeShuffleAndRepeatGlyphsAreAccentText() {
        composeRule.setNowPlayingScreen(
            inputs = NowPlayingTestInputs(shuffle = ShuffleMode.ON, repeat = RepeatMode.ALL),
            onColors = { colors = it },
        )
        assertNotEquals(colors.primary, colors.accentText)
        val bitmap = composeRule.captureWindow()
        listOf("Shuffle", "Repeat").forEach { control ->
            val bounds = composeRule.onNodeWithContentDescription(control).getBoundsInRoot()
            val pixels = composeRule.pixelsIn(bitmap, bounds)
            assertTrue("$control glyph has no accentText pixel", pixels.any { isColorNear(colors.accentText, it) })
            assertFalse("$control glyph has a primary pixel", pixels.any { isColorNear(colors.primary, it) })
        }
    }

    // Spec §8.4: the filled heart alone marks a favourite; it stays onSurface.
    // Mutation: tint the heart primary when favourited → RED.
    @Test
    fun favouritedHeartIsOnSurface() {
        composeRule.setNowPlayingScreen(inputs = NowPlayingTestInputs(favorite = true), onColors = { colors = it })
        val bitmap = composeRule.captureWindow()
        val bounds = composeRule.onNodeWithContentDescription("Remove from favourites").getBoundsInRoot()
        val pixels = composeRule.pixelsIn(bitmap, bounds)
        assertTrue("heart has no onSurface pixel", pixels.any { isColorNear(colors.onSurface, it) })
        assertFalse("heart has a primary pixel", pixels.any { isColorNear(colors.primary, it) })
    }

    // Play is a bare glyph: inside the 64 dp circle but outside the 40 dp glyph (6 dp below the box top, centred), the
    // pixel is the background, i.e. the same as a pixel 2 dp above the box (the gap over the transport row).
    // Mutation: restore a primary circle background on the play box → RED.
    @Test
    fun playHasNoCircleFill() {
        composeRule.setNowPlayingScreen()
        val bitmap = composeRule.captureWindow()
        val play = composeRule.onNodeWithContentDescription("Play").getBoundsInRoot()
        with(composeRule.density) {
            val x = ((play.left + play.right) / 2).roundToPx()
            val inside = bitmap.getPixel(x, (play.top + 6.dp).roundToPx())
            val above = bitmap.getPixel(x, (play.top - 2.dp).roundToPx())
            assertTrue(
                "inside ${Integer.toHexString(inside)} differs from background ${Integer.toHexString(above)}",
                isColorNear(Color(above), inside),
            )
        }
    }

    private companion object {
        const val PIXEL_COPY_RENDER_MODE = "robolectric.pixelCopyRenderMode"
    }
}
