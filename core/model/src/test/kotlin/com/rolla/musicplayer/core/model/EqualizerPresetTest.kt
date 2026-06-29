package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetTest {

    private val eightBandGains = listOf<Short>(300, 200, 100, 0, -100, -200, 0, 100)
    private val flatGains = listOf<Short>(0, 0, 0, 0, 0, 0, 0, 0)
    private val expectedBandCount = eightBandGains.size

    @Test
    fun customFlagReturnsTrueForCustomPreset() {
        val preset = EqualizerPreset(id = 1L, name = "Rock", isCustom = true, gainsMillibel = eightBandGains)
        assertTrue(preset.isCustom)
    }

    @Test
    fun customFlagReturnsFalseForBuiltInPreset() {
        val preset = EqualizerPreset(id = 2L, name = "Flat", isCustom = false, gainsMillibel = flatGains)
        assertFalse(preset.isCustom)
    }

    @Test
    fun gainsMillibelPreservesAllBandValues() {
        val preset = EqualizerPreset(id = 1L, name = "Rock", isCustom = false, gainsMillibel = eightBandGains)
        assertEquals(expectedBandCount, preset.gainsMillibel.size)
        assertEquals(eightBandGains, preset.gainsMillibel)
    }

    @Test
    fun emptyGainListIsAccepted() {
        val preset = EqualizerPreset(id = 3L, name = "Empty", isCustom = false, gainsMillibel = emptyList())
        assertTrue(preset.gainsMillibel.isEmpty())
    }

    @Test
    fun presetsWithSameFieldsAreEqual() {
        val a = EqualizerPreset(id = 1L, name = "Rock", isCustom = true, gainsMillibel = eightBandGains)
        val b = EqualizerPreset(id = 1L, name = "Rock", isCustom = true, gainsMillibel = eightBandGains)
        assertEquals(a, b)
    }

    @Test
    fun copyChangingIsCustomProducesDistinctPreset() {
        val builtin = EqualizerPreset(id = 1L, name = "Rock", isCustom = false, gainsMillibel = eightBandGains)
        val custom = builtin.copy(isCustom = true)
        assertFalse(builtin.isCustom)
        assertTrue(custom.isCustom)
        assertEquals(builtin.gainsMillibel, custom.gainsMillibel)
    }
}
