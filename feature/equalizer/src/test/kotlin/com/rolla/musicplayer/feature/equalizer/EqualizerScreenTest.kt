package com.rolla.musicplayer.feature.equalizer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The restyled Equaliser screen (spec §8.5), driven through the production [EqualizerScreen] with a state built from
 * the real built-in presets plus one user preset whose gains match none of them.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqualizerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        enabled: Boolean = true,
        onSetEnabled: (Boolean) -> Unit = {},
        onDelete: (Long) -> Unit = {},
    ) = composeRule.setEqualizerScreen(enabled = enabled, onSetEnabled = onSetEnabled, onDelete = onDelete)

    private fun bounds(text: String): DpRect = composeRule.onNodeWithText(text).getBoundsInRoot()

    // Mutation: keep the old "40Hz" … "10kHz" labels → RED (spec §8.5 drops the Hz suffix).
    @Test
    fun frequencyLabelsDropTheHzSuffix() {
        setScreen()
        listOf("40", "80", "160", "315", "630", "1.25k", "2.5k", "5k", "10k").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onAllNodesWithText("Hz", substring = true).assertCountEquals(0)
    }

    // Mutation: build the slider descriptions from the new visible labels → "1.25k gain" → RED (ruling 1: TalkBack
    // keeps saying the unit even though the visible label drops it).
    @Test
    fun sliderDescriptionsKeepTheUnit() {
        setScreen()
        composeRule.onNodeWithContentDescription("1.25kHz gain").assertExists()
        composeRule.onNodeWithContentDescription("40Hz gain").assertExists()
    }

    // Mutation: keep the old order (user presets before Custom) → "Mine" takes Custom's slot → RED.
    @Test
    fun customChipFollowsTheBuiltInsAndPrecedesUserPresets() {
        setScreen()
        // Rows 3 and 4 sit below the 640 dp viewport, where bounds in root clamp to its edge; bring them on screen.
        composeRule.onNodeWithText("Mine").performScrollTo()
        val trebleBoost = bounds("Treble boost")
        val custom = bounds("Custom")
        val mine = bounds("Mine")
        assertTrue(custom.top > trebleBoost.top) // row 4 vs row 3
        assertEquals(custom.top.value, mine.top.value, 1f) // same row
        assertTrue(custom.left < mine.left) // Custom in the first column
    }

    // Mutation: leave the chips (or, separately, the sliders) enabled when the EQ is off → RED (spec §8.5).
    @Test
    fun disabledEqualiserDisablesSlidersAndChips() {
        setScreen(enabled = false)
        composeRule.onNodeWithText("Balanced").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("40Hz gain").assertIsNotEnabled()
    }

    // Mutation: pass onCheckedChange to OneUiSwitch as well as the row → two toggleable nodes → RED. Also: the row's
    // onValueChange no longer calls onSetEnabled → enabledTo stays null → RED. Also: the row's label is not
    // "Equaliser" → RED.
    @Test
    fun enableRowIsOneToggleTarget() {
        var enabledTo: Boolean? = null
        setScreen(enabled = false, onSetEnabled = { enabledTo = it })
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNodeWithTag(EQ_ENABLE_ROW_TAG).assert(hasText("Equaliser"))
        composeRule.onNodeWithTag(EQ_ENABLE_ROW_TAG).performClick()
        assertEquals(true, enabledTo)
    }

    // Mutation: compose the curve unconditionally → it exists before any drag → RED (ruling 10). Also: drop the shared
    // interactionSource from the sliders → no drag is ever seen → the curve never appears → RED. Also: a hand-rolled
    // "dragging" flag set on the first gain change and never reset → the curve outlives the drag → RED.
    @Test
    fun responseCurveAppearsOnlyWhileDragging() {
        setScreen()
        composeRule.onNodeWithTag(EQ_CURVE_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("40Hz gain").performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
        }
        composeRule.onNodeWithTag(EQ_CURVE_TAG).assertExists()
        composeRule.onNodeWithContentDescription("40Hz gain").performTouchInput { up() }
        composeRule.waitForIdle() // lets the fade-out finish (the clock auto-advances)
        composeRule.onNodeWithTag(EQ_CURVE_TAG).assertDoesNotExist()
    }

    // Mutation: drop onLongClick from the user chip, or delete built-ins too → RED.
    @Test
    fun longPressDeletesOnlyUserPresets() {
        val deleted = mutableListOf<Long>()
        setScreen(onDelete = { deleted += it })
        composeRule.onNodeWithText("Mine").performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithText("Balanced").performScrollTo().performTouchInput { longClick() }
        assertEquals(listOf(100L), deleted)
    }

    // Mutation: drop onLongClickLabel from the user chip, or give the built-ins a long press too → RED (ruling 2).
    @Test
    fun onlyUserPresetsAnnounceTheDeleteAction() {
        setScreen()
        composeRule.onNodeWithText("Mine").assert(
            SemanticsMatcher("long-click label is \"Delete preset\"") {
                it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Delete preset"
            },
        )
        composeRule.onNodeWithText("Balanced").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    // Mutation: keep the "Equalizer" title → RED.
    @Test
    fun titleIsEqualiserHeading() {
        setScreen()
        composeRule.onNode(hasText("Equaliser") and isHeading()).assertExists()
    }
}
