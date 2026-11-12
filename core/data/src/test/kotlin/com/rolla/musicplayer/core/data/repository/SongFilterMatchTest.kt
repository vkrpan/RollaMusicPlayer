package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [matches], Find tracks' pure matcher (custom tags phase 4): trimmed, case-blind "contains" text fields, an exact
 * year, every filter tag carried within its own kind, and every active field together (AND).
 */
class SongFilterMatchTest {

    // Review Focus 1. Trivial stub `true` fails the "Lovesong" vs "Hate" assertion; `false` fails the first.
    // Mutation: drop trim() → " LOVE " is not in "Lovesong" → RED.
    @Test
    fun matches_trimsAndIgnoresCase() {
        val lovesong = song(title = "Lovesong")

        val love = SongFilter(title = " LOVE ")

        assertTrue("\" LOVE \" finds Lovesong", love.matches(lovesong, null, NONE, NONE))
        assertFalse("\" LOVE \" does not find Hate", love.matches(song(title = "Hate"), null, NONE, NONE))
        assertTrue("an empty title matches everything", SongFilter(title = "").matches(lovesong, null, NONE, NONE))
        assertTrue("a blank title matches everything", SongFilter(title = "  ").matches(lovesong, null, NONE, NONE))
    }

    // Review Focus 2. Mutation: match the raw name only → raw "" does not contain "unknown" → RED.
    @Test
    fun matches_artistUsesDisplayName() {
        assertTrue(
            "\"unknown\" finds a blank raw artist shown as Unknown",
            SongFilter(artist = "unknown").matches(song(artist = ""), null, NONE, NONE),
        )
        assertTrue(
            "\"artist x\" finds raw \"Artist X\"",
            SongFilter(artist = "artist x").matches(song(artist = "Artist X"), null, NONE, NONE),
        )
        assertFalse(
            "\"unknown\" does not find a named artist",
            SongFilter(artist = "unknown").matches(song(artist = "Artist X"), null, NONE, NONE),
        )
    }

    // Mutation: compare toString().contains → 19990 contains 1999 → RED.
    @Test
    fun matches_yearExact() {
        val y1999 = SongFilter(year = 1999)

        assertTrue("1999 finds 1999", y1999.matches(song(year = 1999), null, NONE, NONE))
        assertFalse("1999 does not find 19990", y1999.matches(song(year = 19990), null, NONE, NONE))
        assertFalse("1999 does not find 2000", y1999.matches(song(year = 2000), null, NONE, NONE))
        assertTrue("no year finds no year", SongFilter(year = null).matches(song(year = null), null, NONE, NONE))
    }

    // Mutation: `genre == null || genre.contains(f, true)` → null genre, filter "rock" → RED.
    @Test
    fun matches_genreNullNeverMatchesActiveGenre() {
        assertTrue("\"rock\" finds Hard Rock", SongFilter(genre = "rock").matches(song(), "Hard Rock", NONE, NONE))
        assertFalse("\"rock\" does not find a null genre", SongFilter(genre = "rock").matches(song(), null, NONE, NONE))
        assertTrue("no genre filter finds a null genre", SongFilter().matches(song(), null, NONE, NONE))
    }

    // Mutation: any instead of all → {2} vs {2,3} → RED.
    @Test
    fun matches_allTagsRequired() {
        val filter = SongFilter(tagIds = setOf(2L, 3L))

        assertFalse("filter {2,3} vs song {2}", filter.matches(song(), null, setOf(2L), NONE))
        assertTrue("filter {2,3} vs song {2,3,9}", filter.matches(song(), null, setOf(2L, 3L, 9L), NONE))
    }

    // Mutation: check the tag filter against tag ∪ subtag IDs → subtag 2 satisfies tag 2 → RED.
    @Test
    fun matches_kindsAreSeparate() {
        assertFalse(
            "filter TAG 2 vs a song with only SUBTAG 2",
            SongFilter(tagIds = setOf(2L)).matches(song(), null, NONE, setOf(2L)),
        )
        assertTrue(
            "filter SUBTAG 2 vs a song with SUBTAG 2",
            SongFilter(subTagIds = setOf(2L)).matches(song(), null, NONE, setOf(2L)),
        )
    }

    // Mutation: || between fields → title ok → RED.
    @Test
    fun matches_everyFieldAnd() {
        val lovesong1999 = song(title = "Lovesong", year = 1999)

        assertTrue(
            "title and year both right",
            SongFilter(title = "love", year = 1999).matches(lovesong1999, null, NONE, NONE),
        )
        assertFalse(
            "title right, year wrong",
            SongFilter(title = "love", year = 2000).matches(lovesong1999, null, NONE, NONE),
        )
    }

    private fun song(
        title: String = "Title",
        artist: String = "Artist",
        album: String = "Album",
        year: Int? = 2020,
    ) = Song(
        id = "1",
        title = title,
        artist = artist,
        album = album,
        albumId = 10L,
        durationMs = 200_000L,
        trackNumber = 1,
        year = year,
        contentUri = "content://media/external/audio/media/1",
        artworkUri = "rolla-artwork://song/1?v=1000",
    )

    private companion object {
        val NONE: Set<Long> = emptySet()
    }
}
