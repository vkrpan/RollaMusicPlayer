package com.rolla.musicplayer.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's pager pages: spec §7.2 order and copy. Plain JVM. */
class HomeTabTest {

    // Mutation: list ALBUMS before TRACKS, or keep the two-tab enum → RED.
    @Test
    fun `tabs follow the spec order and titles`() {
        assertEquals(
            listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists"),
            HomeTab.entries.map { it.title },
        )
    }
}
