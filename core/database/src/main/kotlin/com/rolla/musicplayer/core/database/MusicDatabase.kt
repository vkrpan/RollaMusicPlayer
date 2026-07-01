package com.rolla.musicplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "music_database"
    }
}
