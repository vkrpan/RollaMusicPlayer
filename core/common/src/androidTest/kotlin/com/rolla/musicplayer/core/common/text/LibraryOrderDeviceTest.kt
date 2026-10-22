package com.rolla.musicplayer.core.common.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// The JVM suite runs the JDK collator. Android's java.text.Collator is ICU-backed, so the same rule is pinned here on
// the device (Phase 5 ruling 14). The order fixture and its expectation are the JVM test's, verbatim; the section
// inputs are a subset of the JVM test's.
@RunWith(AndroidJUnit4::class)
class LibraryOrderDeviceTest {

    // Mutation: in LibraryOrder.kt make `bySection = 0` in sortedByLibraryName → "Ωmega" sorts after the Latin names
    // → RED.
    @Test
    fun icuCollationGivesTheSameLibraryOrder() {
        val input = listOf("Zebra", "Eva", "Ωmega", "Apricot", "Émile", "1999", "Fox", "apple", "Emma")
        val expected = listOf("1999", "Ωmega", "apple", "Apricot", "Émile", "Emma", "Eva", "Fox", "Zebra")
        assertEquals(expected, input.sortedByLibraryName(naturalOrder()) { it })
        assertEquals(listOf("B", "b"), listOf("b", "B").sortedByLibraryName(naturalOrder()) { it })
    }

    // Mutation: in LibraryOrder.kt skip the NFD normalisation in indexLetter → 'É' and 'ñ' map to '#' → RED.
    @Test
    fun icuNormalizerGivesTheSameSections() {
        assertEquals(listOf('E', 'N', 'A', '#', '#'), listOf("Émile", "ñu", "Åsa", "Øl", "Ωmega").map(::indexLetter))
    }
}
