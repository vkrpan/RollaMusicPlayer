package com.rolla.musicplayer.feature.equalizer

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.media.equalizer.DeviceEqualizer
import com.rolla.musicplayer.core.media.equalizer.EqualizerController
import com.rolla.musicplayer.core.media.equalizer.EqualizerRepository
import com.rolla.musicplayer.core.media.equalizer.TARGET_FREQUENCIES_HZ
import com.rolla.musicplayer.core.testing.FakeEqualizerPreferences
import com.rolla.musicplayer.core.testing.FakeEqualizerPresetDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val SESSION_ID = 7

/**
 * Test double for [DeviceEqualizer]. Maps each of the project's nine target frequencies onto its
 * own distinct band (unlike a real device, which usually shares bands across several target
 * frequencies -- see EqualizerBands.kt) so tests can assert per-band gains independently.
 */
private class FakeDeviceEqualizer(
    override val numberOfBands: Short,
    range: ShortArray,
) : DeviceEqualizer {

    override val bandLevelRange: ShortArray = range
    override var enabled: Boolean = false

    private val levelsByBand = mutableMapOf<Short, Short>()

    override fun getBand(frequencyMilliHz: Int): Short {
        val frequencyHz = frequencyMilliHz / MILLIHZ_PER_HZ
        return TARGET_FREQUENCIES_HZ.indexOf(frequencyHz).toShort()
    }

    override fun getBandLevel(band: Short): Short = levelsByBand[band] ?: 0

    override fun setBandLevel(band: Short, levelMillibel: Short) {
        levelsByBand[band] = levelMillibel
    }

    override fun release() = Unit

    private companion object {
        const val MILLIHZ_PER_HZ = 1000
    }
}

/**
 * Unit tests for [EqualizerViewModel].
 *
 * [controller] is constructed as a real [EqualizerController] wired to a [FakeDeviceEqualizer] via
 * its factory, per the class's own rule that it must never call attach/release itself -- tests
 * that need an attached session call [EqualizerController.attach] directly, simulating what the
 * playback service would otherwise do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeDeviceEqualizer = FakeDeviceEqualizer(numberOfBands = 9, range = shortArrayOf(-1500, 1500))
    private val controller = EqualizerController(DeviceEqualizer.Factory { _, _ -> fakeDeviceEqualizer })

    private val presetDao = FakeEqualizerPresetDao()
    private val preferences = FakeEqualizerPreferences()
    private val repository = EqualizerRepository(presetDao, preferences)

    // Every EqualizerViewModel this test class constructs, so tearDown() can cancel each one's
    // viewModelScope. EqualizerRepository's writes hop onto the real Dispatchers.IO (see the
    // class KDoc), which is not tied to runTest's virtual scheduler -- a persist job can still be
    // completing that real dispatcher hop after a test method returns. Left unchecked, that
    // in-flight coroutine can later try to resume on Dispatchers.Main right as MainDispatcherRule
    // tears it down for the next test, corrupting an unrelated, later test. Explicitly cancelling
    // every ViewModel's scope here -- mirroring production's onCleared() -- closes that window.
    private val createdViewModels = mutableListOf<EqualizerViewModel>()

    private fun newViewModel(
        controller: EqualizerController = this.controller,
        repository: EqualizerRepository = this.repository,
    ): EqualizerViewModel = EqualizerViewModel(controller, repository).also { createdViewModels += it }

    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel ->
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    // ── initial hydration ──────────────────────────────────────────────────

    @Test
    fun uiState_hydratesFromPersistedSettingsOnInit() = runTest {
        preferences.setEnabled(true)
        preferences.setGainsMillibel(listOf(100, 200, 300, 0, 0, 0, 0, 0, 0))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.enabled)
        assertEquals(listOf<Short>(100, 200, 300, 0, 0, 0, 0, 0, 0), viewModel.uiState.value.gainsMillibel.values)
    }

    @Test
    fun uiState_defaultAllZeroGains_selectsBalancedPresetNotCustom() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val balanced = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Balanced" }
        assertEquals(List(TARGET_FREQUENCIES_HZ.size) { 0.toShort() }, viewModel.uiState.value.gainsMillibel.values)
        assertEquals(balanced.id, viewModel.uiState.value.selectedPresetId)
    }

    @Test
    fun uiState_controllerDetached_fallsBackToDefaultGainRange() = runTest {
        // controller is never attached in this test.
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals((-1500).toShort(), viewModel.uiState.value.minGainMillibel)
        assertEquals(1500.toShort(), viewModel.uiState.value.maxGainMillibel)
    }

    @Test
    fun uiState_controllerAttached_reflectsDeviceCapabilities() = runTest {
        val customDevice = FakeDeviceEqualizer(numberOfBands = 5, range = shortArrayOf(-2000, 2000))
        val customController = EqualizerController(DeviceEqualizer.Factory { _, _ -> customDevice })
        customController.attach(SESSION_ID)

        val viewModel = newViewModel(controller = customController)
        advanceUntilIdle()

        assertEquals((-2000).toShort(), viewModel.uiState.value.minGainMillibel)
        assertEquals(2000.toShort(), viewModel.uiState.value.maxGainMillibel)
    }

    // ── setEnabled ────────────────────────────────────────────────────────

    @Test
    fun setEnabled_appliesToControllerImmediatelyAndPersists() = runTest {
        controller.attach(SESSION_ID)
        val viewModel = newViewModel()
        advanceUntilIdle()

        // EqualizerRepository's writes hop onto the real Dispatchers.IO (not the test scheduler),
        // so persistence is awaited via Turbine's real cross-thread suspension on the settings
        // flow rather than a `currentSettings()` snapshot immediately after advanceUntilIdle() --
        // the latter races with the actual write completing.
        repository.observeSettings().test {
            assertEquals(false, awaitItem().enabled)

            viewModel.setEnabled(true)

            assertEquals(true, fakeDeviceEqualizer.enabled)
            assertEquals(true, viewModel.uiState.value.enabled)

            assertEquals(true, awaitItem().enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── setBandGain ───────────────────────────────────────────────────────

    @Test
    fun setBandGain_updatesControllerAndStateImmediately_persistsLastValueAfterDebounce() = runTest {
        controller.attach(SESSION_ID)
        val viewModel = newViewModel()
        advanceUntilIdle()

        repository.observeSettings().test {
            assertEquals(0.toShort(), awaitItem().gainsMillibel[0])

            viewModel.setBandGain(0, 100)
            assertEquals(100.toShort(), viewModel.uiState.value.gainsMillibel.values[0])
            assertEquals(100.toShort(), fakeDeviceEqualizer.getBandLevel(0))

            viewModel.setBandGain(0, 300)
            assertEquals(300.toShort(), viewModel.uiState.value.gainsMillibel.values[0])
            assertEquals(300.toShort(), fakeDeviceEqualizer.getBandLevel(0))

            // Not yet persisted -- still inside the debounce window, and the second call
            // cancelled the first call's pending persist before it ever ran.
            expectNoEvents()

            advanceUntilIdle()

            // Only the last value in the rapid-fire burst is ever written through.
            assertEquals(300.toShort(), awaitItem().gainsMillibel[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setBandGain_indexOutOfBounds_isNoOp() = runTest {
        controller.attach(SESSION_ID)
        val viewModel = newViewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value

        viewModel.setBandGain(-1, 500)
        viewModel.setBandGain(TARGET_FREQUENCIES_HZ.size, 500)
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value)
        assertTrue(TARGET_FREQUENCIES_HZ.indices.all { fakeDeviceEqualizer.getBandLevel(it.toShort()) == 0.toShort() })
    }

    @Test
    fun setBandGain_nonMatchingGains_selectedPresetIdIsNullMeaningCustom() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setBandGain(0, 777)

        assertNull(viewModel.uiState.value.selectedPresetId)
    }

    // ── selectPreset ──────────────────────────────────────────────────────

    @Test
    fun selectPreset_appliesAllGainsToControllerAndUpdatesSelection() = runTest {
        controller.attach(SESSION_ID)
        val viewModel = newViewModel()
        advanceUntilIdle()

        val bassBoost = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Bass boost" }

        repository.observeSettings().test {
            awaitItem() // initial persisted state

            viewModel.selectPreset(bassBoost)

            assertEquals(bassBoost.gainsMillibel, viewModel.uiState.value.gainsMillibel.values)
            assertEquals(bassBoost.id, viewModel.uiState.value.selectedPresetId)
            bassBoost.gainsMillibel.forEachIndexed { index, gain ->
                assertEquals(gain, fakeDeviceEqualizer.getBandLevel(index.toShort()))
            }

            assertEquals(bassBoost.gainsMillibel, awaitItem().gainsMillibel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectPreset_leavesEnabledUnchanged() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.setEnabled(true)
        advanceUntilIdle()

        val smooth = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Smooth" }
        viewModel.selectPreset(smooth)

        assertEquals(true, viewModel.uiState.value.enabled)
    }

    // ── saveCurrentAsPreset ───────────────────────────────────────────────

    @Test
    fun saveCurrentAsPreset_delegatesToRepositoryWithCurrentGains() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.setBandGain(2, 500)
        advanceUntilIdle()
        val expectedGains = viewModel.uiState.value.gainsMillibel.values

        repository.observePresets().test {
            awaitItem() // presets before saving

            viewModel.saveCurrentAsPreset("My preset")

            val saved = awaitItem().firstOrNull { it.name == "My preset" }
            assertTrue("A new preset named 'My preset' must have been saved", saved != null)
            assertEquals(expectedGains, saved!!.gainsMillibel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveCurrentAsPreset_trimsLeadingAndTrailingWhitespaceFromName() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        repository.observePresets().test {
            awaitItem() // presets before saving

            viewModel.saveCurrentAsPreset("  Padded Name  ")

            val saved = awaitItem().firstOrNull { it.name == "Padded Name" }
            assertTrue("Preset name must be trimmed before being persisted", saved != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveCurrentAsPreset_givenBlankName_isNoOp() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.saveCurrentAsPreset("   ")
        advanceUntilIdle()

        assertEquals(BUILT_IN_EQUALIZER_PRESETS.size, repository.observePresets().first().size)
    }

    // ── deletePreset ──────────────────────────────────────────────────────

    @Test
    fun deletePreset_givenUserPreset_removesItFromRepository() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val savedId = repository.savePreset("Temp", List(TARGET_FREQUENCIES_HZ.size) { 0 })
        advanceUntilIdle()

        repository.observePresets().test {
            assertTrue(awaitItem().any { it.id == savedId })

            viewModel.deletePreset(savedId)

            assertTrue(awaitItem().none { it.id == savedId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletePreset_givenBuiltInPreset_isNoOp() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val balancedId = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Balanced" }.id

        viewModel.deletePreset(balancedId)
        advanceUntilIdle()

        assertTrue(repository.observePresets().first().any { it.id == balancedId })
    }
}
