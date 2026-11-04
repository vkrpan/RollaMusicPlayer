package com.rolla.musicplayer

import android.app.Application
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Phase 7 ruling P14: [applyRollaEdgeToEdge] (MainActivity's resolved edge-to-edge setup) leaves a transparent
 * navigation bar with no system contrast scrim on API 29+ (the device comparison measured the scrim as a grey band the
 * reference lacks), and the light theme keeps dark navigation-bar icons. Robolectric runs at SDK 34. The plain
 * Application keeps Robolectric from booting RollaApp's Hilt graph; a bare ComponentActivity hosts the window.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SystemBarsTest {

    // Control: SystemBarStyle.auto alone (androidx's enableEdgeToEdge) turns the contrast scrim on, so the check can
    // fail. Mutation: drop `window.isNavigationBarContrastEnforced = false` from applyRollaEdgeToEdge → RED.
    @Test
    fun navigationBarIsTransparentWithNoContrastScrim() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.enableEdgeToEdge()
        assertTrue(
            "control: enableEdgeToEdge's auto styles enforce the contrast scrim",
            activity.window.isNavigationBarContrastEnforced,
        )

        activity.applyRollaEdgeToEdge(darkTheme = true)

        assertFalse("no navigation-bar contrast scrim", activity.window.isNavigationBarContrastEnforced)
        assertEquals("transparent navigation bar", Color.TRANSPARENT, activity.window.navigationBarColor)
    }

    // The light theme keeps dark navigation-bar icons, the dark theme light ones (one activity, both themes in turn).
    // Mutation: navigationBarStyle detects dark mode as `{ true }` → the light theme gets light icons → RED.
    @Test
    fun navigationBarIconsFollowTheResolvedTheme() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

        activity.applyRollaEdgeToEdge(darkTheme = true)
        assertFalse("dark theme: light navigation-bar icons", insets.isAppearanceLightNavigationBars)

        activity.applyRollaEdgeToEdge(darkTheme = false)
        assertTrue("light theme: dark navigation-bar icons", insets.isAppearanceLightNavigationBars)
    }
}
