package com.rolla.musicplayer.core.datastore.di

import com.rolla.musicplayer.core.datastore.EqualizerPreferences
import com.rolla.musicplayer.core.datastore.EqualizerPreferencesImpl
import com.rolla.musicplayer.core.datastore.RecentSearchesDataSource
import com.rolla.musicplayer.core.datastore.RecentSearchesDataSourceImpl
import com.rolla.musicplayer.core.datastore.SettingsPreferences
import com.rolla.musicplayer.core.datastore.SettingsPreferencesImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindEqualizerPreferences(impl: EqualizerPreferencesImpl): EqualizerPreferences

    @Binds
    @Singleton
    abstract fun bindRecentSearchesDataSource(impl: RecentSearchesDataSourceImpl): RecentSearchesDataSource

    @Binds
    @Singleton
    abstract fun bindSettingsPreferences(impl: SettingsPreferencesImpl): SettingsPreferences
}
