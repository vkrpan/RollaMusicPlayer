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
        val tester = setRestorableHome()

        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        assertEquals(HomeTab.PLAYLISTS.ordinal, pagerState.currentPage)

        tester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals("The restored pager must stay on Playlists", HomeTab.PLAYLISTS.ordinal, pagerState.currentPage)
        }
    }

    // A non-default tab, two pages right of Tracks: back from ArtistDetail (no popUpTo) re-enters Home on this tab.
    // Mutation (already pinned by the Playlists case above): drop rememberHomePagerState's rememberSaveable backing,
    // e.g. remember { PagerState(currentPage = HomeTab.TRACKS.ordinal) { HomeTab.entries.size } } → reopens on Tracks.
    @Test
    fun artistsTabSurvivesRecreation() {
        val tester = setRestorableHome()

        composeRule.onNodeWithText("Artists").performClick()
        composeRule.waitForIdle()
        assertEquals(HomeTab.ARTISTS.ordinal, pagerState.currentPage)

        tester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals("The restored pager must stay on Artists", HomeTab.ARTISTS.ordinal, pagerState.currentPage)
        }
    }

    private fun setRestorableHome(): StateRestorationTester {
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
        return tester
    }
}
