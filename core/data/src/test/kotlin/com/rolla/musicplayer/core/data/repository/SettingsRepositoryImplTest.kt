package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.datastore.SettingsPreferences
import com.rolla.musicplayer.core.model.ThemeMode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryImplTest {

    private val preferences: SettingsPreferences = mockk()
    private val repository = SettingsRepositoryImpl(preferences)

    // ── reads: plain delegation ─────────────────────────────────────────────────

    @Test
    fun themeMode_delegatesToPreferences() = runTest {
        every { preferences.themeMode } returns flowOf(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.observeThemeMode().first())
    }

    @Test
    fun useDynamicColor_delegatesToPreferences() = runTest {
        every { preferences.useDynamicColor } returns flowOf(true)

        assertEquals(true, repository.observeUseDynamicColor().first())
    }

    @Test
    fun skipSilence_delegatesToPreferences() = runTest {
        every { preferences.skipSilence } returns flowOf(true)

        assertEquals(true, repository.observeSkipSilence().first())
    }

    // ── playbackSpeed: read-side normalization ──────────────────────────────────

    @Test
    fun playbackSpeed_inRangeValue_passesThroughUnchanged() = runTest {
        every { preferences.playbackSpeed } returns flowOf(1.5f)

        assertEquals(1.5f, repository.observePlaybackSpeed().first())
    }

    @Test
    fun playbackSpeed_belowMinimum_fallsBackToDefault() = runTest {
        every { preferences.playbackSpeed } returns flowOf(0.1f)

        assertEquals(DEFAULT_PLAYBACK_SPEED, repository.observePlaybackSpeed().first())
    }

    @Test
    fun playbackSpeed_aboveMaximum_fallsBackToDefault() = runTest {
        every { preferences.playbackSpeed } returns flowOf(5.0f)

        assertEquals(DEFAULT_PLAYBACK_SPEED, repository.observePlaybackSpeed().first())
    }

    @Test
    fun playbackSpeed_atExactMinimumBound_passesThroughUnchanged() = runTest {
        every { preferences.playbackSpeed } returns flowOf(MIN_PLAYBACK_SPEED)

        assertEquals(MIN_PLAYBACK_SPEED, repository.observePlaybackSpeed().first())
    }

    @Test
    fun playbackSpeed_atExactMaximumBound_passesThroughUnchanged() = runTest {
        every { preferences.playbackSpeed } returns flowOf(MAX_PLAYBACK_SPEED)

        assertEquals(MAX_PLAYBACK_SPEED, repository.observePlaybackSpeed().first())
    }

    // ── writes: delegation ───────────────────────────────────────────────────────

    @Test
    fun setThemeMode_delegatesToPreferences() = runTest {
        coEvery { preferences.setThemeMode(any()) } just Runs

        repository.setThemeMode(ThemeMode.LIGHT)

        coVerify { preferences.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun setUseDynamicColor_delegatesToPreferences() = runTest {
        coEvery { preferences.setUseDynamicColor(any()) } just Runs

        repository.setUseDynamicColor(true)

        coVerify { preferences.setUseDynamicColor(true) }
    }

    @Test
    fun setSkipSilence_delegatesToPreferences() = runTest {
        coEvery { preferences.setSkipSilence(any()) } just Runs

        repository.setSkipSilence(true)

        coVerify { preferences.setSkipSilence(true) }
    }

    // ── setPlaybackSpeed: write-side clamping ───────────────────────────────────

    @Test
    fun setPlaybackSpeed_inRangeValue_persistsUnchanged() = runTest {
        coEvery { preferences.setPlaybackSpeed(any()) } just Runs

        repository.setPlaybackSpeed(1.25f)

        coVerify { preferences.setPlaybackSpeed(1.25f) }
    }

    @Test
    fun setPlaybackSpeed_belowMinimum_clampsToMinimumBeforePersisting() = runTest {
        coEvery { preferences.setPlaybackSpeed(any()) } just Runs

        repository.setPlaybackSpeed(0.1f)

        coVerify { preferences.setPlaybackSpeed(MIN_PLAYBACK_SPEED) }
    }

    @Test
    fun setPlaybackSpeed_aboveMaximum_clampsToMaximumBeforePersisting() = runTest {
        coEvery { preferences.setPlaybackSpeed(any()) } just Runs

        repository.setPlaybackSpeed(5.0f)

        coVerify { preferences.setPlaybackSpeed(MAX_PLAYBACK_SPEED) }
    }
}
