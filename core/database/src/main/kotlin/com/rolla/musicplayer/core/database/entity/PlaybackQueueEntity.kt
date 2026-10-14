package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved queue entry (queue-restore spec §5.1). [slot] is the Media3 window index, so the same song can sit in two
 * slots. [shuffleRank] is the slot's position in the shuffled playback order. There is deliberately no foreign key to
 * `songs` (ruling R2): a song deleted since the save must stay readable as "missing", with its rank, so the restore can
 * find the next survivor in playback order.
 */
@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "shuffle_rank")
    val shuffleRank: Int,
)
