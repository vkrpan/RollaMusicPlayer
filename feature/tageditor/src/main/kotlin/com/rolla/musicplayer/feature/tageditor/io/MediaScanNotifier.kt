package com.rolla.musicplayer.feature.tageditor.io

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Suspending wrapper around [MediaScannerConnection.scanFile], used right after a successful tag
 * write so MediaStore re-reads the edited file(s) before the library's targeted re-sync queries
 * it -- without this, `LibraryIndexer.syncSongs` would just re-read MediaStore's still-stale row
 * (MediaStore does not reliably re-parse a file's tags when its bytes are rewritten through
 * `openOutputStream`). Notifying MediaStore also makes the change visible to other apps, per the
 * tag-editor skill's re-index step.
 *
 * [awaitScan] resumes once every path's scan completion callback has fired, bounded by
 * [SCAN_COMPLETION_TIMEOUT_MS]: the platform scanner offers no failure callback, so a path it
 * never acknowledges (deleted mid-flight, unmounted volume) would otherwise suspend the caller
 * forever. On timeout the method simply returns -- the follow-up re-sync then reads whatever
 * MediaStore currently holds, which is never worse than not having waited at all.
 */
class MediaScanNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun awaitScan(paths: List<String>) {
        if (paths.isEmpty()) return
        withTimeoutOrNull(SCAN_COMPLETION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val remaining = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                    // Resume exactly once, on the LAST completion callback. A callback that
                    // arrives after the timeout already cancelled the continuation is dropped
                    // silently (resuming a cancelled cancellable continuation is a no-op).
                    if (remaining.decrementAndGet() == 0) continuation.resume(Unit)
                }
            }
        }
    }

    private companion object {
        const val SCAN_COMPLETION_TIMEOUT_MS = 10_000L
    }
}
