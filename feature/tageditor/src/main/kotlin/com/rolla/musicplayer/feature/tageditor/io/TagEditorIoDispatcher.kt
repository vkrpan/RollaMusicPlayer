package com.rolla.musicplayer.feature.tageditor.io

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Qualifies the [CoroutineDispatcher] used for every tag-editor file operation: reading/writing
 * tag fields with jaudiotagger, and copying bytes to/from a `content://` uri in
 * [SongFileResolver]. Nothing here is Android-specific, so tests supply their own dispatcher
 * directly to the relevant constructor instead of going through Hilt.
 *
 * `:core:common` has no established dispatcher-qualifier convention yet (checked before adding
 * this), so the qualifier lives here, local to `:feature:tageditor`, rather than introducing a
 * project-wide one unilaterally.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TagEditorIoDispatcher

/** Binds [TagEditorIoDispatcher] to the real [Dispatchers.IO] for production. */
@Module
@InstallIn(SingletonComponent::class)
object TagEditorDispatchersModule {

    @Provides
    @TagEditorIoDispatcher
    fun provideTagEditorIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
