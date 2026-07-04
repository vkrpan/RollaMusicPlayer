package com.rolla.musicplayer.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPreferencesCodecTest {

    private val eightBandGains = listOf<Short>(300, 200, 100, 0, -100, -200, 0, 100)

    @Test
    fun encodeGainsMillibel_producesCommaSeparatedString() {
        assertEquals("300,200,100,0,-100,-200,0,100", encodeGainsMillibel(eightBandGains))
    }

    @Test
    fun encodeGainsMillibel_emptyList_producesEmptyString() {
        assertEquals("", encodeGainsMillibel(emptyList()))
    }

    @Test
    fun decodeGainsMillibel_validCsv_returnsOriginalValues() {
        assertEquals(eightBandGains, decodeGainsMillibel("300,200,100,0,-100,-200,0,100"))
    }

    @Test
    fun decodeGainsMillibel_nullValue_returnsEmptyList() {
        assertTrue(decodeGainsMillibel(null).isEmpty())
    }

    @Test
    fun decodeGainsMillibel_blankValue_returnsEmptyList() {
        assertTrue(decodeGainsMillibel("").isEmpty())
        assertTrue(decodeGainsMillibel("   ").isEmpty())
    }

    @Test
    fun decodeGainsMillibel_singleCorruptToken_treatsWholeValueAsCorrupt() {
        assertTrue(decodeGainsMillibel("300,notANumber,100").isEmpty())
    }

    @Test
    fun decodeGainsMillibel_trailingCorruptToken_treatsWholeValueAsCorrupt() {
        assertTrue(decodeGainsMillibel("300,200,").isEmpty())
    }

    @Test
    fun roundTrip_negativeAndZeroGains_preservesValues() {
        val gains = listOf<Short>(-1200, 0, 1200, -1, 1)

        assertEquals(gains, decodeGainsMillibel(encodeGainsMillibel(gains)))
    }

    @Test
    fun roundTrip_emptyList_preservesEmptyList() {
        assertTrue(decodeGainsMillibel(encodeGainsMillibel(emptyList())).isEmpty())
    }
}
