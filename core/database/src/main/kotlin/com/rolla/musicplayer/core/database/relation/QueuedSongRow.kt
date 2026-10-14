package com.rolla.musicplayer.core.database.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity

/** A saved queue slot with its song, or `song == null` when the song is no longer in the library (ruling R2). */
data class QueuedSongRow(
    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "shuffle_rank")
    val shuffleRank: Int,

    @ColumnInfo(name = "queued_song_id")
    val songId: String,

    @Embedded
    val song: SongEntity?,
)

/** The saved queue and state, read in one transaction (ruling R8). */
data class PlaybackSnapshot(
    val queue: List<QueuedSongRow>,
    val state: PlaybackStateEntity?,
)
