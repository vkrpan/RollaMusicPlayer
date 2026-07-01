package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rolla.musicplayer.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAllSongs(): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE media_store_id IN (:ids)")
    suspend fun deleteByMediaStoreIds(ids: List<Long>)

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("UPDATE songs SET is_favorite = NOT is_favorite WHERE id = :songId")
    suspend fun toggleFavorite(songId: String)

    @Query("UPDATE songs SET play_count = play_count + 1, last_played = :timestamp WHERE id = :songId")
    suspend fun recordPlaybackStarted(songId: String, timestamp: Long)

    @Query("SELECT * FROM songs ORDER BY date_added DESC")
    fun observeRecentlyAdded(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE last_played IS NOT NULL ORDER BY last_played DESC")
    fun observeRecentlyPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE play_count > 0 ORDER BY play_count DESC")
    fun observeMostPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title ASC")
    fun observeFavourites(): Flow<List<SongEntity>>
}
