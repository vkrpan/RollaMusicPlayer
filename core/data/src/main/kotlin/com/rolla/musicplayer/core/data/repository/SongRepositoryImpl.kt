package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
) : SongRepository {

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAllSongs().map { entities -> entities.map { it.toDomain() } }
}
