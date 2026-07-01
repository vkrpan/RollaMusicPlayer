package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.PlaylistWithCount
import com.rolla.musicplayer.core.database.relation.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [PlaylistDao] backed by [MutableStateFlow]s.
 *
 * Call [emitSongs] to seed the song catalog (needed to resolve [PlaylistDao.observePlaylistSongs]
 * / [PlaylistDao.getPlaylistWithSongs] joins) without touching a real Room database.
 */
class FakePlaylistDao : PlaylistDao {

    private val playlistsFlow = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    private val crossRefsFlow = MutableStateFlow<List<PlaylistSongCrossRef>>(emptyList())
    private val songsFlow = MutableStateFlow<List<SongEntity>>(emptyList())

    private var nextId = 1L

    /** Seeds the in-memory song catalog used to resolve playlist-song joins. */
    fun emitSongs(songs: List<SongEntity>) {
        songsFlow.value = songs
    }

    override fun observePlaylistsWithCounts(): Flow<List<PlaylistWithCount>> =
        playlistsFlow.asStateFlow().map { playlists ->
            playlists.map { playlist ->
                val count = crossRefsFlow.value.count { it.playlistId == playlist.id }
                PlaylistWithCount(playlist = playlist, songCount = count)
            }.sortedBy { it.playlist.name }
        }

    override suspend fun getPlaylistWithSongsUnordered(playlistId: Long): PlaylistWithSongs? {
        val playlist = playlistsFlow.value.firstOrNull { it.id == playlistId } ?: return null
        val refs = crossRefsFlow.value.filter { it.playlistId == playlistId }
        val songs = songsFlow.value.filter { song -> refs.any { it.songId == song.id } }
        return PlaylistWithSongs(playlist = playlist, songs = songs, crossRefs = refs)
    }

    // getPlaylistWithSongs is a top-level extension function on PlaylistDao (see PlaylistDao.kt),
    // not an interface member, so it needs no override here — it delegates through
    // getPlaylistWithSongsUnordered above for every PlaylistDao implementation, fakes included.

    override fun observePlaylistSongs(playlistId: Long): Flow<List<SongEntity>> =
        songsFlow.asStateFlow().map {
            val refs = crossRefsFlow.value.filter { ref -> ref.playlistId == playlistId }.sortedBy { it.position }
            refs.mapNotNull { ref -> songsFlow.value.firstOrNull { song -> song.id == ref.songId } }
        }

    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        val id = if (playlist.id != 0L) playlist.id else nextId++
        playlistsFlow.update { it + playlist.copy(id = id) }
        return id
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String, updatedAt: Long) {
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(name = name, updatedAt = updatedAt) else it }
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistsFlow.update { current -> current.filter { it.id != playlistId } }
        crossRefsFlow.update { current -> current.filter { it.playlistId != playlistId } }
    }

    override suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef) {
        crossRefsFlow.update { current ->
            val filtered = current.filter {
                !(it.playlistId == crossRef.playlistId && it.songId == crossRef.songId)
            }
            filtered + crossRef
        }
    }

    override suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>) {
        crossRefs.forEach { addSongToPlaylist(it) }
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        crossRefsFlow.update { current ->
            current.filter { !(it.playlistId == playlistId && it.songId == songId) }
        }
    }

    override suspend fun updatePosition(playlistId: Long, songId: String, position: Int) {
        crossRefsFlow.update { current ->
            current.map {
                if (it.playlistId == playlistId && it.songId == songId) it.copy(position = position) else it
            }
        }
    }

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<String>) {
        orderedSongIds.forEachIndexed { index, songId ->
            updatePosition(playlistId, songId, index)
        }
    }
}
