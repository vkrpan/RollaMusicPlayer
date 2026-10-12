package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Semantic tokens must follow the theme RollaMusicPlayerTheme actually renders, not the system setting.
 * Robolectric's default configuration is light (not night) mode, so forcing dark here reproduces the
 * "Dark forced on a light-mode device" case that caused the v1.0 contrast bug.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeTokensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forcedDarkThemeResolvesDarkTokensOnLightSystem() {
        var accent: Color? = null
        var panel: Color? = null
        var pill: Color? = null
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                accent = MaterialTheme.colorScheme.accentText
                panel = MaterialTheme.colorScheme.surfaceContainer
                pill = MaterialTheme.colorScheme.miniPlayerContainer
            }
        }
        assertEquals(DarkPalette.accentText, accent)
        assertEquals(DarkPalette.surfaceContainer, panel)
        assertEquals(DarkPalette.miniPlayerContainer, pill)
    }

    @Test
    fun forcedLightThemeResolvesLightTokens() {
        var accent: Color? = null
        var placeholder: Color? = null
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = false) {
                accent = MaterialTheme.colorScheme.accentText
                placeholder = MaterialTheme.colorScheme.artworkPlaceholder
            }
        }
        assertEquals(LightPalette.accentText, accent)
        assertEquals(LightPalette.artworkPlaceholder, placeholder)
    }

    // The folder badge sits on artwork, not on a theme surface, so its colours must not follow the theme (Phase 4b
    // ruling 5). Mutation: DarkPalette `folderBadgeStroke = Color(0xFFEEEEEE)` → RED.
    @Test
    fun folderBadgeStrokeIsTheSameInBothThemes() {
        assertEquals(
            "folderBadgeStroke must not differ between themes",
            LightPalette.folderBadgeStroke,
            DarkPalette.folderBadgeStroke,
        )
    }

    // Mutation: DarkPalette `folderBadgeFill = Color(0xFF333333)` → RED.
    @Test
    fun folderBadgeFillIsTheSameInBothThemes() {
        assertEquals(
            "folderBadgeFill must not differ between themes",
            LightPalette.folderBadgeFill,
            DarkPalette.folderBadgeFill,
        )
    }
}
