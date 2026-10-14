package com.rolla.musicplayer.core.database.di

import android.content.Context
import androidx.room.Room
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.dao.AlbumDao
import com.rolla.musicplayer.core.database.dao.ArtistDao
import com.rolla.musicplayer.core.database.dao.EqualizerPresetDao
import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.dao.SearchDao
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.migration.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // addMigrations is vararg-only; the spread copies the small ALL_MIGRATIONS array once per process, at database
    // creation.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, MusicDatabase.DATABASE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideEqualizerPresetDao(database: MusicDatabase): EqualizerPresetDao = database.equalizerPresetDao()

    @Provides
    fun provideSearchDao(database: MusicDatabase): SearchDao = database.searchDao()

    @Provides
    fun provideAlbumDao(database: MusicDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: MusicDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideFolderDao(database: MusicDatabase): FolderDao = database.folderDao()

    @Provides
    fun providePlaybackQueueDao(database: MusicDatabase): PlaybackQueueDao = database.playbackQueueDao()
}
