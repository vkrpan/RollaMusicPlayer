package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.relation.PlaybackSnapshot
import com.rolla.musicplayer.core.database.relation.QueuedSongRow

/**
 * The saved playback queue and state (queue-restore spec §5.3). The insert and delete members exist so the
 * `@Transaction` default bodies below can compose them; callers use [replaceQueue], [upsertState], [loadSnapshot] and
 * [clear].
 */
@Dao
interface PlaybackQueueDao {

    /** Every saved slot in slot order, each with its song, or a null song when the song has left the library. */
    @Query(
        """
        SELECT q.slot AS slot, q.shuffle_rank AS shuffle_rank, q.song_id AS queued_song_id, s.*
        FROM playback_queue AS q
        LEFT JOIN songs AS s ON s.id = q.song_id
        ORDER BY q.slot ASC
        """,
    )
    suspend fun loadQueue(): List<QueuedSongRow>

    @Query("SELECT * FROM playback_state WHERE id = ${PlaybackStateEntity.SINGLE_ROW_ID}")
    suspend fun loadState(): PlaybackStateEntity?

    @Upsert
    suspend fun upsertState(state: PlaybackStateEntity)

    @Insert
    suspend fun insertQueue(rows: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun deleteQueue()

    @Query("DELETE FROM playback_state")
    suspend fun deleteState()

    /** Replaces the whole queue and the state atomically: a failed insert leaves the previous save intact. */
    @Transaction
    suspend fun replaceQueue(rows: List<PlaybackQueueEntity>, state: PlaybackStateEntity) {
        deleteQueue()
        insertQueue(rows)
        upsertState(state)
    }

    @Transaction
    suspend fun loadSnapshot(): PlaybackSnapshot = PlaybackSnapshot(loadQueue(), loadState())

    @Transaction
    suspend fun clear() {
        deleteQueue()
        deleteState()
    }
}
