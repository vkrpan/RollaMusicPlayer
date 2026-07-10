package com.rolla.musicplayer.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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
 * ### Decode strategy
 * `content://` streams are single-use (they can't be rewound), so the uri is opened TWICE via
 * [Context.getContentResolver]'s `openInputStream`, each in its own `.use { }`:
 * 1. Bounds-only pass ([BitmapFactory.Options.inJustDecodeBounds]) to read the source width/height
 *    without allocating pixel data.
 * 2. A downscaled pass using an [BitmapFactory.Options.inSampleSize] computed by
 *    [calculateInSampleSize] for a [TARGET_MAX_EDGE_PX] target, so the decoded bitmap never comes
 *    in dramatically larger than what the widget actually displays.
 *
 * ### Failure contract
 * Any failure -- an unopenable uri, bytes that don't decode as an image, a [SecurityException]
 * from a revoked grant, or anything else -- returns `null` rather than throwing.
 * [CancellationException] is the sole exception: it is rethrown so structural coroutine
 * cancellation still works. `null` is the *entire* failure contract; this class does not build or
 * know about the themed placeholder shown for a `null` result -- that is the Glance UI's job,
 * landing in a later prompt.
 */
@Singleton
class WidgetArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @WidgetIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Loads and downscales the local artwork at [artworkPath], or returns `null` on a missing
     * path or any decode failure. A `null`/blank [artworkPath] returns `null` immediately without
     * touching [Context.getContentResolver].
     */
    suspend fun load(artworkPath: String?): Bitmap? {
        if (artworkPath.isNullOrBlank()) return null
        return withContext(ioDispatcher) {
            try {
                decode(artworkPath)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                null
            }
        }
    }

    private fun decode(artworkPath: String): Bitmap? {
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
