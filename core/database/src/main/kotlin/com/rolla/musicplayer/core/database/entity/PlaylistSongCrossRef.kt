package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["song_id"]),
    ],
)
data class PlaylistSongCrossRef(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)
