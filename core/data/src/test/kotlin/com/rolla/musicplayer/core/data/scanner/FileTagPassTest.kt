package com.rolla.musicplayer.core.data.scanner

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.entity.SongCustomTagCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.tagio.FileTags
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.io.IOException

/**
 * [FileTagPass] against [FakeCustomTagDao] (whose foreign-key throw needs Robolectric, as does `Log`). The file side
 * is mocked: [SongFileSource] hands `block` a `<songId>.mp3` file that never exists, and [RollaTagsFileIo.read] answers
 * from [fileTags] by that name.
 */
@RunWith(AndroidJUnit4::class)
class FileTagPassTest {

    private val fakeDao = FakeCustomTagDao()
    private val dao = RecordingCustomTagDao(fakeDao)
    private val songFileSource: SongFileSource = mockk()
    private val fileIo: RollaTagsFileIo = mockk()

    /** One virtual-time scheduler for the test body, the pass's IO work and its long-lived request scope. */
    private val dispatcher = StandardTestDispatcher()
    private val passScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val pass = FileTagPass(songFileSource, fileIo, dao, passScope, dispatcher)

    /** Per song id: the extension [SongFileSource.extensionOf] reports; absent means "mp3". */
    private val extensions = mutableMapOf<String, String?>()

    /** Per song id: what the song's file holds; absent means no field and no genre. */
    private val fileTags = mutableMapOf<String, FileTags>()

    /** Song ids whose file cannot be opened. */
    private val unreadable = mutableSetOf<String>()

    @Before
    fun setUp() {
        coEvery { songFileSource.extensionOf(any()) } coAnswers {
            val id = firstArg<SongEntity>().id
            if (id in extensions) extensions[id] else "mp3"
        }
        coEvery { songFileSource.withReadableFile<Any?>(any(), any()) } coAnswers {
            val song = firstArg<SongEntity>()
            if (song.id in unreadable) throw IOException("cannot open ${song.id}")
            secondArg<(File) -> Any?>().invoke(File("${song.id}.mp3"))
        }
        every { fileIo.read(any()) } answers {
            fileTags[firstArg<File>().nameWithoutExtension] ?: FileTags(CustomTagIds.EMPTY, genre = null)
        }
    }

    @Test
    fun pass_readsUnreadRowsAndAppliesIdsAndGenre() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = "Pop")))
        fileTags["1"] = FileTags(CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()), genre = "Techno")

        val result = pass.run()

        assertEquals(setOf(SongCustomTagCrossRef("1", CustomTagKind.TAG, 2L)), fakeDao.linksOf("1"))
        assertEquals("Techno", fakeDao.song("1")?.genre)
        assertTrue(fakeDao.song("1")!!.fileTagsRead)
        assertEquals(1, result.read)
        assertEquals(0, result.failed)
    }

    @Test
    fun pass_fileWithoutGenre_keepsMediaStoreGenre() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = "Pop")))
        fileTags["1"] = FileTags(CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()), genre = null)

        pass.run()

        assertEquals("Pop", fakeDao.song("1")?.genre)
    }

    @Test
    fun pass_unsupportedFormat_appliesEmptyWithoutOpeningFile() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = "Pop")))
        extensions["1"] = "wav"

        pass.run()

        coVerify(exactly = 0) { songFileSource.withReadableFile<Any?>(any(), any()) }
        assertEquals(emptySet<SongCustomTagCrossRef>(), fakeDao.linksOf("1"))
        assertEquals("Pop", fakeDao.song("1")?.genre)
        assertTrue(fakeDao.song("1")!!.fileTagsRead)
    }

    @Test
    fun pass_undecodableValue_linksNothingAndMarksRead() = runTest(dispatcher) {
        // RollaTagsFileIo.read turns an undecodable field into EMPTY; the pass must still store that and set the flag.
        fakeDao.seedSongs(listOf(unreadSong("1", genre = "Pop")))
        fileTags["1"] = FileTags(CustomTagIds.EMPTY, genre = null)

        pass.run()

        assertEquals(emptySet<SongCustomTagCrossRef>(), fakeDao.linksOf("1"))
        assertTrue("the read flag is set", fakeDao.song("1")!!.fileTagsRead)
    }

    @Test
    fun pass_readThrows_keepsOldLinksMarksReadAndContinues() = runTest(dispatcher) {
        // Song 1 carries links and a genre from an earlier read; its file has changed since and now cannot be opened.
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null, dateModified = 2_000L)))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(3L), subTagIds = emptySet()), genre = "House")
        fakeDao.seedSongs(listOf(fakeDao.song("1")!!.copy(fileTagsRead = false)))
        fakeDao.seedSongs(listOf(unreadSong("2", genre = null, dateModified = 1_000L)))
        unreadable += "1"
        fileTags["2"] = FileTags(CustomTagIds(tagIds = setOf(4L), subTagIds = emptySet()), genre = "Techno")

        val result = pass.run()

        assertEquals(setOf(SongCustomTagCrossRef("1", CustomTagKind.TAG, 3L)), fakeDao.linksOf("1"))
        assertEquals("House", fakeDao.song("1")?.genre)
        assertTrue("the failed row is marked read", fakeDao.song("1")!!.fileTagsRead)
        assertEquals(setOf(SongCustomTagCrossRef("2", CustomTagKind.TAG, 4L)), fakeDao.linksOf("2"))
        assertEquals(FileTagPassResult(read = 1, failed = 1, elapsedMs = result.elapsedMs), result)
    }

    // jaudiotagger can overflow the stack on a corrupt file. Escaping, it would leave the row unread, so every later
    // sync would hit it again.
    @Test
    fun pass_readThrowsError_marksRowReadAndContinues() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null, dateModified = 2_000L)))
        fakeDao.seedSongs(listOf(unreadSong("2", genre = null, dateModified = 1_000L)))
        fileTags["2"] = FileTags(CustomTagIds(tagIds = setOf(4L), subTagIds = emptySet()), genre = null)
        coEvery { songFileSource.withReadableFile<Any?>(match { it.id == "1" }, any()) } throws
            StackOverflowError("corrupt frame")

        val result = pass.run()

        assertTrue("the failed row is marked read", fakeDao.song("1")!!.fileTagsRead)
        assertEquals(setOf(SongCustomTagCrossRef("2", CustomTagKind.TAG, 4L)), fakeDao.linksOf("2"))
        assertEquals(FileTagPassResult(read = 1, failed = 1, elapsedMs = result.elapsedMs), result)
    }

    @Test
    fun pass_loopsBatchesUntilNoneLeft() = runTest(dispatcher) {
        val songs = (1..120).map { unreadSong(it.toString(), genre = null, dateModified = it.toLong()) }
        fakeDao.seedSongs(songs)
        songs.forEach { fileTags[it.id] = FileTags(CustomTagIds(setOf(it.mediaStoreId), emptySet()), genre = null) }

        val result = pass.run()

        assertEquals(120, songs.count { fakeDao.linksOf(it.id).isNotEmpty() })
        assertEquals(120, result.read)
        assertEquals(listOf(50, 50, 20, 0), dao.batchSizes)
    }

    @Test
    fun pass_cancellationPropagates() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = "Pop")))
        every { fileIo.read(any()) } throws CancellationException("scan cancelled")

        val thrown = try {
            pass.run()
            null
        } catch (e: CancellationException) {
            e
        }

        assertNotNull("run rethrows the cancellation", thrown)
        assertFalse("the row is not marked read", fakeDao.song("1")!!.fileTagsRead)
    }

    @Test
    fun pass_logsOneSummaryLineOnlyWhenItTouchedFiles() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null, dateModified = 2_000L)))
        fakeDao.seedSongs(listOf(unreadSong("2", genre = null, dateModified = 1_000L)))
        unreadable += "1"

        pass.run()
        pass.run()

        val messages = ShadowLog.getLogsForTag("FileTagPass").map { it.msg }
        assertEquals(1, messages.size)
        assertTrue(messages.single(), Regex("read 1 files \\(1 failed\\) in \\d+ ms").matches(messages.single()))
    }

    // ── request(): the fire-and-forget entry point LibraryIndexer uses ─────

    @Test
    fun request_whenIdle_runsThePass() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null)))
        fileTags["1"] = FileTags(CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()), genre = "Techno")

        pass.request()
        advanceUntilIdle()

        assertEquals(setOf(SongCustomTagCrossRef("1", CustomTagKind.TAG, 2L)), fakeDao.linksOf("1"))
        assertTrue(fakeDao.song("1")!!.fileTagsRead)
    }

    @Test
    fun request_whileRunning_coalescesIntoOneMoreRun() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { songFileSource.withReadableFile<Any?>(any(), any()) } coAnswers {
            gate.await()
            secondArg<(File) -> Any?>().invoke(File("${firstArg<SongEntity>().id}.mp3"))
        }
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null)))

        pass.request()
        advanceUntilIdle() // the first run is now blocked on the gate, inside song 1's read
        repeat(3) { pass.request() }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        // Every run ends with one empty query: the first run reads song 1, the one extra run finds nothing left.
        assertEquals(listOf(1, 0, 0), dao.batchSizes)
    }

    @Test
    fun request_unexpectedFailure_isLoggedAndLaterRequestsStillRun() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null)))
        dao.failNextQueryWith = IllegalStateException("database is locked")

        pass.request()
        advanceUntilIdle()
        val failure = ShadowLog.getLogsForTag("FileTagPass").single()
        pass.request()
        advanceUntilIdle()

        assertEquals(Log.ERROR, failure.type)
        assertEquals("database is locked", failure.throwable?.message)
        assertTrue("the later request still read the file", fakeDao.song("1")!!.fileTagsRead)
    }

    // An Error is not an Exception: escaping the worker's launch, it would reach the scope's handler, or kill the app.
    @Test
    fun request_errorFromTheDao_isLoggedAndLaterRequestsStillRun() = runTest(dispatcher) {
        fakeDao.seedSongs(listOf(unreadSong("1", genre = null)))
        dao.failNextQueryWith = StackOverflowError("cursor recursion")

        pass.request()
        advanceUntilIdle()
        val failure = ShadowLog.getLogsForTag("FileTagPass").single()
        pass.request()
        advanceUntilIdle()

        assertEquals(Log.ERROR, failure.type)
        assertTrue(failure.throwable is StackOverflowError)
        assertTrue("the later request still read the file", fakeDao.song("1")!!.fileTagsRead)
    }

    @After
    fun tearDown() {
        passScope.cancel()
    }
}

/**
 * Delegates to [delegate], recording the size of every [songsNeedingFileTagRead] answer. After [maxQueries] queries it
 * answers empty, so a pass that never marks a row read ends instead of looping forever. [failNextQueryWith] makes the
 * next query throw it, as a failure outside the per-row catch would.
 */
private class RecordingCustomTagDao(
    private val delegate: FakeCustomTagDao,
    private val maxQueries: Int = 10,
) : CustomTagDao by delegate {

    val batchSizes = mutableListOf<Int>()
    var failNextQueryWith: Throwable? = null

    override suspend fun songsNeedingFileTagRead(limit: Int): List<SongEntity> {
        failNextQueryWith?.let { failure ->
            failNextQueryWith = null
            throw failure
        }
        val batch = if (batchSizes.size < maxQueries) delegate.songsNeedingFileTagRead(limit) else emptyList()
        batchSizes += batch.size
        return batch
    }
}

private fun unreadSong(id: String, genre: String?, dateModified: Long = 1_000L): SongEntity = SongEntity(
    id = id,
    mediaStoreId = id.toLong(),
    title = "Title $id",
    artist = "Artist",
    album = "Album",
    albumId = 10L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/$id",
    artworkUri = "rolla-artwork://song/$id?v=$dateModified",
    dateModified = dateModified,
    folderPath = "Music",
    genre = genre,
    fileTagsRead = false,
)
