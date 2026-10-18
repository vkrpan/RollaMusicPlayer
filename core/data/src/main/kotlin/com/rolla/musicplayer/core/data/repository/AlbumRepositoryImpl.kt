package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.database.dao.AlbumDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// The Albums grid sorts in the library order on [sortDispatcher] (Phase 5 rulings 3 and 5).
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    @CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher,
) : AlbumRepository {

    override fun observeAlbum(albumId: Long): Flow<Album?> =
        albumDao.observeAlbum(albumId)
            .map { row -> row?.toDomain() }
            .distinctUntilChanged()

    override fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAlbums()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { albums -> albums.sortedByLibraryName(compareBy<Album> { it.title }.thenBy { it.id }) { it.title } }
            .flowOn(sortDispatcher)

    override fun observeAlbumSongs(albumId: Long): Flow<List<Song>> =
        albumDao.observeAlbumSongs(albumId)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
}
