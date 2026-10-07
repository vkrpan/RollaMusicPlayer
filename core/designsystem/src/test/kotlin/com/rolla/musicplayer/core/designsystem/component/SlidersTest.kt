package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SlidersTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSliderReportsSetProgress() {
        var reported = -1f
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiSlider(
                    value = 1f,
                    onValueChange = { reported = it },
                    valueRange = 0.5f..2f,
                    steps = 5,
                    modifier = Modifier.width(300.dp).semantics { contentDescription = "Playback speed" },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Playback speed")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.3f) }
        assertEquals(1.25f, reported, 0.001f)
    }

    @Test
    fun eqSliderReportsSetProgressInRtl() {
        var reported = 0f
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    Box(Modifier.size(width = 29.dp, height = 237.dp)) {
                        EqVerticalSlider(
                            value = 0f,
                            onValueChange = { reported = it },
                            valueRange = -1500f..1500f,
                            contentDescription = "40 Hz gain",
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("40 Hz gain")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(600f) }
        assertEquals(600f, reported, 0.5f)
    }

    // SetProgress bypasses geometry, so it can't catch an RTL flip. A real bottom-to-top drag must raise the gain.
    @Test
    fun eqSliderDragsBottomToTopInRtl() {
        var reported = 0f
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    Box(Modifier.size(width = 29.dp, height = 237.dp)) {
                        EqVerticalSlider(
                            value = 0f,
                            onValueChange = { reported = it },
                            valueRange = -1500f..1500f,
                            contentDescription = "40 Hz gain",
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("40 Hz gain").performTouchInput { swipeUp() }
        assertTrue("A bottom-to-top drag should end near max, but reported $reported", reported > 1000f)
    }

    @Test
    fun disabledEqSliderIsNotEnabled() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.size(width = 29.dp, height = 237.dp)) {
                    EqVerticalSlider(
                        value = 0f,
                        onValueChange = {},
                        valueRange = -1500f..1500f,
                        contentDescription = "80 Hz gain",
                        enabled = false,
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("80 Hz gain").assertIsNotEnabled()
    }

    @Test
    fun disabledEqSliderIgnoresDrag() {
        var changes = 0
        setEqSlider(enabled = false, onValueChange = { changes++ })
        composeRule.onNodeWithContentDescription("40 Hz gain").performTouchInput { swipeUp() }
        assertEquals(0, changes)
    }

    @Test
    fun disabledSettingsSliderIsNotEnabled() {
        var changes = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiSlider(
                    value = 1f,
                    onValueChange = { changes++ },
                    valueRange = 0.5f..2f,
                    enabled = false,
                    modifier = Modifier.width(300.dp).semantics { contentDescription = "Playback speed" },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Playback speed")
            .assertIsNotEnabled()
            .performTouchInput { swipeRight() }
        assertEquals(0, changes)
    }

    @Test
    fun eqSliderForwardsDragInteractions() {
        val interactions = mutableListOf<Interaction>()
        setEqSlider(onValueChange = {}, onInteraction = { interactions += it })
        composeRule.onNodeWithContentDescription("40 Hz gain").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("Interactions seen: $interactions", interactions.any { it is DragInteraction.Start })
    }

    private fun setEqSlider(
        onValueChange: (Float) -> Unit,
        enabled: Boolean = true,
        onInteraction: (Interaction) -> Unit = {},
    ) {
        val interactionSource = MutableInteractionSource()
        composeRule.setContent {
            LaunchedEffect(interactionSource) { interactionSource.interactions.collect(onInteraction) }
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.size(width = 29.dp, height = 237.dp)) {
                    EqVerticalSlider(
                        value = 0f,
                        onValueChange = onValueChange,
                        valueRange = -1500f..1500f,
                        contentDescription = "40 Hz gain",
                        enabled = enabled,
                        interactionSource = interactionSource,
                    )
                }
            }
        }
    }
}
