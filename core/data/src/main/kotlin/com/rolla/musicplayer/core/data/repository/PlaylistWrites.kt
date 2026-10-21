package com.rolla.musicplayer.core.data.repository

/**
 * Creates a playlist called [name] (trimmed) holding [songId], and returns its id. A blank name writes nothing and
 * returns null. Room write failures propagate, like every other repository write; callers run it in viewModelScope.
 * This replaces the "New playlist" bodies the track lists used to copy (Phase 6a ruling 7).
 */
suspend fun PlaylistRepository.createPlaylistWithSong(name: String, songId: String): Long? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val id = createPlaylist(trimmed)
    addSongs(id, listOf(songId))
    return id
}
