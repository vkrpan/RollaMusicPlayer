package com.rolla.musicplayer.core.data.scanner

import com.rolla.musicplayer.core.database.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScannedSong.toEntity]'s merge of the file pass's two columns: the read flag and the genre survive a re-scan only
 * while the file is unchanged and the caller does not ask for a re-read.
 */
class ScannedSongMergeTest {

    @Test
    fun toEntity_unchangedFile_keepsReadFlagAndGenre() {
        val existing = storedSong(dateModified = OLD_DATE_MODIFIED, fileTagsRead = true, genre = "Techno")

        val entity = scannedSong(dateModified = OLD_DATE_MODIFIED, genre = "Pop").toEntity(existing)

        assertTrue(entity.fileTagsRead)
        assertEquals("Techno", entity.genre)
    }

    @Test
    fun toEntity_changedFile_resetsReadFlagTakesScannedGenre() {
        val existing = storedSong(dateModified = OLD_DATE_MODIFIED, fileTagsRead = true, genre = "Techno")

        val entity = scannedSong(dateModified = NEW_DATE_MODIFIED, genre = "Pop").toEntity(existing)

        assertFalse(entity.fileTagsRead)
        assertEquals("Pop", entity.genre)
    }

    @Test
    fun toEntity_reread_resetsFlagEvenWhenUnchanged() {
        val existing = storedSong(dateModified = OLD_DATE_MODIFIED, fileTagsRead = true, genre = "Techno")

        val entity = scannedSong(dateModified = OLD_DATE_MODIFIED, genre = "Pop")
            .toEntity(existing, rereadFileTags = true)

        assertFalse(entity.fileTagsRead)
        assertEquals("Pop", entity.genre)
    }

    // ── fileTagsGeneration: the file pass applies a read only at the generation it queried ─────

    // Mutation: keep `existing.fileTagsGeneration` on a re-read → 4 → RED.
    @Test
    fun toEntity_reread_bumpsGeneration() {
        val existing = storedSong(OLD_DATE_MODIFIED, fileTagsRead = true, genre = null, fileTagsGeneration = 4L)

        val entity = scannedSong(dateModified = OLD_DATE_MODIFIED, genre = null)
            .toEntity(existing, rereadFileTags = true)

        assertEquals(5L, entity.fileTagsGeneration)
    }

    // Mutation: bump only on a re-read → 4 → RED.
    @Test
    fun toEntity_changedFile_bumpsGeneration() {
        val existing = storedSong(OLD_DATE_MODIFIED, fileTagsRead = true, genre = null, fileTagsGeneration = 4L)

        val entity = scannedSong(dateModified = NEW_DATE_MODIFIED, genre = null).toEntity(existing)

        assertEquals(5L, entity.fileTagsGeneration)
    }

    // Mutation: always bump → 5 → RED.
    @Test
    fun toEntity_unchanged_keepsGeneration() {
        val existing = storedSong(OLD_DATE_MODIFIED, fileTagsRead = true, genre = null, fileTagsGeneration = 4L)

        val entity = scannedSong(dateModified = OLD_DATE_MODIFIED, genre = null).toEntity(existing)

        assertEquals(4L, entity.fileTagsGeneration)
    }
}

private const val OLD_DATE_MODIFIED = 1_000L
private const val NEW_DATE_MODIFIED = 2_000L

private fun scannedSong(dateModified: Long, genre: String?): ScannedSong = ScannedSong(
    mediaStoreId = 1L,
    title = "Title",
    artist = "Artist",
    album = "Album",
    albumId = 10L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/1",
    artworkUri = "rolla-artwork://song/1?v=$dateModified",
    dateModified = dateModified,
    folderPath = "Music",
    genre = genre,
)

private fun storedSong(
    dateModified: Long,
    fileTagsRead: Boolean,
    genre: String?,
    fileTagsGeneration: Long = 0L,
): SongEntity = SongEntity(
    id = "1",
    mediaStoreId = 1L,
    title = "Title",
    artist = "Artist",
    album = "Album",
    albumId = 10L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/1",
    artworkUri = "rolla-artwork://song/1?v=$dateModified",
    dateModified = dateModified,
    dateAdded = 500L,
    folderPath = "Music",
    genre = genre,
    fileTagsRead = fileTagsRead,
    fileTagsGeneration = fileTagsGeneration,
)
