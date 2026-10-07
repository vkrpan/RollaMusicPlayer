package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwitchAndChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standaloneSwitchTogglesWithSwitchRole() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                var checked by remember { mutableStateOf(false) }
                OneUiSwitch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("sw"))
            }
        }
        composeRule.onNodeWithTag("sw")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithTag("sw").assertIsOn()
    }

    @Test
    fun disabledSwitchIgnoresClicks() {
        var changes = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiSwitch(
                    checked = false,
                    onCheckedChange = { changes++ },
                    enabled = false,
                    modifier = Modifier.testTag("sw"),
                )
            }
        }
        composeRule.onNodeWithTag("sw").assertIsNotEnabled().assertIsOff().performClick()
        assertEquals(0, changes)
    }

    // Footprint tests measure a wrap-content host. The control's own node reports only its drawn bounds: a merged
    // node takes its bounds from the toggleable/selectable, which sits inside minimumInteractiveComponentSize.
    @Test
    fun standaloneSwitchOccupiesA48dpTarget() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.testTag("host")) { OneUiSwitch(checked = false, onCheckedChange = {}) }
            }
        }
        composeRule.onNodeWithTag("host")
            .assertWidthIsEqualTo(RollaDimens.minTouchTarget)
            .assertHeightIsEqualTo(RollaDimens.minTouchTarget)
    }

    @Test
    fun rowOwnedSwitchIsVisualOnly() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.testTag("host")) {
                    OneUiSwitch(checked = true, onCheckedChange = null, modifier = Modifier.testTag("sw"))
                }
            }
        }
        composeRule.onNodeWithTag("host")
            .assertWidthIsEqualTo(RollaDimens.switchTrackWidth)
            .assertHeightIsEqualTo(RollaDimens.switchTrackHeight)
        composeRule.onNodeWithTag("sw")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assert(hasNoClickAction())
    }

    @Test
    fun chipReportsSelectionAndClicks() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column {
                    PillChip(label = "Balanced", selected = true, onClick = {})
                    PillChip(label = "Bass boost", selected = false, onClick = { clicks++ })
                }
            }
        }
        composeRule.onNodeWithText("Balanced")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        composeRule.onNodeWithText("Bass boost").assertIsNotSelected().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledChipIgnoresClicks() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column {
                    PillChip(label = "Smooth", selected = false, onClick = { clicks++ }, enabled = false)
                    PillChip(label = "Clear", selected = true, onClick = { clicks++ }, enabled = false)
                }
            }
        }
        composeRule.onNodeWithText("Smooth").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Clear").assertIsNotEnabled().assertIsSelected().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun chipOccupiesA48dpTarget() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.testTag("host")) { PillChip(label = "Balanced", selected = false, onClick = {}) }
            }
        }
        composeRule.onNodeWithTag("host").assertHeightIsEqualTo(RollaDimens.minTouchTarget)
    }
}
