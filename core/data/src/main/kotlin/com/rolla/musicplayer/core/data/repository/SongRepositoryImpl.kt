package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Room re-runs observed queries on ANY invalidation of the songs table (e.g. a play-count bump on
// one song), re-emitting content-identical lists. distinctUntilChanged() after the domain mapping
// drops those, so downstream StateFlows/UI only see real changes.
class SongRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
) : SongRepository {

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAllSongs()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeSong(songId: String): Flow<Song?> =
        songDao.observeSong(songId)
            .map { entity -> entity?.toDomain() }
            .distinctUntilChanged()

    override suspend fun toggleFavorite(songId: String) = withContext(Dispatchers.IO) {
        songDao.toggleFavorite(songId)
    }

    override suspend fun recordPlaybackStarted(songId: String) = withContext(Dispatchers.IO) {
        songDao.recordPlaybackStarted(songId, System.currentTimeMillis())
    }

    override fun observeRecentlyAdded(): Flow<List<Song>> =
        songDao.observeRecentlyAdded()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeRecentlyPlayed(): Flow<List<Song>> =
        songDao.observeRecentlyPlayed()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeMostPlayed(): Flow<List<Song>> =
        songDao.observeMostPlayed()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeFavourites(): Flow<List<Song>> =
        songDao.observeFavourites()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
}
