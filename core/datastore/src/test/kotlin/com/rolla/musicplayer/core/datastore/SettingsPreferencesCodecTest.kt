package com.rolla.musicplayer.core.datastore

import com.rolla.musicplayer.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesCodecTest {

    // ── encodeThemeMode / decodeThemeMode ───────────────────────────────────────

    @Test
    fun encodeThemeMode_system_producesSystemToken() {
        assertEquals("SYSTEM", encodeThemeMode(ThemeMode.SYSTEM))
    }

    @Test
    fun encodeThemeMode_light_producesLightToken() {
        assertEquals("LIGHT", encodeThemeMode(ThemeMode.LIGHT))
    }

    @Test
    fun encodeThemeMode_dark_producesDarkToken() {
        assertEquals("DARK", encodeThemeMode(ThemeMode.DARK))
    }

    @Test
    fun decodeThemeMode_systemToken_returnsSystem() {
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode("SYSTEM"))
    }

    @Test
    fun decodeThemeMode_lightToken_returnsLight() {
        assertEquals(ThemeMode.LIGHT, decodeThemeMode("LIGHT"))
    }

    @Test
    fun decodeThemeMode_darkToken_returnsDark() {
        assertEquals(ThemeMode.DARK, decodeThemeMode("DARK"))
    }

    @Test
    fun decodeThemeMode_nullValue_returnsSystem() {
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode(null))
    }

    @Test
    fun decodeThemeMode_unknownToken_returnsSystem() {
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode("SEPIA"))
    }

    @Test
    fun decodeThemeMode_blankToken_returnsSystem() {
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode(""))
    }

    @Test
    fun decodeThemeMode_lowercaseToken_isCaseSensitiveAndFallsBackToSystem() {
        // Enum names are stored/matched verbatim (case-sensitive); a lowercase variant is treated
        // like any other unrecognized token rather than being normalized.
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode("dark"))
    }

    @Test
    fun roundTrip_everyThemeMode_preservesValue() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, decodeThemeMode(encodeThemeMode(mode)))
        }
    }

    // ── DEFAULT_PLAYBACK_SPEED ───────────────────────────────────────────────────

    @Test
    fun defaultPlaybackSpeed_isNormalSpeed() {
        assertEquals(1.0f, DEFAULT_PLAYBACK_SPEED)
    }
}
