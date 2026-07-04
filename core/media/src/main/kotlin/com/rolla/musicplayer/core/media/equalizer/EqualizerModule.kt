package com.rolla.musicplayer.core.media.equalizer

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the production [DeviceEqualizer.Factory] so [EqualizerController] never has to know about
 * `android.media.audiofx.Equalizer` directly. Tests construct [EqualizerController] with a fake
 * factory instead of going through Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object EqualizerModule {

    @Provides
    @Singleton
    fun provideDeviceEqualizerFactory(): DeviceEqualizer.Factory = AudioFxDeviceEqualizerFactory()
}
