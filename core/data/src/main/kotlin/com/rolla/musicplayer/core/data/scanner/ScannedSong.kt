package com.rolla.musicplayer.core.data.scanner

import com.rolla.musicplayer.core.database.entity.SongEntity

internal data class ScannedSong(
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val contentUri: String,
    val artworkUri: String,
    val dateModified: Long,
)

internal fun ScannedSong.toEntity(): SongEntity =
    SongEntity(
        id = mediaStoreId.toString(),
        mediaStoreId = mediaStoreId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        contentUri = contentUri,
        artworkUri = artworkUri,
        dateModified = dateModified,
    )
