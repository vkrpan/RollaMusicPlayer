package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.relation.PlaylistWithCount
import com.rolla.musicplayer.core.database.relation.PlaylistWithSongs
import com.rolla.musicplayer.core.database.relation.orderedByPosition
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT playlists.*, COUNT(playlist_songs.song_id) AS songCount
        FROM playlists
        LEFT JOIN playlist_songs ON playlists.id = playlist_songs.playlist_id
        GROUP BY playlists.id
        ORDER BY playlists.name ASC
        """,
    )
    fun observePlaylistsWithCounts(): Flow<List<PlaylistWithCount>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithSongsUnordered(playlistId: Long): PlaylistWithSongs?

    /**
     * [PlaylistWithSongs.songs] arrives unordered from the `@Relation` (Room can't express
     * `ORDER BY` there); [PlaylistWithSongs.crossRefs] is loaded in the same call and carries
     * `position`, so this wrapper just re-sorts via [orderedByPosition] before returning.
     */
    suspend fun getPlaylistWithSongs(playlistId: Long): PlaylistWithSongs? =
        getPlaylistWithSongsUnordered(playlistId)?.orderedByPosition()

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query(
        "UPDATE playlist_songs SET position = :position WHERE playlist_id = :playlistId AND song_id = :songId",
    )
    suspend fun updatePosition(playlistId: Long, songId: String, position: Int)

    /** Rewrites every song's position to match its index in [orderedSongIds], in a single transaction. */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedSongIds: List<String>) {
        orderedSongIds.forEachIndexed { index, songId ->
            updatePosition(playlistId, songId, index)
        }
    }
}
