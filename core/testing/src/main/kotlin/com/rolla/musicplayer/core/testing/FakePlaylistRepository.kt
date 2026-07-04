package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [PlaylistRepository].
 *
 * Call [emitPlaylists] / [emitPlaylistSongs] from tests to push state through the observe*
 * flows without touching a database. Mirrors [FakeSongRepository]'s style for use by
 * ViewModel tests in later phases.
 */
class FakePlaylistRepository : PlaylistRepository {

    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val playlistSongsFlow = MutableStateFlow<Map<Long, List<Song>>>(emptyMap())

    private var nextId = 1L

    /**
     * Records every [addSongs] invocation as `(playlistId, songIds)`, in call order.
     *
     * [addSongs] itself remains a no-op below (see its docstring) since the fake has no song
     * catalog to resolve ids from — tests asserting on the resulting song list should use
     * [emitPlaylistSongs] directly. This list exists purely so ViewModel tests can verify *that*
     * `addSongs` was called with the expected arguments (e.g. from an "Add to playlist" action).
     */
    val addSongsCalls: List<Pair<Long, List<String>>> get() = _addSongsCalls
    private val _addSongsCalls = mutableListOf<Pair<Long, List<String>>>()

    /** Replaces the current in-memory playlist list, triggering a new emission on the flow. */
    fun emitPlaylists(playlists: List<Playlist>) {
        playlistsFlow.value = playlists
    }

    /** Replaces the in-memory song list for [playlistId], triggering a new emission on the flow. */
    fun emitPlaylistSongs(playlistId: Long, songs: List<Song>) {
        playlistSongsFlow.update { it + (playlistId to songs) }
    }

    override fun observePlaylists(): Flow<List<Playlist>> = playlistsFlow.asStateFlow()

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistSongsFlow.asStateFlow().map { it[playlistId] ?: emptyList() }

    override suspend fun createPlaylist(name: String): Long {
        val id = nextId++
        val now = System.currentTimeMillis()
        playlistsFlow.update { it + Playlist(id = id, name = name, songCount = 0, createdAt = now, updatedAt = now) }
        return id
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(name = name) else it }
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistsFlow.update { current -> current.filter { it.id != playlistId } }
        playlistSongsFlow.update { it - playlistId }
    }

    override suspend fun addSongs(playlistId: Long, songIds: List<String>) {
        // Fakes don't have a song catalog to resolve ids from; tests should use [emitPlaylistSongs]
        // directly to set up the expected end state instead of relying on id resolution here.
        _addSongsCalls += playlistId to songIds
    }

    override suspend fun removeSong(playlistId: Long, songId: String) {
        playlistSongsFlow.update { current ->
            val songs = current[playlistId]?.filter { it.id != songId } ?: return@update current
            current + (playlistId to songs)
        }
    }

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<String>) {
        playlistSongsFlow.update { current ->
            val songs = current[playlistId] ?: return@update current
            val bySongId = songs.associateBy { it.id }
            val reordered = orderedSongIds.mapNotNull { bySongId[it] }
            current + (playlistId to reordered)
        }
    }
}
