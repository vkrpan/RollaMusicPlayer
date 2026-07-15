package com.rolla.musicplayer.core.data.artwork

import com.rolla.musicplayer.core.data.di.CoreDataIoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [AlbumArtworkCache]. One file per album under [cacheDir], named via [cacheFileName].
 *
 * [get] is a classic read-through cache: a hit returns the existing file **untouched** -- crucially
 * WITHOUT rewriting its [File.lastModified]. Both downstream caches key on that timestamp: Coil's
 * default file cache key is `path:lastModified` (so it can serve a decoded bitmap from memory
 * without re-reading the file), and the widget's decode memo keys on `(path, lastModified)`. Bumping
 * lastModified on every read -- e.g. to track access recency -- would change those keys on every
 * bind, defeating both caches (a re-decode per list row, the opposite of this cache's purpose). So
 * lastModified moves ONLY when the file is (re)written: once on the initial decode, and again after
 * an [invalidate] deletes it and the next [get] re-decodes the freshly embedded cover -- which is
 * exactly the signal those downstream caches need to refresh. Eviction therefore orders by
 * write-age, which is appropriate for write-once album art (see [evictOldestWritten]).
 *
 * A miss decodes through [decoder], writes the result, and runs eviction if that write pushed the
 * directory over [MAX_CACHE_BYTES]. [mutex] guards every write to the directory (the miss-path
 * write + eviction, and [clear]) so concurrent [get] calls from many simultaneously binding list
 * rows can't corrupt the eviction pass or double-write the same file -- mirroring `LibraryIndexer`'s
 * internal-[Mutex] precedent. A cache HIT needs no lock: it only stats an already-complete file.
 *
 * All work runs on [ioDispatcher].
 */
@Singleton
class AlbumArtworkCacheImpl @Inject constructor(
    @AlbumArtworkCacheDir private val cacheDir: File,
    private val decoder: ArtworkDecoder,
    @CoreDataIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AlbumArtworkCache {

    private val mutex = Mutex()

    private val mutableInvalidations = MutableSharedFlow<Long>(
        extraBufferCapacity = INVALIDATION_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val invalidations: SharedFlow<Long> = mutableInvalidations.asSharedFlow()

    override suspend fun get(albumId: Long, sourceUri: String): File? = withContext(ioDispatcher) {
        val file = fileFor(albumId)
        // Hit: return the file as-is. Do NOT touch lastModified -- Coil's and the widget's caches
        // key on it (see the class KDoc), so a read must leave it stable.
        if (file.exists()) return@withContext file

        val bytes = decodeSafely(sourceUri) ?: return@withContext null
        mutex.withLock {
            cacheDir.mkdirs()
            file.writeBytes(bytes)
            file.setLastModified(System.currentTimeMillis())
            evictOldestWritten(protectedFile = file)
        }
        file
    }

    override suspend fun invalidate(albumId: Long) {
        withContext(ioDispatcher) {
            mutex.withLock { fileFor(albumId).delete() }
        }
        // extraBufferCapacity + DROP_OLDEST guarantees tryEmit never suspends/drops silently in a
        // way callers need to check -- see the class-level contract on AlbumArtworkCache.
        mutableInvalidations.tryEmit(albumId)
    }

    /**
     * Wipes every cached file. Deliberately emits NOTHING on [invalidations] -- a full clear is
     * only ever triggered by a full manual rescan, and that rescan's own `LibraryIndexer.sync`
     * already re-syncs Room wholesale, so consumers (Coil/widget) pick up fresh state through their
     * own rescan hook rather than draining one [invalidations] event per album.
     */
    override suspend fun clear() {
        withContext(ioDispatcher) {
            mutex.withLock {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    private suspend fun decodeSafely(sourceUri: String): ByteArray? =
        try {
            decoder.decode(sourceUri)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            null
        }

    private fun fileFor(albumId: Long): File = File(cacheDir, cacheFileName(albumId))

    /**
     * Must be called while holding [mutex]. Deletes files ordered oldest-[File.lastModified]-first
     * (write-age, since reads no longer touch the timestamp) until the directory is back at or under
     * [MAX_CACHE_BYTES], skipping [protectedFile] (the entry [get] just wrote) even if it happens to
     * be the oldest by name collision or clock skew. Write-age eviction suits write-once album art:
     * the working set is the covers the user has browsed, each written when first shown; an evicted
     * cover simply re-decodes on its next display.
     */
    private fun evictOldestWritten(protectedFile: File) {
        val files = cacheDir.listFiles()?.toList().orEmpty()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return

        val evictionCandidates = files.filter { it != protectedFile }.sortedBy { it.lastModified() }
        for (candidate in evictionCandidates) {
            if (totalBytes <= MAX_CACHE_BYTES) break
            val size = candidate.length()
            if (candidate.delete()) totalBytes -= size
        }
    }

    /**
     * `internal`, not `private`, so the eviction tests can size fixed fake payloads against
     * [MAX_CACHE_BYTES] directly rather than duplicating the literal.
     */
    internal companion object {
        /** ~150x150dp feature cards, now-playing artwork, and list thumbnails all fit well inside this budget. */
        const val MAX_CACHE_BYTES = 32L * 1024 * 1024
        const val INVALIDATION_BUFFER_CAPACITY = 16
    }
}

/** `albumId` -> stable, distinct cache filename. Exposed `internal` so key derivation is unit-testable in isolation. */
internal fun cacheFileName(albumId: Long): String = "$albumId.jpg"
