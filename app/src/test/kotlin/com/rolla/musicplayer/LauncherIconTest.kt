package com.rolla.musicplayer

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 7 ruling 4: the launcher identity. The label is "Rolla Music", and the icon is an adaptive icon (a white note
 * on the accent blue) with a monochrome layer for themed icons on API 33+.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class LauncherIconTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    // Trivial stub: no android:icon / the system default icon → R.mipmap.ic_launcher unresolved, or not adaptive →
    // RED. Mutation: drop <monochrome> from mipmap-anydpi-v26/ic_launcher.xml → monochrome null → RED.
    @Test
    fun launcherIconIsAnAdaptiveIconWithAMonochromeLayer() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue("the launcher icon is an adaptive icon on API 26+, got $icon", icon is AdaptiveIconDrawable)
        val adaptive = icon as AdaptiveIconDrawable
        assertNotNull("the adaptive icon has a background layer", adaptive.background)
        assertNotNull("the adaptive icon has a foreground layer", adaptive.foreground)
        assertNotNull("the adaptive icon has a monochrome layer for themed icons", adaptive.monochrome)
        assertEquals(
            "the icon background is the accent blue #2F6FF0",
            ACCENT_BLUE,
            context.getColor(R.color.ic_launcher_background),
        )
        assertEquals(
            "the manifest's application icon is the launcher icon",
            R.mipmap.ic_launcher,
            context.applicationInfo.icon,
        )
    }

    // Trivial stub: the old hardcoded android:label="RollaMusicPlayer" → RED. Mutation: app_name = "RollaMusicPlayer"
    // → RED.
    @Test
    fun applicationLabelIsRollaMusic() {
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        assertEquals("the launcher label is Rolla Music", "Rolla Music", label)
    }

    private companion object {
        const val ACCENT_BLUE = 0xFF2F6FF0.toInt()
    }
}
