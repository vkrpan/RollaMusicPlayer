package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Room re-runs observed queries on ANY invalidation of the songs table (e.g. a play-count bump on
// one song), re-emitting content-identical lists. distinctUntilChanged() after the domain mapping
// drops those, so downstream StateFlows/UI only see real changes. The Home lists (all songs, favourites) then sort in
// the library order on [sortDispatcher], after the de-duplication, so a play-count bump never re-sorts.
class SongRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    @CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher,
) : SongRepository {

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAllSongs()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { songs -> songs.sortedByLibraryName(SongTieBreak) { it.title } }
            .flowOn(sortDispatcher)

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
            .map { songs -> songs.sortedByLibraryName(SongTieBreak) { it.title } }
            .flowOn(sortDispatcher)

    private companion object {
        // Ruling 4: an exact-title tie goes to the id, so equal titles never swap between emissions.
        val SongTieBreak: Comparator<Song> = compareBy<Song> { it.title }.thenBy { it.id }
    }
}
