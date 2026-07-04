package com.rolla.musicplayer.core.datastore.di

import com.rolla.musicplayer.core.datastore.EqualizerPreferences
import com.rolla.musicplayer.core.datastore.EqualizerPreferencesImpl
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
}
