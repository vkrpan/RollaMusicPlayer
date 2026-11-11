package com.rolla.musicplayer.core.data.scanner

import android.util.Log
import com.rolla.musicplayer.core.data.di.CoreDataIoDispatcher
import com.rolla.musicplayer.core.data.di.FileTagPassScope
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.tagio.isCustomTagExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

/**
 * What one [FileTagPass.run] did: files read and stored, files that failed, and how long it took. A read dropped
 * because the row was reset meanwhile (a skipped row) counts in neither; the row is read again by this run's next
 * query, and that read counts.
 */
data class FileTagPassResult(val read: Int, val failed: Int, val elapsedMs: Long)

/**
 * Reads each song's custom tag IDs (`ROLLA_TAGS`) and genre from its own file into Room, for every row whose read flag
 * ([SongEntity.fileTagsRead]) is unset: rows the scanner added or found changed, and the whole library once after the
 * v6 migration. [LibraryIndexer] calls [request] after each sync and does not wait for it.
 *
 * Works in batches of [BATCH_SIZE], newest file first, until no unread row is left. Per row:
 * - a format without the field (any extension but MP3, FLAC or M4A, or none) stores no links and keeps the row's
 *   MediaStore genre, without opening the file;
 * - otherwise it stores the file's IDs, replacing the row's links, and the file's genre, falling back to the row's
 *   MediaStore genre when the file has none;
 * - a failure (a missing, unreadable or corrupt file) sets only the read flag, so the row keeps the links and genre it
 *   had, and the pass moves on. The file is read again once the scanner sees it change;
 * - either write applies only while the row holds the [SongEntity.fileTagsGeneration] it was queried at. A row the
 *   scanner reset during the read (a save to the same file) is left unread, counted neither read nor failed, and read
 *   again by this run's next query, so a read of the old file never overwrites the save.
 *
 * Cancellation is never swallowed: the row being read stays unread and the next pass reads it. Its own [mutex]
 * serialises overlapping calls; it never diffs MediaStore, so it runs outside [LibraryIndexer]'s lock. Always runs on
 * [ioDispatcher].
 */
@Singleton
class FileTagPass @Inject constructor(
    private val songFileSource: SongFileSource,
    private val fileIo: RollaTagsFileIo,
    private val customTagDao: CustomTagDao,
    @FileTagPassScope private val scope: CoroutineScope,
    @CoreDataIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    /**
     * Requests waiting for a run. Conflated, so any number of them made while a run is active collapse into the one
     * waiting element, and that makes exactly one more run.
     */
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            while (true) {
                requests.receive()
                runReportingFailures()
            }
        }
    }

    /**
     * Asks for a run on [scope] and returns at once. When idle, a run starts. While a run is active, one more run
     * follows it, however many requests arrive meanwhile, so rows that a post-save `syncSongs` resets during a long
     * first read are read without waiting for the next sync.
     */
    fun request() {
        requests.trySend(Unit)
    }

    suspend fun run(): FileTagPassResult = mutex.withLock {
        withContext(ioDispatcher) {
            val start = TimeSource.Monotonic.markNow()
            var read = 0
            var failed = 0
            while (true) {
                val batch = customTagDao.songsNeedingFileTagRead(limit = BATCH_SIZE)
                if (batch.isEmpty()) break
                batch.forEach { row ->
                    when (readRow(row)) {
                        RowOutcome.READ -> read++
                        RowOutcome.FAILED -> failed++
                        RowOutcome.SKIPPED -> Unit
                    }
                }
            }
            val elapsedMs = start.elapsedNow().inWholeMilliseconds
            if (read + failed > 0) Log.i(TAG, "read $read files ($failed failed) in $elapsedMs ms")
            FileTagPassResult(read = read, failed = failed, elapsedMs = elapsedMs)
        }
    }

    /**
     * One [run] for [request]: a failure outside the per-row catch (a database error, say) is logged and the worker
     * keeps serving later requests. Cancellation of [scope] still ends it. A [CancellationException] thrown from inside
     * the run while the worker's own job is still active is not a cancellation of the worker, so it is logged like any
     * other failure rather than ending every later request. An [Error] is caught too: escaping this worker's
     * `launch`, it would otherwise end the worker, or the process.
     */
    private suspend fun runReportingFailures() {
        try {
            run()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "file tag pass stopped", e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            Log.e(TAG, "file tag pass failed", e)
        }
    }

    /**
     * Stores what [row]'s file holds: [RowOutcome.READ], or [RowOutcome.FAILED] when the file could not be read (the
     * row is then only flagged). Both writes carry the row's generation as queried; when the scanner reset the row
     * meanwhile (a save, a changed file) the write is dropped and the outcome is [RowOutcome.SKIPPED]: the row is
     * still unread, so [run]'s next query returns it and the new file is read.
     */
    private suspend fun readRow(row: SongEntity): RowOutcome = try {
        val extension = songFileSource.extensionOf(row)
        val applied = if (extension == null || !isCustomTagExtension(extension)) {
            customTagDao.applyFileTags(row.id, CustomTagIds.EMPTY, row.genre, row.fileTagsGeneration)
        } else {
            val fileTags = songFileSource.withReadableFile(row) { file -> fileIo.read(file) }
            customTagDao.applyFileTags(
                row.id,
                fileTags.customTagIds,
                fileTags.genre ?: row.genre,
                row.fileTagsGeneration,
            )
        }
        if (applied) RowOutcome.READ else RowOutcome.SKIPPED
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") ignored: Throwable) {
        // jaudiotagger throws its own checked exceptions as well as IO and runtime ones for a bad file, and a corrupt
        // file can overflow its stack. An Error is caught as well: left unread, the row would fail every later pass
        // again. Counted, not logged per file: the pass logs one summary line.
        if (customTagDao.markFileTagsRead(row.id, row.fileTagsGeneration)) RowOutcome.FAILED else RowOutcome.SKIPPED
    }

    /** What [readRow] did with one row; [run] counts READ and FAILED, and a SKIPPED row comes back in a later query. */
    private enum class RowOutcome { READ, FAILED, SKIPPED }

    private companion object {
        const val TAG = "FileTagPass"
        const val BATCH_SIZE = 50
    }
}
