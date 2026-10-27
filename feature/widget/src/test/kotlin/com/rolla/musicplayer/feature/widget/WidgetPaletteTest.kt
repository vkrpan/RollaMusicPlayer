package com.rolla.musicplayer.feature.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The widget palette (Phase 6c ruling 4): `res/values/colors.xml` (read by the drawables) and the Kotlin constants
 * (read by [RollaWidgetColors]) hold the same values, and the Glance providers resolve to that dark palette whatever
 * the system theme (day == night).
 */
@RunWith(AndroidJUnit4::class)
class WidgetPaletteTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Values pinned: each constant equals the ruling 4 table.
    // Mutation: widget_track = #333333 in colors.xml only → resource ≠ constant → RED.
    @Test
    fun resourcesMatchKotlinPalette() {
        assertPaletteEntry("widget_card", R.color.widget_card, WidgetCard, 0xFF000000)
        assertPaletteEntry("widget_title", R.color.widget_title, WidgetTitle, 0xFFFCFCFE)
        assertPaletteEntry("widget_artist", R.color.widget_artist, WidgetArtist, 0xFFB0B0B2)
        assertPaletteEntry("widget_accent", R.color.widget_accent, WidgetAccent, 0xFF2F6FF0)
        assertPaletteEntry("widget_track", R.color.widget_track, WidgetTrack, 0xFF646466)
        assertPaletteEntry(
            "widget_art_placeholder",
            R.color.widget_art_placeholder,
            WidgetArtPlaceholder,
            0xFF363636,
        )
        assertPaletteEntry("widget_error", R.color.widget_error, WidgetError, 0xFFFF5A5A)
    }

    // Mutation: the light side of RollaWidgetColors = lightColorScheme() → the notnight surface is near-white → RED.
    @Test
    @Config(qualifiers = "notnight")
    fun lightAndDarkResolveToTheSameDarkPalette() {
        assertDarkPalette("notnight")
    }

    // Mutation: the dark side of RollaWidgetColors = lightColorScheme() → the night surface is near-white → RED.
    // Mutation (both tests): revert ruling P4, outline = WidgetArtPlaceholder → outline #363636, not #646466 → RED.
    @Test
    @Config(qualifiers = "night")
    fun lightAndDarkResolveToTheSameDarkPaletteAtNight() {
        assertDarkPalette("night")
    }

    private fun assertPaletteEntry(name: String, resource: Int, constant: Color, expected: Long) {
        assertEquals("$name constant", expected.toInt(), constant.toArgb())
        assertEquals("$name resource equals its constant", constant.toArgb(), context.getColor(resource))
    }

    private fun assertDarkPalette(mode: String) {
        assertRole("$mode surface", 0xFF000000, RollaWidgetColors.surface.getColor(context))
        assertRole("$mode onSurface", 0xFFFCFCFE, RollaWidgetColors.onSurface.getColor(context))
        assertRole("$mode onSurfaceVariant", 0xFFB0B0B2, RollaWidgetColors.onSurfaceVariant.getColor(context))
        assertRole("$mode primary", 0xFF2F6FF0, RollaWidgetColors.primary.getColor(context))
        assertRole("$mode surfaceVariant", 0xFF646466, RollaWidgetColors.surfaceVariant.getColor(context))
        assertRole("$mode outline", 0xFF646466, RollaWidgetColors.outline.getColor(context))
    }

    private fun assertRole(role: String, expected: Long, actual: Color) {
        assertEquals(role, Integer.toHexString(expected.toInt()), Integer.toHexString(actual.toArgb()))
    }
}
