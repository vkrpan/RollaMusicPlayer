package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.songArtworkUri

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

    // Written by the scanner (the per-track value) but never read: toDomain derives it (ruling R1). Rows from before
    // per-track artwork still hold MediaStore album-art uris here.
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String,

    // Used by the media scanner to detect changed files without a full re-scan
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,

    // When this song was first indexed by the scanner (backing the "Recently added" smart playlist)
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = 0L,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,

    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null,

    // The song's folder relative to its storage volume, "/"-separated with no leading or trailing slash
    // ("Music/Chill"). "" is the volume root; null means not scanned since the v4 migration, and LibraryIndexer
    // fills it (spec §9.4).
    @ColumnInfo(name = "folder_path", defaultValue = "NULL")
    val folderPath: String? = null,

    // The genre (v6): the file's own genre once the file pass has read it, else the scanned MediaStore value; null
    // when neither has one. Not part of the Song model yet.
    @ColumnInfo(name = "genre", defaultValue = "NULL")
    val genre: String? = null,

    // True once the file pass has read this file's custom tags and genre (v6). The scanner resets it when the file
    // changes, so the next pass reads it again.
    @ColumnInfo(name = "file_tags_read", defaultValue = "0")
    val fileTagsRead: Boolean = false,
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
    // Read-time derivation (spec §5.4, ruling R1): the stored artwork_uri column is ignored, so rows written before
    // per-track artwork (MediaStore album-art uris) never reach a screen and no Room migration is needed.
    artworkUri = songArtworkUri(id, dateModified),
)
