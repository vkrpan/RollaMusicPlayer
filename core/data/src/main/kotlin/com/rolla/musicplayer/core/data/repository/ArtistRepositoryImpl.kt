package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.ArtistDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ArtistRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
) : ArtistRepository {

    override fun observeArtist(artistName: String): Flow<Artist?> =
        artistDao.observeArtist(artistName)
            .map { row -> row?.toDomain() }
            .distinctUntilChanged()

    override fun observeArtistAlbums(artistName: String): Flow<List<Album>> =
        artistDao.observeArtistAlbums(artistName)
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observeArtistSongs(artistName: String): Flow<List<Song>> =
        artistDao.observeArtistSongs(artistName)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
}
