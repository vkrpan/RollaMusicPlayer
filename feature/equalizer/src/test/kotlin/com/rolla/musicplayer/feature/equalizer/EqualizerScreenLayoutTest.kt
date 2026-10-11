package com.rolla.musicplayer.feature.equalizer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.model.EqualizerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Equaliser screen's grid layout, its Custom-only save button (ruling 11), its callback wiring and the curve's
 * framing, driven through the production [EqualizerScreen].
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqualizerScreenLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutation: delete the odd-row Spacer → Custom, alone in row 4 of a 7-chip grid, takes the full row → RED.
    @Test
    fun oddLastRowKeepsTheChipInItsColumn() {
        composeRule.setEqualizerScreen(presets = BUILT_IN_EQUALIZER_PRESETS) // 6 built-ins + Custom = 7 chips
        val balanced = composeRule.onNodeWithText("Balanced").getBoundsInRoot()
        val custom = composeRule.onNodeWithText("Custom").performScrollTo().getBoundsInRoot()
        assertEquals((balanced.right - balanced.left).value, (custom.right - custom.left).value, 0.5f)
        assertEquals(balanced.left.value, custom.left.value, 0.5f)
    }

    // Mutation: drop Modifier.selectableGroup() from the preset grid → RED.
    @Test
    fun presetGridIsASelectableGroup() {
        composeRule.setEqualizerScreen()
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
                hasAnyDescendant(hasText("Balanced")),
        ).assertExists()
    }

    // Mutation: always show "Save as preset" → RED (ruling 11: only in the Custom state).
    @Test
    fun saveAsPresetIsHiddenWhileAPresetMatches() {
        composeRule.setEqualizerScreen(selectedPresetId = BassBoostPreset.id)
        composeRule.onNodeWithText("Save as preset").assertDoesNotExist()
    }

    // Mutation: never show "Save as preset" → RED.
    @Test
    fun saveAsPresetIsShownInTheCustomState() {
        composeRule.setEqualizerScreen(selectedPresetId = null)
        composeRule.onNodeWithText("Save as preset").assertExists()
    }

    // Mutation: report every band as index 0 (onBandGainChange(0, gain)) → RED; drop the call → nothing recorded → RED.
    @Test
    fun draggingABandReportsThatBandAndTheDraggedDirection() {
        val changes = mutableListOf<Pair<Int, Short>>()
        composeRule.setEqualizerScreen(onBandGainChange = { band, gain -> changes += band to gain })
        composeRule.onNodeWithContentDescription("1.25kHz gain").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        assertTrue("no gain change was reported", changes.isNotEmpty())
        assertEquals(setOf(5), changes.map { it.first }.toSet())
        // Bass boost puts 1.25 kHz at 0 mB; an upward drag raises it.
        assertTrue("last gain ${changes.last().second} is not above 0", changes.last().second > 0)
    }

    // Mutations: the chip's onClick = {} → nothing selected → RED; every chip emits presets.first() → Balanced → RED.
    // Taps "Clear", not "Balanced": Balanced is the first preset, so it can't tell those two apart.
    @Test
    fun tappingAPresetChipSelectsThatPreset() {
        val selected = mutableListOf<EqualizerPreset>()
        composeRule.setEqualizerScreen(onSelectPreset = { selected += it })
        composeRule.onNodeWithText("Clear").performScrollTo().performClick()
        assertEquals(listOf(BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Clear" }), selected)
    }

    // Mutation: frame the curve overlay on the whole band column (value label + slider + frequency label) instead of
    // the slider band → at font scale 2 its top/bottom sit on the labels' outer edges, not between them → RED.
    @Test
    fun responseCurveIsFramedOnTheSliderBandAtFontScale2() {
        composeRule.setEqualizerScreen(fontScale = 2f)
        composeRule.onNodeWithContentDescription("10kHz gain").performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
        }
        val curve = composeRule.onNodeWithTag(EQ_CURVE_TAG).getBoundsInRoot()
        val valueLabel = composeRule.onNodeWithText("+6").getBoundsInRoot() // band 0 (40 Hz) under Bass boost
        val frequencyLabel = composeRule.onNodeWithText("40").getBoundsInRoot()
        // The sliders fill exactly the strip between the two label rows, so its center is the sliders' 0 dB center.
        assertEquals(valueLabel.bottom.value, curve.top.value, 0.5f)
        assertEquals(frequencyLabel.top.value, curve.bottom.value, 0.5f)
        composeRule.onNodeWithContentDescription("10kHz gain").performTouchInput { up() }
    }
}
