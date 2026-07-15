package com.rolla.musicplayer.core.data.artwork

import javax.inject.Qualifier

/**
 * Qualifies the [java.io.File] cache directory [AlbumArtworkCacheImpl] persists its downscaled
 * JPEGs into. Bound in `DataModule` to `File(context.cacheDir, "album_art")` for production; tests
 * construct [AlbumArtworkCacheImpl] directly with a plain temp directory, no Hilt/qualifier
 * involved.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlbumArtworkCacheDir
