package com.rolla.musicplayer.artwork

import coil.intercept.Interceptor
import coil.request.ImageResult
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache

/**
 * Coil [Interceptor] that serves album artwork from the app-local, downscaled [AlbumArtworkCache]
 * instead of decoding the full-resolution MediaStore `content://` image on every request.
 *
 * Every artwork request in the app is built with `data(song/album.artworkUri)`, where `artworkUri`
 * is the scanner's stable `content://media/external/audio/albumart/{albumId}` URI. This interceptor
 * recognises that shape, pulls the albumId out of the last path segment, and asks the cache for a
 * downscaled JPEG [java.io.File] for it (decoded once, reused thereafter). On a hit it rewrites the
 * request's `data` to that file, so:
 * - **Perf:** list/grid binds decode a ~512px file, not the full-size album bitmap.
 * - **Freshness:** the cache's `invalidate` deletes the file and the next `get` rewrites it with a
 *   new last-modified; Coil's default file cache key includes last-modified (and the target size),
 *   so an edited cover refreshes automatically across every display size — no manual eviction, no
 *   stale bitmap served for the unchanged `content://` URI string.
 *
 * Anything that isn't a recognised albumart URI, or a cache miss (no decodable art), falls through
 * to the original request unchanged, so non-album data and the missing-art placeholder path behave
 * exactly as before.
 */
class AlbumArtworkInterceptor(
    private val albumArtworkCache: AlbumArtworkCache,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val cachedFile = albumIdOf(request.data)
            ?.let { albumArtworkCache.get(it, request.data.toString()) }
        val effectiveRequest =
            if (cachedFile != null) request.newBuilder().data(cachedFile).build() else request
        return chain.proceed(effectiveRequest)
    }

    private fun albumIdOf(data: Any?): Long? {
        val uri = data as? String
        return if (uri != null && uri.startsWith(ALBUM_ART_URI_PREFIX)) {
            uri.substringAfterLast('/').toLongOrNull()
        } else {
            null
        }
    }

    private companion object {
        // Matches MediaScanner's ContentUris.withAppendedId(ALBUM_ART_URI, albumId) output.
        const val ALBUM_ART_URI_PREFIX = "content://media/external/audio/albumart/"
    }
}
