package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.seekTrackActive
import com.rolla.musicplayer.core.designsystem.theme.seekTrackInactive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiSeekBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var seekTrackActive = Color.Unspecified
    private var seekTrackInactiveOverBackground = Color.Unspecified

    // Mutation: drop the contentDescription semantics → RED (baseline-profile contract).
    @Test
    fun exposesProgressAndContentDescription() {
        setBar(value = 0.25f)
        composeRule.onNodeWithContentDescription("Playback position")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0.25f, 0f..1f),
                ),
            )
    }

    // Mutation: call onValueChangeFinished from onValueChange too → finished > 1 → RED.
    @Test
    fun dragReportsValuesAndCommitsOnceOnRelease() {
        val values = mutableListOf<Float>()
        var finished = 0
        setBar(value = 0f, onValueChange = { values += it }, onFinished = { finished++ })
        composeRule.onNodeWithContentDescription("Playback position").performTouchInput {
            down(centerLeft)
            moveTo(center)
            moveTo(centerRight.copy(x = width * 0.75f))
            up()
        }
        assertTrue(values.isNotEmpty())
        assertEquals(1, finished)
    }

    // Mutation: swap activeColor/inactiveColor in the track → the left pixel is the inactive color → RED.
    @Config(sdk = [30]) // view.draw renders the track reliably here; see OneUiTabRowTest for the precedent
    @Test
    fun activeTrackLeftOfThumbIsSeekTrackActive() {
        setBar(value = 0.5f)
        val bitmap = composeRule.drawRoot()
        val bar = composeRule.onNodeWithContentDescription("Playback position").getBoundsInRoot()
        val y = with(composeRule.density) { ((bar.top + bar.bottom) / 2).roundToPx() }
        val leftX = with(composeRule.density) { (bar.left + (bar.right - bar.left) * 0.15f).roundToPx() }
        val rightX = with(composeRule.density) { (bar.left + (bar.right - bar.left) * 0.85f).roundToPx() }
        assertColorNear(seekTrackActive, bitmap.getPixel(leftX, y))
        assertColorNear(seekTrackInactiveOverBackground, bitmap.getPixel(rightX, y))
    }

    private fun setBar(value: Float, onValueChange: (Float) -> Unit = {}, onFinished: (() -> Unit)? = null) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val colors = MaterialTheme.colorScheme
                seekTrackActive = colors.seekTrackActive
                seekTrackInactiveOverBackground = colors.seekTrackInactive.compositeOver(colors.background)
                Box(Modifier.fillMaxWidth().background(colors.background)) {
                    OneUiSeekBar(
                        value = value,
                        onValueChange = onValueChange,
                        onValueChangeFinished = onFinished,
                        contentDescription = "Playback position",
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
