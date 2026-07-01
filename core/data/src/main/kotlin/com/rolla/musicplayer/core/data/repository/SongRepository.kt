package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun observeSongs(): Flow<List<Song>>

    /** Flips the favorite flag on the given song. */
    suspend fun toggleFavorite(songId: String)

    /** Increments the play count and stamps `last_played` with the current time for the given song. */
    suspend fun recordPlaybackStarted(songId: String)

    /** Songs ordered by when they were first indexed by the scanner, most recent first. */
    fun observeRecentlyAdded(): Flow<List<Song>>

    /** Songs that have been played at least once, most recently played first. */
    fun observeRecentlyPlayed(): Flow<List<Song>>

    /** Songs that have been played at least once, most played first. */
    fun observeMostPlayed(): Flow<List<Song>>

    /** Songs marked as favorite, alphabetical by title. */
    fun observeFavourites(): Flow<List<Song>>
}
