package com.rolla.musicplayer.home

import android.app.Application
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Review Focus 1: process death / rotation on Home restores the selected tab. The plain Application keeps Robolectric
// from booting RollaApp's real Hilt graph.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var pagerState: PagerState

    @Test
    fun selectedTabSurvivesRecreation() {
        val tester = StateRestorationTester(composeRule)
        tester.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                pagerState = rememberHomePagerState()
                HomeScreen(
                    pagerState = pagerState,
                    selection = rememberSongSelectionState(),
                    onSearchClick = {},
                    onSettingsClick = {},
                    onCreatePlaylistClick = {},
                    onEditTagsForSelection = {},
                ) { tab -> Text("page-${tab.name}") }
            }
        }

        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        assertEquals(HomeTab.PLAYLISTS.ordinal, pagerState.currentPage)

        tester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals("The restored pager must stay on Playlists", HomeTab.PLAYLISTS.ordinal, pagerState.currentPage)
        }
    }
}
