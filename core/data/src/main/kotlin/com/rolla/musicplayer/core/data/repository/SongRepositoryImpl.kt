package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
) : SongRepository {

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAllSongs().map { entities -> entities.map { it.toDomain() } }

    override suspend fun toggleFavorite(songId: String) = withContext(Dispatchers.IO) {
        songDao.toggleFavorite(songId)
    }

    override suspend fun recordPlaybackStarted(songId: String) = withContext(Dispatchers.IO) {
        songDao.recordPlaybackStarted(songId, System.currentTimeMillis())
    }

    override fun observeRecentlyAdded(): Flow<List<Song>> =
        songDao.observeRecentlyAdded().map { entities -> entities.map { it.toDomain() } }

    override fun observeRecentlyPlayed(): Flow<List<Song>> =
        songDao.observeRecentlyPlayed().map { entities -> entities.map { it.toDomain() } }

    override fun observeMostPlayed(): Flow<List<Song>> =
        songDao.observeMostPlayed().map { entities -> entities.map { it.toDomain() } }

    override fun observeFavourites(): Flow<List<Song>> =
        songDao.observeFavourites().map { entities -> entities.map { it.toDomain() } }
}
