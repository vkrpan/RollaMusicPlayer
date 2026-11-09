package com.rolla.musicplayer.artwork

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.SongArtworkRef
import com.rolla.musicplayer.core.model.parseSongArtworkUri
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Coil [Interceptor] that serves each track's artwork from the app-local, downscaled [SongArtworkCache] (spec §5.1).
 *
 * Every track artwork request in the app is built with `data(song.artworkUri)`, the per-track value
 * `rolla-artwork://song/{id}?v={version}`. This interceptor parses it ([parseSongArtworkUri]) and asks the cache for a
 * downscaled JPEG [java.io.File]: first through the synchronous `peek`, so a picture already on disk never waits for an
 * IO hop (Coil's memory cache, checked after this interceptor, can then answer in the same frame), and only on a peek
 * miss through the suspending `get`, which decodes the file's embedded picture on IO. On a hit it proceeds with the
 * request's `data` rewritten to that file; Coil's file cache key includes the file's last-modified, and a new version
 * of the track is a new value and a new file, so an edited picture never serves a stale bitmap.
 *
 * A track without a picture returns an [ErrorResult] carrying [NoArtworkException] without proceeding: Coil has no
 * fetcher for the `rolla-artwork` scheme, and the placeholder drawn under every `AsyncImage` shows instead. A cache
 * that throws (a disk write failure) is treated the same way (ruling P9); cancellation is rethrown. Data that is not a
 * per-track value proceeds unchanged, so other images are not affected.
 */
class SongArtworkInterceptor(
    private val cache: SongArtworkCache,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val ref = parseSongArtworkUri(request.data as? String) ?: return chain.proceed(request)
        return cachedFile(ref).fold(
            onSuccess = { file ->
                if (file != null) {
                    chain.proceed(request.newBuilder().data(file).build())
                } else {
                    ErrorResult(null, request, NoArtworkException(ref))
                }
            },
            onFailure = { error -> ErrorResult(null, request, NoArtworkException(ref, error)) },
        )
    }

    /** `peek`, then `get` on a peek miss; a failure other than cancellation comes back as [Result.failure]. */
    private suspend fun cachedFile(ref: SongArtworkRef): Result<File?> =
        try {
            Result.success(cache.peek(ref) ?: cache.get(ref))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.failure(e)
        }
}

/** The [ErrorResult] cause when [ref]'s track has no usable picture: none embedded, or the cache failed ([cause]). */
class NoArtworkException(ref: SongArtworkRef, cause: Throwable? = null) :
    Exception("No artwork for song ${ref.songId} (version ${ref.version})", cause)
