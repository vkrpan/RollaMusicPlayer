package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.AlbumDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
) : AlbumRepository {

    override fun observeAlbum(albumId: Long): Flow<Album?> =
        albumDao.observeAlbum(albumId)
            .map { row -> row?.toDomain() }
            .distinctUntilChanged()

    override fun observeAlbumSongs(albumId: Long): Flow<List<Song>> =
        albumDao.observeAlbumSongs(albumId)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
}
