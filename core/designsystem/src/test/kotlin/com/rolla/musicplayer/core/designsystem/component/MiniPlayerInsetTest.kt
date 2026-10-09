package com.rolla.musicplayer.core.designsystem.component

import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric reports no system insets, so the test dispatches its own navigation bar to the composition's view.
@RunWith(AndroidJUnit4::class)
class MiniPlayerInsetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeListBottomPaddingIsThePillInsetPlusTheNavigationBar() {
        var padding = UNREAD
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalMiniPlayerInset provides PILL_INSET) {
                padding = homeListBottomPadding()
            }
        }
        composeRule.waitForIdle()

        val navBarPx = with(composeRule.density) { NAV_BAR_HEIGHT.roundToPx() }
        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                    .build(),
            )
        }
        composeRule.waitForIdle()

        assertEquals("pill inset + navigation bar", EXPECTED.value, padding.value, TOL)
    }

    private companion object {
        val UNREAD = (-1).dp
        val PILL_INSET = 72.dp
        val NAV_BAR_HEIGHT = 48.dp
        val EXPECTED = 120.dp
        const val TOL = 0.5f
    }
}
