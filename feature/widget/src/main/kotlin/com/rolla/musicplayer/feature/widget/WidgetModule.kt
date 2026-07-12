package com.rolla.musicplayer.feature.widget

import com.rolla.musicplayer.core.media.PlaybackUpdateHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetStateProvider(impl: WidgetStateProviderImpl): WidgetStateProvider

    /** Contributes the widget's updater into :core:media's service-fired hook set. */
    @Binds
    @IntoSet
    abstract fun bindPlaybackUpdateHook(impl: WidgetPlaybackUpdateHook): PlaybackUpdateHook
}
