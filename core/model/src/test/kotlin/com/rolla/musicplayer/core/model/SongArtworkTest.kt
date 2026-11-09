package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SongArtworkTest {

    // Trivial stub (songArtworkUri returns "", parse returns null) → RED. Mutation: parse swaps the id and the
    // version → SongArtworkRef(1794133952, 9545) → RED.
    @Test
    fun roundTrip() {
        assertEquals(
            "a per-track value parses back to its song id and version",
            SongArtworkRef(9545, 1794133952),
            parseSongArtworkUri(songArtworkUri("9545", 1794133952)),
        )
    }

    // Trivial stub ("" for every song) → RED. Mutation: drop the song id from the value → both read
    // "rolla-artwork://song/?v=5" → RED.
    @Test
    fun value_isPerTrack() {
        assertNotEquals(
            "two tracks with the same version still get different values",
            songArtworkUri("9540", 5),
            songArtworkUri("9545", 5),
        )
    }

    // The control shows a real value parses (trivial stub → RED on it). Mutation: a path-segment parser (the first
    // numeric segment is the song id, a missing ?v= is version 0) → the album id parses as a song → RED.
    @Test
    fun rejectsMediaStoreAlbumArt() {
        assertNotNull("control: a per-track value parses", parseSongArtworkUri(songArtworkUri("9540", 1)))
        assertNull(
            "MediaStore's per-album cover is not a per-track value",
            parseSongArtworkUri("content://media/external/audio/albumart/1540603544537963427"),
        )
    }

    // Same control and mutation as above: the path-segment parser reads 9540 as the song id → RED.
    @Test
    fun rejectsMediaStorePerFileArt() {
        assertNotNull("control: a per-track value parses", parseSongArtworkUri(songArtworkUri("9540", 1)))
        assertNull(
            "MediaStore's per-file thumbnail is not a per-track value",
            parseSongArtworkUri("content://media/external/audio/media/9540/albumart"),
        )
    }

    // Control as above. Mutations: a missing ?v= means version 0 → "rolla-artwork://song/1" parses → RED; drop the
    // rolla-artwork:// prefix check → "9545?v=1" parses → RED (the MediaStore probes above lack ?v=, so they cannot
    // see that check).
    @Test
    fun rejectsBlankAndMalformed() {
        assertNotNull("control: a per-track value parses", parseSongArtworkUri(songArtworkUri("1", 1)))
        assertNull("a blank value is no artwork", parseSongArtworkUri(""))
        assertNull("a null value is no artwork", parseSongArtworkUri(null))
        assertNull("a non-numeric song id is rejected", parseSongArtworkUri("rolla-artwork://song/x?v=1"))
        assertNull("a value without a version is rejected", parseSongArtworkUri("rolla-artwork://song/1"))
        assertNull("a value without the rolla-artwork prefix is rejected", parseSongArtworkUri("9545?v=1"))
    }
}
