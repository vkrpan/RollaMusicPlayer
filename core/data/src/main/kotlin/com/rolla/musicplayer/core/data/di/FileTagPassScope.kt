package com.rolla.musicplayer.core.data.di

import javax.inject.Qualifier

/**
 * Qualifies the app-lifetime [kotlinx.coroutines.CoroutineScope] that
 * [com.rolla.musicplayer.core.data.scanner.FileTagPass.request] runs the pass on, so a sync returns without waiting for
 * a long read of the library's files. Provided by `DataModule` (a `SupervisorJob` on `Dispatchers.IO`, never
 * cancelled); tests inject a scope on a test dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FileTagPassScope
