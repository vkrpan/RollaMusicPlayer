package com.rolla.musicplayer.core.database.di

import android.content.Context
import androidx.room.Room
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.dao.SongDao
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
            .build()

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao = database.songDao()
}
