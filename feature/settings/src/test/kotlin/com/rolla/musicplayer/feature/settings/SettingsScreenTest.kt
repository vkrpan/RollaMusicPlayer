package com.rolla.musicplayer.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The production [SettingsScreen] (copy, wiring, conditional rows, headings) and [ThemeModeDialog]'s button colour. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutations: label the row "Equalizer" → RED; title the bar "Settings" → RED; drop the row's onClick → RED.
    @Test
    fun screenSaysRollaMusicSettingsAndEqualiser() {
        var equaliserClicks = 0
        setScreen(SettingsUiState(equalizerEnabled = true), onEqualizerClick = { equaliserClicks++ })
        composeRule.onAllNodesWithText("Rolla Music settings").assertCountEquals(1)
        composeRule.onNodeWithText("Equaliser").performScrollTo().performClick()
        assertEquals(1, equaliserClicks)
    }

    // Mutation: render the Dynamic colour row regardless of isDynamicColorAvailable → present while unavailable → RED.
    // (Dropping the row outright fails the second half.)
    // Mutations (British copy, user decision): revert the label to "Dynamic color" → RED; revert the sub-label to
    // "Use colors from your wallpaper" → RED.
    @Test
    fun dynamicColorRowFollowsAvailability() {
        var state by mutableStateOf(SettingsUiState(isDynamicColorAvailable = false))
        setScreen { state }
        composeRule.onNodeWithText("Dynamic colour").assertDoesNotExist()
        state = SettingsUiState(isDynamicColorAvailable = true)
        composeRule.onNodeWithText("Dynamic colour").assertExists()
        composeRule.onNodeWithText("Use colours from your wallpaper").assertExists()
    }

    // Mutations: drop showProgress from the Rescan row → no indeterminate spinner → RED; drop enabled = !isRescanning
    // → the row stays enabled → RED.
    @Test
    fun rescanRowShowsItsSpinnerAndIsDisabledWhileRescanning() {
        setScreen(SettingsUiState(isRescanning = true))
        composeRule.onNodeWithText("Rescan library").performScrollTo().assertIsNotEnabled()
        // The spinner's progressSemantics() merges into its own node, so it is a descendant of the row, not part of it.
        composeRule.onAllNodes(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and hasAnyAncestor(hasText("Rescan library")),
        ).assertCountEquals(1)
    }

    // Mutation: the Theme row's onClick = {} → no dialog → RED.
    @Test
    fun tappingTheThemeRowOpensTheThemeDialog() {
        setScreen(SettingsUiState(themeMode = ThemeMode.DARK))
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    // Mutation: drop heading() from SettingsSectionHeader → no section header is a heading → RED.
    @Test
    fun sectionHeadersAreHeadings() {
        setScreen(SettingsUiState())
        listOf("Appearance", "Playback", "Library", "Privacy", "About").forEach { header ->
            composeRule.onAllNodes(isHeading() and hasText(header)).assertCountEquals(1)
        }
    }

    // Mutation: drop the TextButton's textButtonColors(contentColor = accentText) → "Cancel" is primary → RED.
    // Dark accentText (#6094FF) and primary (#2F6FF0) differ, which the first assertion pins.
    @Test
    fun themeDialogCancelIsAccentText() {
        var accent = Color.Unspecified
        var primary = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                accent = MaterialTheme.colorScheme.accentText
                primary = MaterialTheme.colorScheme.primary
                ThemeModeDialog(selected = ThemeMode.SYSTEM, onSelect = {}, onDismiss = {})
            }
        }
        assertNotEquals(primary, accent)
        assertEquals(accent, composeRule.textColorOf("Cancel"))
    }

    private fun setScreen(uiState: SettingsUiState, onEqualizerClick: () -> Unit = {}) =
        setScreen(onEqualizerClick = onEqualizerClick) { uiState }

    private fun setScreen(onEqualizerClick: () -> Unit = {}, uiState: () -> SettingsUiState) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SettingsScreen(
                    uiState = uiState(),
                    onNavigateUp = {},
                    onThemeModeSelected = {},
                    onUseDynamicColorChanged = {},
                    onPlaybackSpeedChanged = {},
                    onSkipSilenceChanged = {},
                    onEqualizerClick = onEqualizerClick,
                    onRescanClick = {},
                    onDismissRescanMessage = {},
                    onPrivacyClick = {},
                    onAboutClick = {},
                    onLicensesClick = {},
                )
            }
        }
    }
}
