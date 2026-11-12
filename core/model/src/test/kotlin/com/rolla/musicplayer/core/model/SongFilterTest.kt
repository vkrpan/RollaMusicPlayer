package com.rolla.musicplayer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFilterTest {

    @Test
    fun isEmpty_trueOnlyWhenNoFieldIsActive() {
        assertTrue("the default filter is empty", SongFilter().isEmpty)
        assertTrue(
            "blank text fields are inactive",
            SongFilter(title = " ", artist = "  ", album = "\t", genre = " ").isEmpty,
        )

        val oneActiveField = mapOf(
            "title" to SongFilter(title = "love"),
            "artist" to SongFilter(artist = "queen"),
            "album" to SongFilter(album = "opera"),
            "genre" to SongFilter(genre = "rock"),
            "year" to SongFilter(year = 1999),
            "tagIds" to SongFilter(tagIds = setOf(2L)),
            "subTagIds" to SongFilter(subTagIds = setOf(1L)),
        )
        oneActiveField.forEach { (field, filter) ->
            assertFalse("a filter with only $field set is not empty", filter.isEmpty)
        }
    }
}
