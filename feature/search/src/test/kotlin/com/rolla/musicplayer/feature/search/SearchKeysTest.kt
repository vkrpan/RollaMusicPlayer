package com.rolla.musicplayer.feature.search

import com.rolla.musicplayer.core.model.Artist
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchKeysTest {

    @Test
    fun artistsThatShareAnIdStillGetDistinctKeys() {
        // The same id simulates an FNV hash collision; the names differ only in case.
        val upper = Artist(id = 7L, name = "Queen", albumCount = 1, songCount = 1)
        val lower = Artist(id = 7L, name = "queen", albumCount = 1, songCount = 1)

        assertNotEquals(searchArtistKey(upper), searchArtistKey(lower))
    }
}
