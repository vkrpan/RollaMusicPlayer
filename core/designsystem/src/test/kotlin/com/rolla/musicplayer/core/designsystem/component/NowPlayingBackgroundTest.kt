package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.NowPlayingGradient
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingGradient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Mirrors the production constant (private there): where the wash begins.
private const val WASH_START_FRACTION = 0.55f

// SDK 30: view.draw renders reliably there; see OneUiTabRowTest for the precedent.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingBackgroundTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Mutation: make the element a plain class (identity equality) → RED. Equality is what keeps drawWithCache from
    // rebuilding its brushes on every caller recomposition (Phase 1 Task 4 minor).
    @Test
    fun modifiersWithEqualGradientsAreEqual() {
        val g = NowPlayingGradient(Color.Black, Color.DarkGray, Color.Red, Color.Blue)
        assertEquals(Modifier.nowPlayingBackground(g), Modifier.nowPlayingBackground(g.copy()))
    }

    // Mutation: delete the wash draw → the bottom corners are `middle`, not washStart/washEnd → RED. Deleting only the
    // DstIn fade survives here (the fade is already opaque at the bottom edge); washFadesInFromItsTopEdge kills it.
    @Test
    fun drawsTopBaseAndBottomWash() {
        lateinit var g: NowPlayingGradient
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                g = MaterialTheme.colorScheme.nowPlayingGradient
                Box(Modifier.fillMaxSize().nowPlayingBackground(g))
            }
        }
        val bitmap = composeRule.drawRoot()
        assertColorNear(g.top, bitmap.getPixel(bitmap.width / 2, 0))
        assertColorNear(g.washStart, bitmap.getPixel(0, bitmap.height - 1))
        assertColorNear(g.washEnd, bitmap.getPixel(bitmap.width - 1, bitmap.height - 1))
    }

    // Mutation: delete the DstIn fade → the wash is opaque from its top edge, so just below it the pixel is washStart,
    // not middle → RED. (drawsTopBaseAndBottomWash cannot see this: at the bottom edge the fade is already opaque.)
    @Test
    fun washFadesInFromItsTopEdge() {
        lateinit var g: NowPlayingGradient
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                g = MaterialTheme.colorScheme.nowPlayingGradient
                Box(Modifier.fillMaxSize().nowPlayingBackground(g))
            }
        }
        val bitmap = composeRule.drawRoot()
        // The base has settled on middle here and the fade is ~0 % opaque, so only middle shows.
        val justBelowWashTop = (bitmap.height * WASH_START_FRACTION).toInt() + 1
        assertColorNear(g.middle, bitmap.getPixel(0, justBelowWashTop))
    }

    // Mutation: delete drawContent() from the node → the child never draws and the pixel is the background → RED.
    // The old onDrawBehind drew content implicitly; a DrawModifierNode must call it itself.
    @Test
    fun drawsContentOverTheBackground() {
        var child = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                child = MaterialTheme.colorScheme.primary
                Box(Modifier.fillMaxSize().nowPlayingBackground(MaterialTheme.colorScheme.nowPlayingGradient)) {
                    Box(Modifier.fillMaxSize().background(child))
                }
            }
        }
        val bitmap = composeRule.drawRoot()
        assertColorNear(child, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }
}
