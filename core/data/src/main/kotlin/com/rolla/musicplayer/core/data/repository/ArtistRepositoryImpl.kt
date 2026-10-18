package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.database.dao.ArtistDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// The Artists tab sorts in the library order on [sortDispatcher] (Phase 5 rulings 1, 3 and 4). The artist name is the
// GROUP BY key, so it is unique and is its own exact tie-break.
class ArtistRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
    @CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher,
) : ArtistRepository {

    override fun observeArtist(artistName: String): Flow<Artist?> =
        artistDao.observeArtist(artistName)
            .map { row -> row?.toDomain() }
            .distinctUntilChanged()

    override fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeArtists()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { artists -> artists.sortedByLibraryName(compareBy { it.name }) { it.name } }
            .flowOn(sortDispatcher)

    override fun observeArtistAlbums(artistName: String): Flow<List<Album>> =
        artistDao.observeArtistAlbums(artistName)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeArtistSongs(artistName: String): Flow<List<Song>> =
        artistDao.observeArtistSongs(artistName)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
}
