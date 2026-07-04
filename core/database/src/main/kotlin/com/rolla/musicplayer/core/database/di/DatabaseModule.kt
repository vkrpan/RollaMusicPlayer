package com.rolla.musicplayer.core.database.di

import android.content.Context
import androidx.room.Room
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.dao.EqualizerPresetDao
import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.migration.MIGRATION_1_2
import com.rolla.musicplayer.core.database.migration.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, MusicDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideEqualizerPresetDao(database: MusicDatabase): EqualizerPresetDao = database.equalizerPresetDao()
}
