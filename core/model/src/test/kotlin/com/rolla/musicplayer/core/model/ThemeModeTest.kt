package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun declarationOrderIsSystemThenLightThenDark() {
        assertEquals(
            listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
            ThemeMode.entries.toList(),
        )
    }

    @Test
    fun valueOfSystemReturnsSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.valueOf("SYSTEM"))
    }

    @Test
    fun valueOfLightReturnsLight() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
    }

    @Test
    fun valueOfDarkReturnsDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
    }
}
