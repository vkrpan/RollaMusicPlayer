package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rolla.musicplayer.core.model.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

// songCount is derived from the playlist_songs cross-ref table (see PlaylistWithCount),
// not stored on this entity — callers must supply it from that projection.
fun PlaylistEntity.toDomain(songCount: Int): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
