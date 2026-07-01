package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.entity.toEntity
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylistsWithCounts()
            .map { list -> list.map { it.playlist.toDomain(it.songCount) } }

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observePlaylistSongs(playlistId)
            .map { entities: List<SongEntity> -> entities.map { it.toDomain() } }

    override suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(
            Playlist(
                id = 0L,
                name = name,
                songCount = 0,
                createdAt = now,
                updatedAt = now,
            ).toEntity(),
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        playlistDao.renamePlaylist(playlistId, name, System.currentTimeMillis())
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addSongs(playlistId: Long, songIds: List<String>) = withContext(Dispatchers.IO) {
        // Only the current cross-ref count is needed here (to append after it), so the unordered
        // projection is used directly rather than the position-sorted getPlaylistWithSongs wrapper.
        val startIndex = playlistDao.getPlaylistWithSongsUnordered(playlistId)?.crossRefs?.size ?: 0
        val now = System.currentTimeMillis()
        val crossRefs = songIds.mapIndexed { index, songId ->
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = startIndex + index,
                addedAt = now,
            )
        }
        playlistDao.addSongsToPlaylist(crossRefs)
    }

    override suspend fun removeSong(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<String>) = withContext(Dispatchers.IO) {
        playlistDao.reorder(playlistId, orderedSongIds)
    }
}
