package com.rolla.musicplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.SongEntity

@Database(
    entities = [SongEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        const val DATABASE_NAME = "music_database"
    }
}
