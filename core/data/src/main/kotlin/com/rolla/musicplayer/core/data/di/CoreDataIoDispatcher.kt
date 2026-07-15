package com.rolla.musicplayer.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Qualifies the [CoroutineDispatcher] used for [com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache]'s
 * local disk-cache IO (decode, file write, eviction). `:core:common` has no established
 * dispatcher-qualifier convention yet -- `:feature:tageditor`'s `TagEditorIoDispatcher` and
 * `:feature:widget`'s `WidgetIoDispatcher` hit the same gap -- so this mirrors that precedent and
 * stays local to `:core:data` rather than introducing a project-wide one unilaterally.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CoreDataIoDispatcher

/** Binds [CoreDataIoDispatcher] to the real [Dispatchers.IO] for production. */
@Module
@InstallIn(SingletonComponent::class)
object CoreDataDispatchersModule {

    @Provides
    @CoreDataIoDispatcher
    fun provideCoreDataIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
