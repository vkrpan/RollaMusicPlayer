package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addSongs(playlistId: Long, songIds: List<String>)
    suspend fun removeSong(playlistId: Long, songId: String)
    suspend fun reorder(playlistId: Long, orderedSongIds: List<String>)
}
