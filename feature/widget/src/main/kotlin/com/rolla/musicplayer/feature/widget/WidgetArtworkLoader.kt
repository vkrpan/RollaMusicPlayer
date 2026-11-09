package com.rolla.musicplayer.feature.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.parseSongArtworkUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes [MusicWidgetState.artworkPath] -- the current song's per-track artwork value,
 * `rolla-artwork://song/{id}?v={version}`, never a URL -- into a DOWNSCALED [Bitmap] sized for the
 * widget, entirely offline.
 *
 * ### Why not Coil
 * The rest of the app loads artwork through Coil, but the widget renders through Glance's
 * `RemoteViews` bridge, which has a tight per-appwidget memory budget (a full-resolution bitmap
 * risks `IllegalArgumentException`/`TransactionTooLargeException` when the `RemoteViews` bundle
 * is parceled to the launcher's process). Coil's caching, request-lifecycle, and Compose-target
 * machinery is built for in-app composition and does nothing to help with that budget -- what's
 * needed here is a small, bounded, two-pass [BitmapFactory] decode that never inflates a
 * full-size bitmap in the first place.
 *
 * ### Routed through [SongArtworkCache]
 * The value is parsed with [parseSongArtworkUri] and handed to [SongArtworkCache.get], which
 * returns an already-downscaled (~512px) local JPEG [File] of the picture embedded in that track's
 * own file -- decoded, cached, and shared with every other artwork consumer in the app (Coil, the
 * notification). A `null` from the cache (the file has no picture) returns `null`. Anything that
 * does not parse -- including a MediaStore album uri -- is "no artwork" and returns `null` without
 * reading anything: the widget never shows a picture that is not in the track's own file (spec §4).
 * The Glance UI's placeholder handles every `null`.
 *
 * ### Decode strategy
 * Two passes over the cache [File] (files support repeat reads):
 * 1. Bounds-only ([BitmapFactory.Options.inJustDecodeBounds]) to read the source width/height
 *    without allocating pixel data.
 * 2. A downscaled pass using an [BitmapFactory.Options.inSampleSize] computed by
 *    [calculateInSampleSize] for a [TARGET_MAX_EDGE_PX] target, so the decoded bitmap never comes
 *    in dramatically larger than what the widget actually displays.
 *
 * ### Failure contract
 * Any failure -- an unreadable file, bytes that don't decode as an image, a cache that throws (a
 * disk write failure, ruling P9), or anything else -- returns `null` rather than throwing.
 * [CancellationException] is the sole exception: it is rethrown so structural coroutine
 * cancellation still works. `null` is the *entire* failure contract; this class does not build or
 * know about the themed placeholder shown for a `null` result -- that is the Glance UI's job.
 *
 * ### Single-entry memoization
 * The service's ~1s position tick re-renders the widget on every tick while playing, and every
 * render asks for the SAME artwork value until the track changes -- without memoization that is a
 * repeated decode per second, for hours, producing byte-identical bitmaps. The last successful
 * decode is memoized keyed by the cache file it was decoded from, `(File.getAbsolutePath,
 * File.lastModified)`: the cache publishes a new file (new name, new stamp) for a new version of
 * the track, and a hit never touches the stamp, so a rewritten picture busts the memo and an
 * unchanged one never does. Every [load] still re-resolves [SongArtworkCache.get]; only the
 * [BitmapFactory] decode is skipped behind the memo. Only ever one "current" artwork exists, so a
 * plain volatile field pair suffices (no LruCache). The evicted bitmap is NOT [Bitmap.recycle]d --
 * an in-flight RemoteViews parcel may still reference it; dropping the reference and letting GC
 * reclaim it is the safe teardown.
 *
 * [decodeFile] is the two-pass decode in production; the `internal` constructor lets tests replace
 * it, since [BitmapFactory] is an unimplemented stub under plain JUnit.
 */
@Singleton
class WidgetArtworkLoader internal constructor(
    private val songArtworkCache: SongArtworkCache,
    private val ioDispatcher: CoroutineDispatcher,
    private val decodeFile: (File) -> Bitmap?,
) {

    @Inject
    constructor(
        songArtworkCache: SongArtworkCache,
        @WidgetIoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(songArtworkCache, ioDispatcher, ::decodeDownscaled)

    @Volatile
    private var memoKey: DecodeKey? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    /**
     * Loads and downscales the picture embedded in the track named by [artworkPath], or returns
     * `null` when [artworkPath] is null, blank or not a per-track artwork value, when the track has
     * no picture, or on any failure. An unchanged cache file returns the memoized bitmap without a
     * re-decode (see the class KDoc).
     */
    suspend fun load(artworkPath: String?): Bitmap? {
        val ref = parseSongArtworkUri(artworkPath) ?: return null
        return withContext(ioDispatcher) {
            try {
                val cachedFile = songArtworkCache.get(ref)
                cachedFile?.let { file ->
                    decodeMemoized(DecodeKey(file.absolutePath, file.lastModified())) { decodeFile(file) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                null
            }
        }
    }

    /**
     * Returns the memoized bitmap for [key] without decoding, else decodes via [decode] and
     * memoizes it. `internal`, not `private`, so the memo's hit/bust behavior is directly
     * unit-testable against a fake `decode` lambda.
     */
    internal fun decodeMemoized(key: DecodeKey, decode: () -> Bitmap?): Bitmap? {
        val memoized = cachedBitmap?.takeIf { memoKey == key }
        return memoized ?: decode()?.also {
            memoKey = key
            cachedBitmap = it
        }
    }

    /**
     * The exact cache file a memoized [Bitmap] was decoded from; see the class KDoc on memoization.
     * `internal` alongside [decodeMemoized] for the same test-seam reason.
     */
    internal data class DecodeKey(val absolutePath: String, val lastModified: Long)
}

/** Roughly the widget's displayed artwork size -- generous for any launcher density. */
private const val TARGET_MAX_EDGE_PX = 256

/** Two-pass downscaled decode of an already-downscaled [SongArtworkCache] file; see the class KDoc. */
private fun decodeDownscaled(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE_PX)
    }
    return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
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
