package com.rolla.musicplayer.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's pager pages: spec §7.2 order and copy. Plain JVM. */
class HomeTabTest {

    // Mutation: list ALBUMS before TRACKS, or leave out FOLDERS → RED.
    @Test
    fun `tabs follow the spec order and titles`() {
        assertEquals(
            listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders"),
            HomeTab.entries.map { it.title },
        )
    }
}
