package com.rolla.musicplayer.core.data.artwork

import javax.inject.Qualifier

/**
 * Qualifies the [java.io.File] directory [SongArtworkCacheImpl] keeps its files in. Bound in `DataModule` to
 * `File(context.cacheDir, "song_art")`; tests construct [SongArtworkCacheImpl] directly with a temp directory.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SongArtworkCacheDir
