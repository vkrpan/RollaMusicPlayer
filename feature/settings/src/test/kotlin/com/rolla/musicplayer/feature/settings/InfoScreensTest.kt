package com.rolla.musicplayer.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** About and Privacy call the app "Rolla Music" in UI copy, never "RollaMusicPlayer" (Phase 6b ruling 7). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class InfoScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Trivial stub: the old copy → "Rolla Music" missing on About. Mutation: the old About string "RollaMusicPlayer" →
    // RED. Control: the version line shows. The flip to Privacy checks its statement in the same composition.
    @Test
    fun aboutAndPrivacyNameTheAppRollaMusic() {
        var showPrivacy by mutableStateOf(false)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                if (showPrivacy) {
                    PrivacyRoute(onNavigateUp = {})
                } else {
                    AboutRoute(buildInfo = AppBuildInfo(versionName = "1.0", versionCode = 1L), onNavigateUp = {})
                }
            }
        }
        // Each screen's positive assertion ("Rolla Music" on About, "Rolla Music is fully offline" on Privacy) is the
        // control of its "never says RollaMusicPlayer" absence check: the copy renders, under the new name.
        assertEquals("About names the app Rolla Music", 1, textCount("Rolla Music"))
        assertEquals("About shows its version line (control)", 1, textCount("Version 1.0 (1)"))
        assertEquals("About never says RollaMusicPlayer", 0, substringCount("RollaMusicPlayer"))

        composeRule.runOnIdle { showPrivacy = true }
        assertEquals("Privacy says Rolla Music is fully offline", 1, substringCount("Rolla Music is fully offline"))
        assertEquals("Privacy never says RollaMusicPlayer", 0, substringCount("RollaMusicPlayer"))
    }

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun substringCount(text: String): Int =
        composeRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size
}
