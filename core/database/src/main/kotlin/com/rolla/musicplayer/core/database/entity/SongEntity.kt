package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rolla.musicplayer.core.model.Song

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album_id"]),
        Index(value = ["media_store_id"]),
    ],
)
data class SongEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "album")
    val album: String,

    @ColumnInfo(name = "album_id")
    val albumId: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "track_number")
    val trackNumber: Int?,

    @ColumnInfo(name = "year")
    val year: Int?,

    @ColumnInfo(name = "content_uri")
    val contentUri: String,

    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String,

    // Used by the media scanner to detect changed files without a full re-scan
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
)

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    contentUri = contentUri,
    artworkUri = artworkUri,
)

fun Song.toEntity(mediaStoreId: Long, dateModified: Long): SongEntity = SongEntity(
    id = id,
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
