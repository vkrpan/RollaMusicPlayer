package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The single saved playback-state row (queue-restore spec §5.1). [repeatMode] is a `RepeatMode` enum name. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLE_ROW_ID,

    @ColumnInfo(name = "current_slot")
    val currentSlot: Int,

    @ColumnInfo(name = "position_ms")
    val positionMs: Long,

    @ColumnInfo(name = "shuffle_enabled")
    val shuffleEnabled: Boolean,

    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
