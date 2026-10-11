package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The chip's click, selection, disabled and footprint tests live in SwitchAndChipTest; these cover the long press.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PillChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutation: drop onLongClick from the combinedClickable → longClicks stays 0 → RED.
    @Test
    fun longPressCallsOnLongClick() {
        var longClicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PillChip(label = "Mine", selected = false, onClick = {}, onLongClick = { longClicks++ })
            }
        }
        composeRule.onNodeWithText("Mine").performTouchInput { longClick() }
        assertEquals(1, longClicks)
    }

    // Mutation: drop the onLongClickLabel pass-through to combinedClickable → the action's label is null → RED.
    @Test
    fun longClickActionCarriesItsLabel() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PillChip(
                    label = "Mine",
                    selected = false,
                    onClick = {},
                    onLongClick = {},
                    onLongClickLabel = "Delete preset",
                )
            }
        }
        composeRule.onNodeWithText("Mine").assert(
            SemanticsMatcher("long-click label is \"Delete preset\"") {
                it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Delete preset"
            },
        )
    }

    // Mutation: always pass onLongClick = {} to combinedClickable → an OnLongClick action appears → RED.
    @Test
    fun noLongClickActionWithoutOnLongClick() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { PillChip(label = "Balanced", selected = true, onClick = {}) }
        }
        composeRule.onNodeWithText("Balanced").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }
}
