package com.rolla.musicplayer.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchesCodecTest {

    private val threeQueries = listOf("rock", "metal", "jazz")

    @Test
    fun encodeRecentSearches_producesCommaSeparatedString() {
        assertEquals("rock,metal,jazz", encodeRecentSearches(threeQueries))
    }

    @Test
    fun encodeRecentSearches_emptyList_producesEmptyString() {
        assertEquals("", encodeRecentSearches(emptyList()))
    }

    @Test
    fun decodeRecentSearches_validCsv_returnsOriginalValues() {
        assertEquals(threeQueries, decodeRecentSearches("rock,metal,jazz"))
    }

    @Test
    fun decodeRecentSearches_nullValue_returnsEmptyList() {
        assertTrue(decodeRecentSearches(null).isEmpty())
    }

    @Test
    fun decodeRecentSearches_emptyValue_returnsEmptyList() {
        assertTrue(decodeRecentSearches("").isEmpty())
    }

    @Test
    fun roundTrip_queryContainingComma_preservesLiteralComma() {
        val queries = listOf("ac/dc, back in black", "rock")

        assertEquals(queries, decodeRecentSearches(encodeRecentSearches(queries)))
    }

    @Test
    fun roundTrip_queryContainingBackslash_preservesLiteralBackslash() {
        val queries = listOf("""c:\music""", "rock")

        assertEquals(queries, decodeRecentSearches(encodeRecentSearches(queries)))
    }

    @Test
    fun roundTrip_queryContainingBackslashAndComma_preservesBoth() {
        val queries = listOf("""a\b,c""", "d")

        assertEquals(queries, decodeRecentSearches(encodeRecentSearches(queries)))
    }

    @Test
    fun roundTrip_singleQuery_preservesValue() {
        assertEquals(listOf("rock"), decodeRecentSearches(encodeRecentSearches(listOf("rock"))))
    }

    @Test
    fun roundTrip_emptyList_preservesEmptyList() {
        assertTrue(decodeRecentSearches(encodeRecentSearches(emptyList())).isEmpty())
    }

    @Test
    fun encodeRecentSearches_escapesBackslashBeforeComma() {
        // Backslash must be escaped first, or the marker added for the literal comma would itself
        // get mangled by a naive backslash-escape pass applied afterward.
        assertEquals("""a\\,b""", encodeRecentSearches(listOf("""a\""", "b")))
    }

    // ── withRecordedSearch ──────────────────────────────────────────────────────

    @Test
    fun withRecordedSearch_emptyCurrent_insertsSingleEntry() {
        assertEquals(listOf("rock"), withRecordedSearch(emptyList(), "rock"))
    }

    @Test
    fun withRecordedSearch_newQuery_prependsMostRecentFirst() {
        val current = listOf("metal", "jazz")

        assertEquals(listOf("rock", "metal", "jazz"), withRecordedSearch(current, "rock"))
    }

    @Test
    fun withRecordedSearch_exactDuplicate_movesExistingEntryToFrontWithoutDuplicating() {
        val current = listOf("metal", "rock", "jazz")

        val updated = withRecordedSearch(current, "rock")

        assertEquals(listOf("rock", "metal", "jazz"), updated)
    }

    @Test
    fun withRecordedSearch_atCap_dropsOldestEntry() {
        val current = (1..MAX_RECENT_SEARCHES).map { "query$it" }

        val updated = withRecordedSearch(current, "newQuery")

        assertEquals(MAX_RECENT_SEARCHES, updated.size)
        assertEquals("newQuery", updated.first())
        assertTrue(updated.none { it == "query$MAX_RECENT_SEARCHES" })
    }

    @Test
    fun withRecordedSearch_belowCap_doesNotDropAnyEntries() {
        val current = (1 until MAX_RECENT_SEARCHES).map { "query$it" }

        val updated = withRecordedSearch(current, "newQuery")

        assertEquals(MAX_RECENT_SEARCHES, updated.size)
    }

    @Test
    fun withRecordedSearch_reRecordingOldestEntry_movesItToFrontInsteadOfDropping() {
        val current = (1..MAX_RECENT_SEARCHES).map { "query$it" }
        val oldest = "query$MAX_RECENT_SEARCHES"

        val updated = withRecordedSearch(current, oldest)

        assertEquals(MAX_RECENT_SEARCHES, updated.size)
        assertEquals(oldest, updated.first())
    }
}
