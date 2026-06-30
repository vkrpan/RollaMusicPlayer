package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun observeSongs(): Flow<List<Song>>
}
