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

/**
 * Maps a freshly-scanned MediaStore row to the Room entity, merging in the user-state columns
 * (favourite / play count / last played / date added) from [existing].
 *
 * Room's `@Upsert` replaces the *entire* row, so any upsert that builds a [SongEntity] straight
 * from scan data alone silently wipes those columns back to their defaults for every row it
 * touches. This is the single merge point both [LibraryIndexer.sync] and
 * [LibraryIndexer.syncSongs] go through so that guarantee only needs to be correct once.
 *
 * [existing] is null only when MediaStore has never reported this row before — i.e. it is being
 * indexed for the first time — in which case it gets `dateAdded = System.currentTimeMillis()`,
 * which backs the "Recently added" smart playlist's ordering. When [existing] is non-null every
 * user-state column is carried forward unchanged.
 */
internal fun ScannedSong.toEntity(existing: SongEntity?): SongEntity =
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
        dateAdded = existing?.dateAdded ?: System.currentTimeMillis(),
        isFavorite = existing?.isFavorite ?: false,
        playCount = existing?.playCount ?: 0,
        lastPlayed = existing?.lastPlayed,
    )
