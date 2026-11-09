package com.rolla.musicplayer.core.data.artwork

import com.rolla.musicplayer.core.data.di.CoreDataIoDispatcher
import com.rolla.musicplayer.core.model.SongArtworkRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [SongArtworkCache]. Under [cacheDir], a song's entry is `{songId}-{version}.jpg` (its picture) or
 * `{songId}-{version}.none` (a zero-byte marker: the file has no picture). A song has at most one entry: publishing a
 * version drops the song's other versions.
 *
 * The guarantees carried over from the album cache it replaces (spec §5.1):
 * - **A hit never touches [File.lastModified].** Coil's file cache key (`path:lastModified`) and the widget's decode
 *   memo key on it, so a stamp that moved on every read would defeat both. It moves only when a file is (re)written.
 *   Eviction therefore orders by write-age ([evictOldestWritten]).
 * - **Publish by rename.** A miss writes a temp file ([TEMP_SUFFIX]) and renames it to its final name, so [peek] and a
 *   lock-free hit never see a partial JPEG.
 * - **One [Mutex] for every write** (publish, eviction, [invalidate], [clear]), so many list rows binding at once
 *   cannot corrupt an eviction pass. Decoding runs outside the lock: two concurrent misses for one ref may both decode,
 *   but only the first to take the lock publishes; the second re-checks under the lock and returns the published entry
 *   without rewriting it.
 * - **A fixed budget**, [MAX_CACHE_BYTES] (ruling R2: about 800 tracks at ~40 KB each; an evicted picture simply
 *   decodes again).
 *
 * A decoder that throws caches nothing (no negative marker), so a later [get] tries again (Review Focus 3).
 *
 * All work runs on [ioDispatcher].
 */
@Singleton
class SongArtworkCacheImpl @Inject constructor(
    @SongArtworkCacheDir private val cacheDir: File,
    private val decoder: EmbeddedArtworkDecoder,
    @CoreDataIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SongArtworkCache {

    private val mutex = Mutex()

    override suspend fun get(ref: SongArtworkRef): File? = withContext(ioDispatcher) {
        val picture = pictureFile(ref)
        val marker = markerFile(ref)
        // Hits: return as-is, without touching lastModified (see the class KDoc).
        if (picture.exists()) return@withContext picture
        if (marker.exists()) return@withContext null

        val decoded = decodeOrNull(ref.songId) ?: return@withContext null
        mutex.withLock {
            // Another miss for this ref may have published while this one decoded: keep its entry (and its stamp).
            if (picture.exists()) return@withContext picture
            if (marker.exists()) return@withContext null
            cacheDir.mkdirs()
            // A new version (the file changed) replaces whatever the song had cached before.
            entriesOf(ref.songId).forEach { it.delete() }
            when (decoded) {
                is DecodedArtwork.Picture -> {
                    if (!publish(picture, decoded.jpeg)) return@withContext null
                    evictOldestWritten(protectedFile = picture)
                    picture
                }
                DecodedArtwork.None -> {
                    publish(marker, ByteArray(0))
                    null
                }
            }
        }
    }

    override fun peek(ref: SongArtworkRef): File? = pictureFile(ref).takeIf { it.exists() }

    override suspend fun invalidate(songId: Long) {
        withContext(ioDispatcher) {
            mutex.withLock { entriesOf(songId).forEach { it.delete() } }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            mutex.withLock { cacheDir.listFiles()?.forEach { it.delete() } }
        }
    }

    /** The decoder's answer, or null when it threw: a failure is not "no picture", so nothing gets cached. */
    private suspend fun decodeOrNull(songId: Long): DecodedArtwork? =
        try {
            decoder.decode(songId)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            null
        }

    /** Must be called while holding [mutex]. Writes [bytes] to a temp file and renames it to [target]. */
    private fun publish(target: File, bytes: ByteArray): Boolean {
        val temp = File(cacheDir, "${target.name}$TEMP_SUFFIX")
        temp.writeBytes(bytes)
        temp.setLastModified(System.currentTimeMillis())
        if (temp.renameTo(target)) return true
        temp.delete()
        return false
    }

    private fun pictureFile(ref: SongArtworkRef): File = File(cacheDir, "${ref.songId}-${ref.version}$PICTURE_SUFFIX")

    private fun markerFile(ref: SongArtworkRef): File = File(cacheDir, "${ref.songId}-${ref.version}$NONE_SUFFIX")

    /** Every file of [songId], any version, picture or marker. The `-` keeps song 1 from matching song 11. */
    private fun entriesOf(songId: Long): List<File> {
        val prefix = "$songId-"
        return cacheDir.listFiles()?.filter { it.name.startsWith(prefix) }.orEmpty()
    }

    /**
     * Must be called while holding [mutex]. Deletes files oldest-[File.lastModified]-first (write-age, since reads
     * never touch the stamp) until the directory is at or under [MAX_CACHE_BYTES], skipping [protectedFile] (the entry
     * [get] just wrote) even if clock skew makes it look oldest. Zero-byte negative markers are skipped: they cost no
     * budget, and dropping one would only make the next bind re-open its file.
     */
    private fun evictOldestWritten(protectedFile: File) {
        val files = cacheDir.listFiles()?.toList().orEmpty()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return

        val evictionCandidates = files
            .filter { it != protectedFile && it.length() > 0 }
            .sortedBy { it.lastModified() }
        for (candidate in evictionCandidates) {
            if (totalBytes <= MAX_CACHE_BYTES) break
            val size = candidate.length()
            if (candidate.delete()) totalBytes -= size
        }
    }

    /** `internal` so the eviction test sizes its payloads against [MAX_CACHE_BYTES] rather than a copied literal. */
    internal companion object {
        /** Ruling R2: the album cache's budget, now per track. */
        const val MAX_CACHE_BYTES = 32L * 1024 * 1024

        /** Suffix of a cached picture. */
        const val PICTURE_SUFFIX = ".jpg"

        /** Suffix of the zero-byte negative marker: the file has no embedded picture. */
        const val NONE_SUFFIX = ".none"

        /** Suffix of the in-progress file a miss writes before renaming it to its final name. */
        const val TEMP_SUFFIX = ".tmp"
    }
}
