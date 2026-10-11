package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The production shell (AppShell) and the production Now Playing destination (nowPlayingDestination), with stand-in
// screen bodies so no Hilt graph is needed. The plain Application keeps Robolectric from booting RollaApp.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** What composed, in order: "pill:<target>" from the pill, "np:<target>" from Now Playing. */
    private val events = mutableListOf<String>()

    // The device defect (back from Now Playing: grey circle at the pill, no artwork flight). The route flips to Home
    // in one frame and NavHost starts Now Playing's exit a frame later. A pill shown on the route alone enters in
    // between, while Now Playing still targets Visible, so the artwork shared element has no exiting side.
    // Mutation A: AppShell's showPill drops `&& !nowPlayingPresence.isTarget` -> "pill:Visible" is recorded before
    // "np:PostExit" -> RED. Mutation B: nowPlayingDestination stops reporting (no DisposableEffect) -> isTarget stays
    // false -> same order -> RED.
    @Test
    fun onBackThePillEntersOnlyOnceNowPlayingTargetsExit() {
        val navController = setShell()
        composeRule.runOnUiThread { navController.navigate(NowPlaying) }
        composeRule.waitForIdle()
        assertTrue("Now Playing composed", events.contains("np:${EnterExitState.Visible}"))

        events.clear()
        popWithManualClock(navController)

        assertPillEnteredAfterNowPlayingTargetedExit()
    }

    // Back, then the pill tapped while the old Now Playing is still exiting: openNowPlaying pushes a new entry, so two
    // Now Playing instances are composed together (old PostExit, new Visible). When the old one leaves, it must not
    // clear the new one's presence, or the next Back shows the pill on the route alone again.
    // Mutation D: NowPlayingPresence back to a single Boolean (set on report, cleared on any dispose) -> the old
    // instance's dispose clears it while the new one is the target -> "pill:Visible" before "np:PostExit" -> RED.
    @Test
    fun reopeningNowPlayingMidExitStillHoldsThePillOnTheNextBack() {
        val navController = setShell()
        composeRule.runOnUiThread { navController.navigate(NowPlaying) }
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { navController.popBackStack() }
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("old Now Playing still exiting", 1, nowPlayingInstances())
        composeRule.runOnUiThread { navController.openNowPlaying() }
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("old and new Now Playing composed together", 2, nowPlayingInstances())
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("the old Now Playing has left", 1, nowPlayingInstances())

        events.clear()
        popWithManualClock(navController)

        assertPillEnteredAfterNowPlayingTargetedExit()
    }

    // The gate only holds the pill while Now Playing is the target; once Now Playing is gone it shows on Home again.
    // Mutation C: nowPlayingDestination reports only its first value and never clears it (effect keyed on Unit, empty
    // onDispose) -> Now Playing stays "present" after the pop -> no pill -> RED.
    @Test
    fun afterTheRoundTripThePillIsShownOnHome() {
        val navController = setShell()
        composeRule.runOnUiThread { navController.navigate(NowPlaying) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PILL_TAG).assertDoesNotExist()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PILL_TAG).assertExists()

        composeRule.runOnUiThread { navController.navigate(Search()) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PILL_TAG).assertExists()
    }

    /** Pops with a manual clock, stepping the pop's first frames one by one, then lets everything settle. */
    private fun popWithManualClock(navController: NavHostController) {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { navController.popBackStack() }
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private fun assertPillEnteredAfterNowPlayingTargetedExit() {
        val npExits = events.indexOf("np:${EnterExitState.PostExit}")
        val pillEnters = events.indexOf("pill:${EnterExitState.Visible}")
        assertTrue("Now Playing began its exit: $events", npExits >= 0)
        assertTrue("the pill came back: $events", pillEnters >= 0)
        assertTrue("the pill entered only after Now Playing targeted exit: $events", pillEnters > npExits)
    }

    private fun nowPlayingInstances(): Int = composeRule.onAllNodesWithTag(NOW_PLAYING_TAG).fetchSemanticsNodes().size

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun setShell(): NavHostController {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppShell(
                navController = navController,
                hasSong = true,
                pill = { _, animatedVisibilityScope, measure ->
                    val target = animatedVisibilityScope.transition.targetState
                    SideEffect { events += "pill:$target" }
                    Box(measure.fillMaxWidth().height(PILL_HEIGHT).testTag(PILL_TAG))
                },
            ) { _, nowPlayingPresence ->
                composable<Home> { Box(Modifier.fillMaxSize()) }
                composable<Search> { Box(Modifier.fillMaxSize()) }
                nowPlayingDestination(nowPlayingPresence) {
                    val target = transition.targetState
                    SideEffect { events += "np:$target" }
                    Box(Modifier.fillMaxSize().testTag(NOW_PLAYING_TAG))
                }
            }
        }
        composeRule.waitForIdle()
        return navController
    }

    private companion object {
        const val PILL_TAG = "pill"
        const val NOW_PLAYING_TAG = "now_playing"
        val PILL_HEIGHT = 60.dp

        // Enough frames to cover the pop's first compositions; the assertions only look at the order of events.
        const val FRAMES = 6
    }
}
