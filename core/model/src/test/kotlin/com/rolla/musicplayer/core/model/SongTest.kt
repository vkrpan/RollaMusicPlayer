package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongTest {

    private val sample = Song(
        id = "song-1",
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 42L,
        durationMs = 354_000L,
        trackNumber = 11,
        year = 1975,
        contentUri = "content://media/external/audio/media/1",
        artworkUri = "content://media/external/audio/albumart/42",
    )

    @Test
    fun songsWithIdenticalFieldsAreEqual() {
        assertEquals(sample, sample.copy())
    }

    @Test
    fun songsWithDifferentIdsAreNotEqual() {
        assertNotEquals(sample, sample.copy(id = "song-2"))
    }

    @Test
    fun songsWithDifferentTitlesAreNotEqual() {
        assertNotEquals(sample, sample.copy(title = "Radio Ga Ga"))
    }

    @Test
    fun copyPreservesAllUnchangedFields() {
        val renamed = sample.copy(title = "Radio Ga Ga")
        assertEquals(sample.id, renamed.id)
        assertEquals(sample.artist, renamed.artist)
        assertEquals(sample.album, renamed.album)
        assertEquals(sample.albumId, renamed.albumId)
        assertEquals(sample.durationMs, renamed.durationMs)
        assertEquals(sample.trackNumber, renamed.trackNumber)
        assertEquals(sample.year, renamed.year)
        assertEquals(sample.contentUri, renamed.contentUri)
        assertEquals(sample.artworkUri, renamed.artworkUri)
    }

    @Test
    fun trackNumberCanBeNull() {
        val song = sample.copy(trackNumber = null)
        assertNull(song.trackNumber)
    }

    @Test
    fun yearCanBeNull() {
        val song = sample.copy(year = null)
        assertNull(song.year)
    }

    @Test
    fun idIsStringType() {
        val song = sample.copy(id = "content://42")
        assertEquals("content://42", song.id)
    }
}
