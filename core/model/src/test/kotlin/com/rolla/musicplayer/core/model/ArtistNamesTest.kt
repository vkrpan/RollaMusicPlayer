package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNamesTest {

    // Trivial stubs: identity → "<unknown>" stays "<unknown>" → RED on the first assertion; a constant "Unknown" → RED
    // on the "Sade" control.
    @Test
    fun mediaStoresUnknownAndBlankNamesShowAsUnknown() {
        assertEquals("MediaStore's <unknown> shows as Unknown", "Unknown", artistDisplayName("<unknown>"))
        assertEquals("an empty name shows as Unknown", "Unknown", artistDisplayName(""))
        assertEquals("a blank name shows as Unknown", "Unknown", artistDisplayName("  "))
        assertEquals("control: a real artist keeps its name", "Sade", artistDisplayName("Sade"))
    }

    // Mutation (the brief's): match with contains("unknown") instead of an exact equality → "<unknown> band" becomes
    // "Unknown" → RED. The brief's "<Unknown> band" control catches the ignoreCase variant; a case-sensitive
    // contains("unknown") does not match its capital U, so the lower-case control is what the brief's mutation hits.
    // Trivial stubs: identity → RED on the "<unknown>" control; a constant "Unknown" → RED on "Sade".
    @Test
    fun onlyAnExactMarkerMaps() {
        assertEquals("control: MediaStore's <unknown> shows as Unknown", "Unknown", artistDisplayName("<unknown>"))
        assertEquals("a real artist keeps its name", "Sade", artistDisplayName("Sade"))
        assertEquals(
            "a name that only contains <Unknown> is not MediaStore's marker and keeps its name",
            "<Unknown> band",
            artistDisplayName("<Unknown> band"),
        )
        assertEquals(
            "a name that only contains <unknown> is not MediaStore's marker and keeps its name",
            "<unknown> band",
            artistDisplayName("<unknown> band"),
        )
    }
}
