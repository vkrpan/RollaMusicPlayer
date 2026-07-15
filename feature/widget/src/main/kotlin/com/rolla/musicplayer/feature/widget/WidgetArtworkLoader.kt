package com.rolla.musicplayer.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes [MusicWidgetState.artworkPath] -- a local `content://` uri, never a URL -- into a
 * DOWNSCALED [Bitmap] sized for the widget, entirely offline.
 *
 * ### Why not Coil
 * The rest of the app loads artwork through Coil, but the widget renders through Glance's
 * `RemoteViews` bridge, which has a tight per-appwidget memory budget (a full-resolution bitmap
 * risks `IllegalArgumentException`/`TransactionTooLargeException` when the `RemoteViews` bundle
 * is parceled to the launcher's process). Coil's caching, request-lifecycle, and Compose-target
 * machinery is built for in-app composition and does nothing to help with that budget -- what's
 * needed here is a small, bounded, two-pass [BitmapFactory] decode that never inflates a
 * full-size bitmap in the first place. `:feature:tageditor`'s `ArtworkLoader` set the same
 * precedent: a hand-rolled decode for a non-Coil consumer.
 *
 * ### Wave 2: routed through [AlbumArtworkCache]
 * [artworkPath] is the scanner's `content://media/external/audio/albumart/{albumId}` uri. The
 * `albumId` is parsed off its last path segment and handed to [AlbumArtworkCache.get], which
 * returns an already-downscaled (~512px) local JPEG [File] -- decoded, cached, and shared with
 * every other artwork consumer in the app (Coil, the tag editor). This loader's own two-pass
 * decode now runs against that small file instead of the raw (possibly huge, embedded-cover-art)
 * `content://` stream, so it stays cheap even without a hit on the local memo below. A path whose
 * last segment does NOT parse as a `Long` (unparseable/unexpected format) falls back to the
 * original direct `content://` decode so nothing regresses for a path this loader can't resolve
 * an `albumId` from. A `null` from [AlbumArtworkCache.get] (no decodable art) returns `null`
 * immediately -- the Glance UI's existing placeholder handles it, unchanged.
 *
 * ### Decode strategy
 * `content://` streams are single-use (they can't be rewound), so the direct-decode fallback opens
 * the uri TWICE via [Context.getContentResolver]'s `openInputStream`, each in its own `.use { }`.
 * The cache-file path decodes the same [File] twice instead (files support repeat reads, no
 * stream-reopen dance needed). Both passes are:
 * 1. Bounds-only ([BitmapFactory.Options.inJustDecodeBounds]) to read the source width/height
 *    without allocating pixel data.
 * 2. A downscaled pass using an [BitmapFactory.Options.inSampleSize] computed by
 *    [calculateInSampleSize] for a [TARGET_MAX_EDGE_PX] target, so the decoded bitmap never comes
 *    in dramatically larger than what the widget actually displays.
 *
 * ### Failure contract
 * Any failure -- an unopenable uri/file, bytes that don't decode as an image, a [SecurityException]
 * from a revoked grant, or anything else -- returns `null` rather than throwing.
 * [CancellationException] is the sole exception: it is rethrown so structural coroutine
 * cancellation still works. `null` is the *entire* failure contract; this class does not build or
 * know about the themed placeholder shown for a `null` result -- that is the Glance UI's job.
 *
 * ### Single-entry memoization
 * The service's ~1s position tick re-renders the widget on every tick while playing, and every
 * render asks for the SAME artwork path until the track changes -- without memoization that is a
 * repeated decode per second, for hours, producing byte-identical bitmaps (review-gate HIGH
 * finding from wave 1). The last successful decode is memoized keyed by the exact source it was
 * decoded from: for the cache-file path that's `(File.getAbsolutePath, File.lastModified)`, not
 * just the path -- [AlbumArtworkCache.invalidate] deletes and lets the next [AlbumArtworkCache.get]
 * rewrite the file with fresh bytes AND a fresh `lastModified`, so a genuinely edited cover busts
 * the memo and re-decodes automatically. Subscribing to [AlbumArtworkCache.invalidations] to
 * explicitly clear the memo was considered and rejected as unnecessary complexity: this loader
 * already re-resolves [AlbumArtworkCache.get] on every [load] call (the widget re-renders after a
 * tag edit anyway, via the existing `PlaybackUpdateHook` metadata refresh), so the lastModified
 * comparison alone is sufficient to detect staleness without a second, independent invalidation
 * listener to keep in sync. The direct-decode fallback keeps its original path-only memo key,
 * since a raw `content://` uri carries no local file/mtime to compare. Only ever one "current"
 * artwork exists, so a plain volatile field pair suffices (no LruCache). The evicted bitmap is NOT
 * [Bitmap.recycle]d -- an in-flight RemoteViews parcel may still reference it; dropping the
 * reference and letting GC reclaim it is the safe teardown.
 */
@Singleton
class WidgetArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumArtworkCache: AlbumArtworkCache,
    @WidgetIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    @Volatile
    private var memoKey: DecodeKey? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    /**
     * Loads and downscales the local artwork at [artworkPath], or returns `null` on a missing
     * path, no decodable art, or any decode failure. A `null`/blank [artworkPath] returns `null`
     * immediately without touching the cache or [Context.getContentResolver]; an unchanged source
     * returns the memoized bitmap without a re-decode (see the class KDoc).
     */
    suspend fun load(artworkPath: String?): Bitmap? {
        if (artworkPath.isNullOrBlank()) return null
        return withContext(ioDispatcher) {
            try {
                loadInternal(artworkPath)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                null
            }
        }
    }

    private suspend fun loadInternal(artworkPath: String): Bitmap? {
        val albumId = albumIdOrNull(artworkPath)
        return if (albumId != null) {
            val cachedFile = albumArtworkCache.get(albumId, artworkPath) ?: return null
            decodeMemoized(DecodeKey.CacheFile(cachedFile.absolutePath, cachedFile.lastModified())) {
                decodeFile(cachedFile)
            }
        } else {
            decodeMemoized(DecodeKey.DirectUri(artworkPath)) { decodeContentUri(artworkPath) }
        }
    }

    /**
     * Parses `{albumId}` off the scanner's `content://.../albumart/{albumId}` uri's last path
     * segment -- deliberately plain string splitting rather than [Uri.parse]/[Uri.getLastPathSegment]:
     * `Uri.parse` is a real device call but an unmocked, throwing JVM stub under plain JUnit (see
     * `PlaybackControllerTest`'s `mockkStatic(Uri::class)` precedent in `:core:media` for the same
     * constraint), and this parse only ever needs to split a fixed, app-controlled uri shape, not
     * handle arbitrary/query-bearing uris the way [Uri] does.
     */
    private fun albumIdOrNull(artworkPath: String): Long? =
        artworkPath.substringAfterLast('/').toLongOrNull()

    /**
     * Returns the memoized bitmap for [key] without decoding, else decodes via [decode] and
     * memoizes it. `internal`, not `private`, so the memo's hit/bust behavior is directly
     * unit-testable against a fake `decode` lambda -- a real decode requires [BitmapFactory],
     * an unimplemented stub under plain JUnit (see the class KDoc).
     */
    internal fun decodeMemoized(key: DecodeKey, decode: () -> Bitmap?): Bitmap? {
        val memoized = cachedBitmap?.takeIf { memoKey == key }
        return memoized ?: decode()?.also {
            memoKey = key
            cachedBitmap = it
        }
    }

    /** Two-pass decode of an already-downscaled [AlbumArtworkCache] file -- files support repeat reads. */
    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE_PX)
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    /** Direct two-pass `content://` decode -- the pre-wave-2 fallback for an unparseable [artworkPath]. */
    private fun decodeContentUri(artworkPath: String): Bitmap? {
        val uri = Uri.parse(artworkPath)
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (boundsRead == null || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE_PX)
        }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    /**
     * The exact source a memoized [Bitmap] was decoded from; see the class KDoc on memoization.
     * `internal` alongside [decodeMemoized] for the same test-seam reason.
     */
    internal sealed interface DecodeKey {
        data class CacheFile(val absolutePath: String, val lastModified: Long) : DecodeKey
        data class DirectUri(val path: String) : DecodeKey
    }

    private companion object {
        /** Roughly the widget's displayed artwork size -- generous for any launcher density. */
        const val TARGET_MAX_EDGE_PX = 256
    }
}

/**
 * Computes the smallest power-of-two [BitmapFactory.Options.inSampleSize] such that decoding a
 * [width]x[height] source downscales its longer edge to at or just above [targetMaxEdge] --
 * [BitmapFactory] only honors power-of-two sample sizes, so this doubles from `1` until halving
 * again would drop the longer edge below the target.
 *
 * Non-positive dimensions (a malformed or unreadable source) defensively return `1` rather than
 * looping or dividing by a non-positive number.
 */
internal fun calculateInSampleSize(width: Int, height: Int, targetMaxEdge: Int): Int {
    if (width <= 0 || height <= 0 || targetMaxEdge <= 0) return 1
    val longerEdge = maxOf(width, height)
    var sampleSize = 1
    while (longerEdge / (sampleSize * 2) >= targetMaxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}
