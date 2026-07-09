package com.rolla.musicplayer.core.data.di

import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.PlaylistRepositoryImpl
import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.data.repository.SearchRepositoryImpl
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import com.rolla.musicplayer.core.data.repository.SettingsRepositoryImpl
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.data.repository.SongRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
