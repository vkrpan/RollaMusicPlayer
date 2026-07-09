package com.rolla.musicplayer

import app.cash.turbine.test
import com.rolla.musicplayer.core.model.ThemeMode
import com.rolla.musicplayer.core.testing.FakeSettingsRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [MainViewModel].
 *
 * [MainViewModel] is the app-root bridge between [com.rolla.musicplayer.core.data.repository.SettingsRepository]
 * and `RollaMusicPlayerTheme` -- it exposes [MainViewModel.themeMode] and
 * [MainViewModel.useDynamicColor] as hot [kotlinx.coroutines.flow.StateFlow]s reflecting the
 * repository and nothing else. Offline: [FakeSettingsRepository] is fully in-memory, no network
 * calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = FakeSettingsRepository()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel(settingsRepository)
    }

    // themeMode ---------------------------------------------------------------------

    @Test
    fun themeMode_beforeAnyWrite_defaultsToSystem() = runTest {
        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeMode_whenRepositoryEmitsDark_thatModeIsReflected() = runTest {
        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem()) // initial
            settingsRepository.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeMode_reflectsMultipleConsecutiveRepositoryEmissions() = runTest {
        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            settingsRepository.setThemeMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
            settingsRepository.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            settingsRepository.setThemeMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // useDynamicColor -----------------------------------------------------------------

    @Test
    fun useDynamicColor_beforeAnyWrite_defaultsToFalse() = runTest {
        viewModel.useDynamicColor.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun useDynamicColor_whenRepositoryEmitsTrue_isReflected() = runTest {
        viewModel.useDynamicColor.test {
            assertFalse(awaitItem()) // initial
            settingsRepository.setUseDynamicColor(true)
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun useDynamicColor_togglesBetweenTrueAndFalse() = runTest {
        viewModel.useDynamicColor.test {
            assertFalse(awaitItem())
            settingsRepository.setUseDynamicColor(true)
            assertTrue(awaitItem())
            settingsRepository.setUseDynamicColor(false)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
