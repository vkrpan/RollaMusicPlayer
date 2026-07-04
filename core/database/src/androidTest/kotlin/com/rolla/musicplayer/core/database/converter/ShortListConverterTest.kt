package com.rolla.musicplayer.core.database.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit coverage for [ShortListConverter]'s encode/decode contract. Lives in `androidTest`
 * rather than a JVM `test` source set because `:core:database` has no `test` source set today —
 * see the migration/DAO tests in this same source set for the on-disk (Room-level) corrupt-CSV
 * coverage of the same decode path.
 */
class ShortListConverterTest {

    private val converter = ShortListConverter()
    private val eightBandGains = listOf<Short>(300, 200, 100, 0, -100, -200, 0, 100)

    @Test
    fun fromShortList_encodesAsCommaSeparatedString() {
        assertEquals("300,200,100,0,-100,-200,0,100", converter.fromShortList(eightBandGains))
    }

    @Test
    fun fromShortList_emptyList_encodesAsEmptyString() {
        assertEquals("", converter.fromShortList(emptyList()))
    }

    @Test
    fun toShortList_decodesCommaSeparatedString() {
        assertEquals(eightBandGains, converter.toShortList("300,200,100,0,-100,-200,0,100"))
    }

    @Test
    fun toShortList_blankString_returnsEmptyList() {
        assertTrue(converter.toShortList("").isEmpty())
    }

    @Test
    fun toShortList_corruptToken_returnsEmptyList() {
        assertTrue(converter.toShortList("300,notANumber,100").isEmpty())
    }

    @Test
    fun toShortList_trailingCorruptToken_returnsEmptyList() {
        assertTrue(converter.toShortList("300,200,").isEmpty())
    }

    @Test
    fun roundTrip_negativeAndZeroGains_preservesValues() {
        val gains = listOf<Short>(-1200, 0, 1200, -1, 1)

        val roundTripped = converter.toShortList(converter.fromShortList(gains))

        assertEquals(gains, roundTripped)
    }

    @Test
    fun roundTrip_emptyList_preservesEmptyList() {
        val roundTripped = converter.toShortList(converter.fromShortList(emptyList()))

        assertTrue(roundTripped.isEmpty())
    }
}
