package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.entity.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * `SongEntity.toDomain()` derives the per-track artwork value at read time and ignores the stored `artwork_uri`
 * column (spec §5.4, ruling R1). Lives in `:core:data` because `:core:database` has no JVM test source set (ruling P3).
 */
class SongEntityArtworkMappingTest {

    // Trivial stub (pass the stored column through, the old mapping) → the album-art uri → RED. Mutation: prefer the
    // stored column when it is not blank → RED.
    @Test
    fun toDomain_ignoresStoredAlbumArtUri() {
        val song = entity(id = "9545", dateModified = 7, artworkUri = "content://media/external/audio/albumart/1")
            .toDomain()
        assertEquals(
            "the value is derived from the song id and dateModified",
            "rolla-artwork://song/9545?v=7",
            song.artworkUri,
        )
    }

    // Trivial stub (the stored column: the same default in both) → equal → RED. Mutation: a fixed version 0 → RED.
    @Test
    fun toDomain_versionFollowsDateModified() {
        assertNotEquals(
            "a new dateModified gives a new value, so a changed picture is loaded afresh",
            entity(id = "1", dateModified = 7).toDomain().artworkUri,
            entity(id = "1", dateModified = 8).toDomain().artworkUri,
        )
    }

    private fun entity(
        id: String,
        dateModified: Long,
        artworkUri: String = "content://media/external/audio/albumart/1",
    ) = SongEntity(
        id = id,
        mediaStoreId = id.toLongOrNull() ?: 0L,
        title = "Title",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = artworkUri,
        dateModified = dateModified,
    )
}
