package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Geometry only (no text), but NATIVE + 360 dp match the rest of the :app layout tests. The plain Application keeps
// Robolectric from booting RollaApp's Hilt graph.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PushedScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentEndsTheMiniPlayerInsetAboveTheHostsBottomEdge() {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().testTag(HOST_TAG)) {
                CompositionLocalProvider(LocalMiniPlayerInset provides INSET) {
                    PushedScreen { Box(modifier = Modifier.fillMaxSize().testTag(CONTENT_TAG)) }
                }
            }
        }
        composeRule.waitForIdle()

        val host = composeRule.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertEquals("gap below the pushed screen's content", INSET.value, (host.bottom - content.bottom).value, 0.5f)
        // Only the bottom is padded: the top and the sides stay owned by the screen and the nav graph.
        assertEquals("content top", host.top.value, content.top.value, 0.5f)
        assertEquals("content width", (host.right - host.left).value, (content.right - content.left).value, 0.5f)
    }

    private companion object {
        const val HOST_TAG = "host"
        const val CONTENT_TAG = "content"
        val INSET = 72.dp
    }
}
