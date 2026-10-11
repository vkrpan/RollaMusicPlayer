package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeContentColorTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutation: delete the LocalContentColor provider in RollaMusicPlayerTheme → content is Color.Black → RED (dark).
    @Test
    fun contentColorDefaultsToOnBackgroundInDarkTheme() = assertContentColorIsOnBackground(dark = true)

    // Mutation: delete the LocalContentColor provider in RollaMusicPlayerTheme → content is Color.Black → RED (light:
    // onBackground is #111113 there, so the default black still differs).
    @Test
    fun contentColorDefaultsToOnBackgroundInLightTheme() = assertContentColorIsOnBackground(dark = false)

    // createComposeRule allows one setContent per test, so each theme is its own test.
    private fun assertContentColorIsOnBackground(dark: Boolean) {
        var content = Color.Unspecified
        var expected = Color.Magenta
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = dark, dynamicColor = false) {
                content = LocalContentColor.current
                expected = MaterialTheme.colorScheme.onBackground
            }
        }
        composeRule.waitForIdle()
        assertEquals("dark=$dark", expected, content)
    }
}
