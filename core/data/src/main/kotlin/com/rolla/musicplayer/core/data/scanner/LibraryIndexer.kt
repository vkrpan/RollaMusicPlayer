package com.rolla.musicplayer.core.data.scanner

import com.rolla.musicplayer.core.database.dao.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indexes the device's MediaStore audio into Room. [sync] and [syncSongs] are called from several
 * independent, uncoordinated sites -- Home's permission-grant scan (`HomeViewModel`), the settings
 * screen's manual "Rescan library" action, and the tag editor's post-save [syncSongs] -- any of
 * which can legitimately overlap in time (e.g. the user taps "Rescan library" while the initial
 * permission-grant scan is still running).
 *
 * Both entry points share a single [mutex], so **concurrent calls into this singleton serialize**:
 * a second call blocks (suspends, without holding a thread) until the first has fully committed its
 * upserts/deletes, then computes its own diff against that already-committed state rather than a
 * stale, concurrently-read snapshot. This is not needed for correctness of the terminal state --
 * every diff is (re)computed straight from MediaStore, so two interleaved full syncs still converge
 * on the same rows, and `deleteByMediaStoreIds` on an already-deleted id is a harmless no-op -- but
 * it removes the redundant duplicate scan/DB work of two overlapping full syncs, and closes a
 * narrow TOCTOU window: without it, a sync's `getAllSongs()` snapshot could be read before a
 * different in-flight call's play-count/favourite update commits, and that stale snapshot would
 * then flow through [ScannedSong.toEntity]'s merge for any row whose `dateModified` also changed in
 * that same window.
 */
@Singleton
class LibraryIndexer @Inject constructor(
    private val scanner: MediaScanner,
    private val songDao: SongDao,
) {

    private val mutex = Mutex()

    /**
     * Full-library incremental sync: inserts new rows, updates rows whose `dateModified` changed,
     * and deletes rows MediaStore no longer reports. Never wipes the table. Every upsert goes
     * through [ScannedSong.toEntity]'s merge so favourites/play-count/last-played/date-added
     * survive across a re-scan of a changed file.
     *
     * Safe to call while another [sync] or [syncSongs] is in flight -- see the class-level KDoc for
     * the serialization guarantee. Always runs on [Dispatchers.IO] regardless of the caller's
     * dispatcher.
     */
    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val scanned = scanner.scan()
            val scannedById = scanned.associateBy { it.mediaStoreId }
            val existing = songDao.getAllSongs().associateBy { it.mediaStoreId }

            val toUpsert = scanned
                .filter { song ->
                    val existingEntity = existing[song.mediaStoreId]
                    existingEntity == null || existingEntity.dateModified != song.dateModified
                }
                .map { it.toEntity(existing[it.mediaStoreId]) }

            val toDeleteIds = (existing.keys - scannedById.keys).toList()

            if (toUpsert.isNotEmpty()) songDao.upsertSongs(toUpsert)
            if (toDeleteIds.isNotEmpty()) songDao.deleteByMediaStoreIds(toDeleteIds)

            SyncResult(added = toUpsert.size, removed = toDeleteIds.size)
        }
    }

    /**
     * Targeted re-sync for a known-changed subset of MediaStore rows — e.g. after the tag editor
     * writes new tags and calls `MediaScannerConnection.scanFile` for just the files it touched.
     * Avoids a full library rescan.
     *
     * Unlike [sync], every id in [mediaStoreIds] that MediaStore still reports is upserted
     * *unconditionally* — this does NOT compare `dateModified` against the existing row.
     * `DATE_MODIFIED` only has second-granularity, so a tag write followed immediately by a rescan
     * can land inside the same second as the row's previous sync; a `dateModified != existing`
     * guard could then silently skip re-indexing the very file this call exists to refresh. A
     * targeted sync is only ever triggered because the caller already knows the file changed, so
     * that guard is both unnecessary and actively harmful here.
     *
     * User-state columns are preserved via the same [ScannedSong.toEntity] merge [sync] uses. Any
     * id present in [mediaStoreIds] but no longer present in MediaStore (file deleted/moved) is
     * removed from the library. An empty [mediaStoreIds] is a no-op that returns `SyncResult(0, 0)`
     * without touching the scanner or the DAO -- and without waiting on [mutex].
     *
     * Safe to call while another [sync] or [syncSongs] is in flight -- see the class-level KDoc for
     * the serialization guarantee. Always runs on [Dispatchers.IO] regardless of the caller's
     * dispatcher.
     */
    suspend fun syncSongs(mediaStoreIds: List<Long>): SyncResult {
        if (mediaStoreIds.isEmpty()) return SyncResult(added = 0, removed = 0)

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val scanned = scanner.scan(mediaStoreIds)
                val scannedIds = scanned.map { it.mediaStoreId }.toSet()
                val existing = songDao.getAllSongs().associateBy { it.mediaStoreId }

                val toUpsert = scanned.map { it.toEntity(existing[it.mediaStoreId]) }
                val toDeleteIds = mediaStoreIds.filter { it !in scannedIds }

                if (toUpsert.isNotEmpty()) songDao.upsertSongs(toUpsert)
                if (toDeleteIds.isNotEmpty()) songDao.deleteByMediaStoreIds(toDeleteIds)

                SyncResult(added = toUpsert.size, removed = toDeleteIds.size)
            }
        }
    }
}
