package com.rolla.musicplayer.core.permissions

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The permission gate's stateless body (Phase 6b ruling 8): one prompt per status, on the One UI kit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PermissionContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Ruling 7 copy and the unchanged button labels, per status. Trivial stub: content rendered for every status → the
    // NEEDS_REQUEST message is missing. Mutation: swap the SHOW_RATIONALE and PERMANENTLY_DENIED branches → "Grant
    // access" is missing after the flip → RED.
    @Test
    fun eachStatusShowsItsMessageAndButton() {
        var status by mutableStateOf(MediaPermissionStatus.NEEDS_REQUEST)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PermissionContent(status = status, onRequest = {}, onOpenSettings = {}) {
                    Box(Modifier.testTag(CONTENT_TAG))
                }
            }
        }
        composeRule.onNodeWithText("Rolla Music needs access", substring = true, useUnmergedTree = true)
            .assertExists("NEEDS_REQUEST explains why Rolla Music needs access")
        composeRule.onNodeWithText("Allow access").assertExists("NEEDS_REQUEST offers Allow access")
        assertEquals("NEEDS_REQUEST hides the gated content", 0, tagCount(CONTENT_TAG))

        composeRule.runOnIdle { status = MediaPermissionStatus.SHOW_RATIONALE }
        composeRule.onNodeWithText("Grant access").assertExists("SHOW_RATIONALE offers Grant access")

        composeRule.runOnIdle { status = MediaPermissionStatus.PERMANENTLY_DENIED }
        composeRule.onNodeWithText("Open Settings").assertExists("PERMANENTLY_DENIED offers Open Settings")
        composeRule.onNodeWithText("so Rolla Music can load", substring = true, useUnmergedTree = true)
            .assertExists("PERMANENTLY_DENIED says Settings lets Rolla Music load the music")

        composeRule.runOnIdle { status = MediaPermissionStatus.GRANTED }
        composeRule.onNodeWithTag(CONTENT_TAG).assertExists("GRANTED renders the gated content")
        assertEquals("GRANTED shows no prompt button", 0, textCount("Open Settings"))
    }

    // Each button calls its own action. Trivial stub: buttons do nothing → onRequest stays 0. Mutation: both buttons
    // call onRequest → onOpenSettings stays 0 (and onRequest becomes 2) → RED.
    @Test
    fun buttonsCallTheirOwnAction() {
        var status by mutableStateOf(MediaPermissionStatus.NEEDS_REQUEST)
        var requests = 0
        var settingsOpens = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PermissionContent(
                    status = status,
                    onRequest = { requests++ },
                    onOpenSettings = { settingsOpens++ },
                ) {}
            }
        }
        composeRule.onNodeWithText("Allow access").performClick()
        composeRule.runOnIdle {
            assertEquals("Allow access requests the permission once", 1, requests)
            assertEquals("Allow access does not open Settings", 0, settingsOpens)
        }

        composeRule.runOnIdle { status = MediaPermissionStatus.PERMANENTLY_DENIED }
        composeRule.onNodeWithText("Open Settings").performClick()
        composeRule.runOnIdle {
            assertEquals("Open Settings opens the app's Settings once", 1, settingsOpens)
            assertEquals("Open Settings does not request the permission", 1, requests)
        }
    }

    // Ruling 8 anatomy: the prompt sits in a ContentPanel (surfaceContainer) on the background, with a filled pill
    // (primary). The message is centred below the placeholder, so 4 dp above its top lies in the gap between the two:
    // panel, never artwork or text. The pill probe sits inside the pill's round end, left of the label (which starts
    // chipHorizontalPadding in). Trivial stub: the prompt drawn straight on the background → the first probe reads
    // background. Mutation: drop the ContentPanel → RED on the first probe.
    @Test
    fun promptSitsInAPanelWithAFilledPill() {
        val scheme = setNeedsRequestPromptOnBackground()
        val bitmap = composeRule.drawRoot()
        val message = composeRule
            .onNodeWithText("Rolla Music needs access", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val abovePanel = bitmap.pixelAt((message.left + message.right) / 2, message.top - PANEL_PROBE_GAP)
        assertTrue(
            "4 dp above the message is the ContentPanel (surfaceContainer), was ${abovePanel.hex()}",
            isColorNear(scheme.surfaceContainer, abovePanel),
        )
        val button = composeRule.onNodeWithText("Allow access").getUnclippedBoundsInRoot()
        val besideLabel = bitmap.pixelAt(button.left + PILL_PROBE_INSET, (button.top + button.bottom) / 2)
        assertTrue(
            "beside the label the button is a filled (primary) pill, was ${besideLabel.hex()}",
            isColorNear(scheme.primary, besideLabel),
        )
    }

    // Final-wave I1: the prompt pads its bottom by homeListBottomPadding and scrolls, so its button can always be
    // scrolled clear of the pill. A short (360 dp) screen makes the prompt (placeholder, message, button) plus the
    // 120 dp pill taller than the panel; on the class's 640 dp screen the centred prompt would clear the pill even
    // unpadded, and the test could not tell. The prompt is scrolled to its end, as a user would. Control: the button
    // exists. Trivial stub: no bottom padding → the button's bottom lies under the pill → RED. Mutation: drop
    // .padding(bottom = homeListBottomPadding()) → RED.
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h360dp-xxhdpi")
    fun theButtonScrollsClearOfThePill() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides PILL_INSET) {
                    PermissionContent(
                        status = MediaPermissionStatus.NEEDS_REQUEST,
                        onRequest = {},
                        onOpenSettings = {},
                    ) {}
                }
            }
        }
        composeRule.onNodeWithText("Allow access").assertExists("control: the prompt shows its button")

        composeRule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, SCROLL_TO_END_PX)
        }
        composeRule.waitForIdle()

        val button = composeRule.onNodeWithText("Allow access").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "scrolled to its end, the button's bottom ${button.bottom} sits at least $PILL_INSET above the root's " +
                "${root.bottom}",
            button.bottom <= root.bottom - PILL_INSET + 1.dp,
        )
    }

    /** The NEEDS_REQUEST prompt full screen on the dark background, as Home places it; returns the dark scheme. */
    private fun setNeedsRequestPromptOnBackground(): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                scheme = MaterialTheme.colorScheme
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    PermissionContent(
                        status = MediaPermissionStatus.NEEDS_REQUEST,
                        onRequest = {},
                        onOpenSettings = {},
                    ) {}
                }
            }
        }
        composeRule.waitForIdle()
        return scheme
    }

    private fun tagCount(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun textCount(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun Bitmap.pixelAt(x: Dp, y: Dp): Int = with(composeRule.density) { getPixel(x.roundToPx(), y.roundToPx()) }

    private fun Int.hex(): String = Integer.toHexString(this)

    private companion object {
        const val CONTENT_TAG = "gated-content"

        /** How far above the message's top the panel probe sits (inside the placeholder-to-message gap). */
        val PANEL_PROBE_GAP = 4.dp

        /** The pill probe's inset from the button's start: inside the round end, before the label. */
        val PILL_PROBE_INSET = 6.dp

        /** The mini-player pill's footprint the prompt must clear. */
        val PILL_INSET = 120.dp

        /** Far more than the prompt can scroll, so ScrollBy lands on its end (the scroll clamps). */
        const val SCROLL_TO_END_PX = 100_000f
    }
}
