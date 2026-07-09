package com.rolla.musicplayer.core.data.scanner

import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LibraryIndexer] -- both the full [LibraryIndexer.sync] and the targeted
 * [LibraryIndexer.syncSongs] used by the tag editor's post-save re-index.
 *
 * [MediaScanner] is mocked (its real implementation needs a ContentResolver); [SongDao] is mocked
 * rather than faked because several of these tests assert on which DAO calls happen at all (e.g.
 * "unchanged rows are NOT re-upserted", "an empty targeted sync never touches the DAO") -- a
 * state-based fake cannot distinguish a skipped upsert from an upsert that wrote identical rows.
 */
class LibraryIndexerTest {

    private val scanner: MediaScanner = mockk()
    private val songDao: SongDao = mockk(relaxed = true)
    private val indexer = LibraryIndexer(scanner, songDao)

    // ── sync ──────────────────────────────────────────────────────────────

    @Test
    fun sync_newSong_insertsWithFreshDateAddedAndDefaultUserState() = runTest {
        coEvery { scanner.scan() } returns listOf(scannedSong(mediaStoreId = 1L))
        coEvery { songDao.getAllSongs() } returns emptyList()

        val result = indexer.sync()

        assertEquals(SyncResult(added = 1, removed = 0), result)
        coVerify(exactly = 1) {
            songDao.upsertSongs(
                match { entities ->
                    val entity = entities.single()
                    entity.mediaStoreId == 1L &&
                        entity.dateAdded > 0L &&
                        !entity.isFavorite &&
                        entity.playCount == 0 &&
                        entity.lastPlayed == null
                },
            )
        }
    }

    @Test
    fun sync_changedSong_updatesTagsButPreservesUserState() = runTest {
        val existing = entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED)
        coEvery { scanner.scan() } returns
            listOf(scannedSong(mediaStoreId = 1L, title = "Retitled", dateModified = NEW_DATE_MODIFIED))
        coEvery { songDao.getAllSongs() } returns listOf(existing)

        indexer.sync()

        coVerify(exactly = 1) { songDao.upsertSongs(match { it.single().preservesUserStateOf(existing, "Retitled") }) }
    }

    @Test
    fun sync_unchangedSong_isNotReUpserted() = runTest {
        coEvery { scanner.scan() } returns listOf(scannedSong(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED))
        coEvery { songDao.getAllSongs() } returns
            listOf(entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED))

        val result = indexer.sync()

        assertEquals(SyncResult(added = 0, removed = 0), result)
        coVerify(exactly = 0) { songDao.upsertSongs(any()) }
    }

    @Test
    fun sync_songMissingFromMediaStore_isDeleted() = runTest {
        coEvery { scanner.scan() } returns emptyList()
        coEvery { songDao.getAllSongs() } returns
            listOf(entityWithUserState(mediaStoreId = 7L, dateModified = OLD_DATE_MODIFIED))

        val result = indexer.sync()

        assertEquals(SyncResult(added = 0, removed = 1), result)
        coVerify(exactly = 1) { songDao.deleteByMediaStoreIds(listOf(7L)) }
    }

    // ── concurrent invocation safety ───────────────────────────────────────

    /**
     * Regression test for the manual "Rescan library" entry point overlapping an already in-flight
     * [LibraryIndexer.sync] (e.g. the library screen's permission-grant scan). The two calls must
     * serialize rather than interleave: the second call's [SongDao.getAllSongs] snapshot must only
     * be read after the first call has fully finished (including its DAO writes), never while the
     * first call still holds the lock.
     */
    @Test
    fun sync_concurrentInvocations_areSerializedByAnInternalMutex() = runTest {
        val callOrder = mutableListOf<String>()
        val secondCallReady = CompletableDeferred<Unit>()
        var callCount = 0

        coEvery { scanner.scan() } coAnswers {
            callCount++
            if (callCount == 1) {
                callOrder += "first-start"
                // Let the second sync() attempt to acquire the lock while this call still holds it.
                secondCallReady.complete(Unit)
                delay(50)
                callOrder += "first-end"
            } else {
                callOrder += "second-start"
                callOrder += "second-end"
            }
            listOf(scannedSong(mediaStoreId = 1L))
        }
        coEvery { songDao.getAllSongs() } returns emptyList()

        val firstJob = launch { indexer.sync() }
        val secondJob = launch {
            secondCallReady.await()
            indexer.sync()
        }

        firstJob.join()
        secondJob.join()

        assertEquals(listOf("first-start", "first-end", "second-start", "second-end"), callOrder)
    }

    /** Same serialization guarantee, but for a [LibraryIndexer.sync] overlapping a [LibraryIndexer.syncSongs]. */
    @Test
    fun syncAndSyncSongs_concurrentInvocations_areSerializedByAnInternalMutex() = runTest {
        val callOrder = mutableListOf<String>()
        val secondCallReady = CompletableDeferred<Unit>()

        coEvery { scanner.scan() } coAnswers {
            callOrder += "sync-start"
            secondCallReady.complete(Unit)
            delay(50)
            callOrder += "sync-end"
            listOf(scannedSong(mediaStoreId = 1L))
        }
        coEvery { scanner.scan(listOf(2L)) } coAnswers {
            callOrder += "syncSongs-start"
            callOrder += "syncSongs-end"
            listOf(scannedSong(mediaStoreId = 2L))
        }
        coEvery { songDao.getAllSongs() } returns emptyList()

        val firstJob = launch { indexer.sync() }
        val secondJob = launch {
            secondCallReady.await()
            indexer.syncSongs(listOf(2L))
        }

        firstJob.join()
        secondJob.join()

        assertEquals(listOf("sync-start", "sync-end", "syncSongs-start", "syncSongs-end"), callOrder)
    }

    // ── syncSongs (targeted re-sync after a tag write) ────────────────────

    @Test
    fun syncSongs_unchangedDateModified_isStillReUpserted() = runTest {
        // The whole point of the targeted path: DATE_MODIFIED has second granularity, so a tag
        // write can land in the same second as the previous sync -- the row must refresh anyway.
        coEvery { scanner.scan(listOf(1L)) } returns
            listOf(scannedSong(mediaStoreId = 1L, title = "Retitled", dateModified = OLD_DATE_MODIFIED))
        coEvery { songDao.getAllSongs() } returns
            listOf(entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED))

        val result = indexer.syncSongs(listOf(1L))

        assertEquals(SyncResult(added = 1, removed = 0), result)
        coVerify(exactly = 1) { songDao.upsertSongs(match { it.single().title == "Retitled" }) }
    }

    @Test
    fun syncSongs_preservesUserStateAcrossTheReSync() = runTest {
        val existing = entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED)
        coEvery { scanner.scan(listOf(1L)) } returns
            listOf(scannedSong(mediaStoreId = 1L, title = "Retitled", dateModified = NEW_DATE_MODIFIED))
        coEvery { songDao.getAllSongs() } returns listOf(existing)

        indexer.syncSongs(listOf(1L))

        coVerify(exactly = 1) { songDao.upsertSongs(match { it.single().preservesUserStateOf(existing, "Retitled") }) }
    }

    @Test
    fun syncSongs_idNoLongerInMediaStore_isDeleted() = runTest {
        coEvery { scanner.scan(listOf(1L, 2L)) } returns listOf(scannedSong(mediaStoreId = 1L))
        coEvery { songDao.getAllSongs() } returns emptyList()

        val result = indexer.syncSongs(listOf(1L, 2L))

        assertEquals(SyncResult(added = 1, removed = 1), result)
        coVerify(exactly = 1) { songDao.deleteByMediaStoreIds(listOf(2L)) }
    }

    @Test
    fun syncSongs_emptyIds_isNoOpWithoutTouchingScannerOrDao() = runTest {
        val result = indexer.syncSongs(emptyList())

        assertEquals(SyncResult(added = 0, removed = 0), result)
        coVerify(exactly = 0) { scanner.scan(any()) }
        coVerify(exactly = 0) { songDao.getAllSongs() }
        coVerify(exactly = 0) { songDao.upsertSongs(any()) }
        coVerify(exactly = 0) { songDao.deleteByMediaStoreIds(any()) }
    }

    private fun SongEntity.preservesUserStateOf(existing: SongEntity, expectedTitle: String): Boolean {
        assertEquals(expectedTitle, title)
        assertEquals(existing.dateAdded, dateAdded)
        assertEquals(existing.isFavorite, isFavorite)
        assertEquals(existing.playCount, playCount)
        assertEquals(existing.lastPlayed, lastPlayed)
        assertTrue(isFavorite)
        return true
    }
}

private const val OLD_DATE_MODIFIED = 1_000L
private const val NEW_DATE_MODIFIED = 2_000L

private fun scannedSong(
    mediaStoreId: Long,
    title: String = "Title $mediaStoreId",
    dateModified: Long = NEW_DATE_MODIFIED,
): ScannedSong = ScannedSong(
    mediaStoreId = mediaStoreId,
    title = title,
    artist = "Artist",
    album = "Album",
    albumId = 10L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/$mediaStoreId",
    artworkUri = "content://media/external/audio/albumart/10",
    dateModified = dateModified,
)

private fun entityWithUserState(mediaStoreId: Long, dateModified: Long): SongEntity = SongEntity(
    id = mediaStoreId.toString(),
    mediaStoreId = mediaStoreId,
    title = "Title $mediaStoreId",
    artist = "Artist",
    album = "Album",
    albumId = 10L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/$mediaStoreId",
    artworkUri = "content://media/external/audio/albumart/10",
    dateModified = dateModified,
    dateAdded = 500L,
    isFavorite = true,
    playCount = 4,
    lastPlayed = 900L,
)
