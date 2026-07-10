package com.rolla.musicplayer.feature.widget

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Qualifies the [CoroutineDispatcher] used for [WidgetArtworkLoader]'s local `content://` bitmap
 * decode. Nothing here is Android-specific, so tests supply their own dispatcher directly to the
 * constructor instead of going through Hilt.
 *
 * `:core:common` has no established dispatcher-qualifier convention (`:feature:tageditor` hit the
 * same gap and landed `TagEditorIoDispatcher` local to that module), so this qualifier mirrors
 * that precedent and stays local to `:feature:widget` rather than introducing a project-wide one
 * unilaterally.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetIoDispatcher

/** Binds [WidgetIoDispatcher] to the real [Dispatchers.IO] for production. */
@Module
@InstallIn(SingletonComponent::class)
object WidgetDispatchersModule {

    @Provides
    @WidgetIoDispatcher
    fun provideWidgetIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
