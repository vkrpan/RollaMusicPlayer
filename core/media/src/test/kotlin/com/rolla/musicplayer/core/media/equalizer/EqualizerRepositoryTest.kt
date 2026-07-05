package com.rolla.musicplayer.core.media.equalizer

import com.rolla.musicplayer.core.testing.FakeEqualizerPreferences
import com.rolla.musicplayer.core.testing.FakeEqualizerPresetDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakePresetDao: FakeEqualizerPresetDao
    private lateinit var fakePreferences: FakeEqualizerPreferences
    private lateinit var repository: EqualizerRepository

    @Before
    fun setUp() {
        fakePresetDao = FakeEqualizerPresetDao()
        fakePreferences = FakeEqualizerPreferences()
        repository = EqualizerRepository(fakePresetDao, fakePreferences)
    }

    // ── observePresets ──────────────────────────────────────────────────────

    @Test
    fun `observePresets emits the six built-ins first in fixed order when no user presets exist`() = runTest {
        val presets = repository.observePresets().first()

        assertEquals(BUILT_IN_EQUALIZER_PRESETS, presets)
        assertEquals(
            listOf("Balanced", "Bass boost", "Smooth", "Dynamic", "Clear", "Treble boost"),
            presets.map { it.name },
        )
    }

    @Test
    fun `observePresets emits built-ins then user presets mapped from the dao in dao order`() = runTest {
        val firstId = repository.savePreset("My Mix", listOf(100, 100, 0, 0, 0, 0, 0, 0, 0))
        val secondId = repository.savePreset("Podcast", listOf(0, 0, 0, 200, 200, 200, 0, 0, 0))

        val presets = repository.observePresets().first()

        assertEquals(BUILT_IN_EQUALIZER_PRESETS.size + 2, presets.size)
        assertEquals(BUILT_IN_EQUALIZER_PRESETS, presets.take(BUILT_IN_EQUALIZER_PRESETS.size))
        val userPresets = presets.drop(BUILT_IN_EQUALIZER_PRESETS.size)
        assertEquals(listOf(firstId, secondId), userPresets.map { it.id })
        assertEquals(listOf("My Mix", "Podcast"), userPresets.map { it.name })
        assertTrue("User-saved presets must be marked isCustom", userPresets.all { it.isCustom })
    }

    // ── built-in preset invariants ────────────────────────────────────────────

    @Test
    fun `every built-in preset is not custom, has a unique negative id, and nine clamped gains`() {
        val seenIds = mutableSetOf<Long>()
        BUILT_IN_EQUALIZER_PRESETS.forEach { preset ->
            assertFalse("${preset.name} must not be marked isCustom", preset.isCustom)
            assertTrue("${preset.name} id must be negative (synthetic, never DB-assigned)", preset.id < 0)
            assertTrue("${preset.name} id must be unique", seenIds.add(preset.id))
            assertEquals(
                "${preset.name} must have exactly ${TARGET_FREQUENCIES_HZ.size} gains",
                TARGET_FREQUENCIES_HZ.size,
                preset.gainsMillibel.size,
            )
            preset.gainsMillibel.forEach { gain ->
                assertTrue(
                    "${preset.name} gain $gain must be within +/-1500 millibel",
                    gain in -1500..1500,
                )
            }
        }
        assertEquals(6, BUILT_IN_EQUALIZER_PRESETS.size)
    }

    // ── savePreset ────────────────────────────────────────────────────────────

    @Test
    fun `savePreset stores isCustom true and returns the dao-assigned id`() = runTest {
        val gains = listOf<Short>(300, 200, 100, 0, 0, 0, -100, -200, -300)

        val id = repository.savePreset("Custom Curve", gains)

        val saved = fakePresetDao.observeAllPresets().first().single()
        assertEquals(id, saved.id)
        assertEquals("Custom Curve", saved.name)
        assertTrue(saved.isCustom)
        assertEquals(gains, saved.gainsMillibel)
    }

    // ── deletePreset ──────────────────────────────────────────────────────────

    @Test
    fun `deletePreset with a built-in negative id is a no-op`() = runTest {
        val userId = repository.savePreset("Keep Me", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0))

        repository.deletePreset(BUILT_IN_EQUALIZER_PRESETS.first().id)

        val presets = repository.observePresets().first()
        assertEquals(BUILT_IN_EQUALIZER_PRESETS.size + 1, presets.size)
        assertTrue(presets.any { it.id == userId })
    }

    @Test
    fun `deletePreset with id zero is a no-op`() = runTest {
        val userId = repository.savePreset("Keep Me Too", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0))

        repository.deletePreset(0L)

        val presets = repository.observePresets().first()
        assertEquals(BUILT_IN_EQUALIZER_PRESETS.size + 1, presets.size)
        assertTrue(presets.any { it.id == userId })
    }

    @Test
    fun `deletePreset with a user id removes only that preset`() = runTest {
        val keepId = repository.savePreset("Keep", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0))
        val removeId = repository.savePreset("Remove", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0))

        repository.deletePreset(removeId)

        val userPresets = repository.observePresets().first().drop(BUILT_IN_EQUALIZER_PRESETS.size)
        assertEquals(listOf(keepId), userPresets.map { it.id })
    }

    // ── observeSettings ───────────────────────────────────────────────────────

    @Test
    fun `observeSettings defaults to disabled and nine zero gains when nothing is stored`() = runTest {
        val settings = repository.observeSettings().first()

        assertFalse(settings.enabled)
        assertEquals(List(TARGET_FREQUENCIES_HZ.size) { 0.toShort() }, settings.gainsMillibel)
    }

    @Test
    fun `observeSettings passes through a valid stored nine-gain list and enabled flag`() = runTest {
        val storedGains = listOf<Short>(100, 200, 300, 0, 0, 0, -100, -200, -300)
        fakePreferences.setEnabled(true)
        fakePreferences.setGainsMillibel(storedGains)

        val settings = repository.observeSettings().first()

        assertTrue(settings.enabled)
        assertEquals(storedGains, settings.gainsMillibel)
    }

    @Test
    fun `observeSettings normalizes a wrong-size stored gains list to nine zero gains`() = runTest {
        fakePreferences.setEnabled(true)
        fakePreferences.setGainsMillibel(listOf(100, 200, 300))

        val settings = repository.observeSettings().first()

        assertTrue("enabled flag is independent of gains normalization", settings.enabled)
        assertEquals(List(TARGET_FREQUENCIES_HZ.size) { 0.toShort() }, settings.gainsMillibel)
    }

    // ── currentSettings ───────────────────────────────────────────────────────

    @Test
    fun `currentSettings returns a one-shot snapshot matching observeSettings`() = runTest {
        val storedGains = listOf<Short>(50, 50, 50, 50, 50, 50, 50, 50, 50)
        fakePreferences.setEnabled(true)
        fakePreferences.setGainsMillibel(storedGains)

        val snapshot = repository.currentSettings()

        assertEquals(EqualizerSettings(enabled = true, gainsMillibel = storedGains), snapshot)
    }

    // ── saveSettings ──────────────────────────────────────────────────────────

    @Test
    fun `saveSettings round-trips enabled and gains through preferences`() = runTest {
        val settings = EqualizerSettings(
            enabled = true,
            gainsMillibel = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90),
        )

        repository.saveSettings(settings)

        assertEquals(settings, repository.currentSettings())
        assertTrue(fakePreferences.enabled.first())
        assertEquals(settings.gainsMillibel, fakePreferences.gainsMillibel.first())
    }
}
