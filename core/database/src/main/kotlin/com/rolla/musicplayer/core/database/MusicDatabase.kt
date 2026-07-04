package com.rolla.musicplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rolla.musicplayer.core.database.converter.ShortListConverter
import com.rolla.musicplayer.core.database.dao.EqualizerPresetDao
import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.EqualizerPresetEntity
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        EqualizerPresetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ShortListConverter::class)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun equalizerPresetDao(): EqualizerPresetDao

    companion object {
        const val DATABASE_NAME = "music_database"
    }
}
