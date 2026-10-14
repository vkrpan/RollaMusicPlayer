package com.rolla.musicplayer.core.database.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    private lateinit var dao: PlaybackQueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        dao = database.playbackQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(id: String, title: String): SongEntity = SongEntity(
        id = id,
        mediaStoreId = id.hashCode().toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "art-$id",
        dateModified = 1_000_000L,
        folderPath = "Music",
    )

    private fun state(currentSlot: Int, positionMs: Long, repeatMode: String = "ALL") = PlaybackStateEntity(
        currentSlot = currentSlot,
        positionMs = positionMs,
        shuffleEnabled = true,
        repeatMode = repeatMode,
        savedAt = 5_000L,
    )

    // Fixture: slot order (zulu, gone, alpha) ≠ title order ≠ insertion order (slot 2, 0, 1). Mutations:
    // (a) LEFT JOIN → JOIN → the missing slot disappears (size 2) → RED; (b) ORDER BY q.slot → ORDER BY s.title → the
    // order changes → RED; (c) select `q.song_id AS queued_song_id` from `s.id` instead → the missing row's songId is
    // null/"" → RED. Deleting ORDER BY q.slot is an equivalent mutant: slot is the rowid alias and the LEFT JOIN
    // scans q in rowid order. The clause stays because SQL guarantees no order without it; the test pins the
    // order-changing mutations (ORDER BY s.title, q.slot DESC, q.shuffle_rank).
    @Test
    fun loadQueue_returnsEverySlotInSlotOrderAndMarksMissingSongs() = runTest {
        songDao.upsertSongs(listOf(song("z", "Zulu"), song("a", "Alpha")))
        dao.replaceQueue(
            listOf(
                PlaybackQueueEntity(slot = 2, songId = "a", shuffleRank = 1),
                PlaybackQueueEntity(slot = 0, songId = "z", shuffleRank = 2),
                PlaybackQueueEntity(slot = 1, songId = "gone", shuffleRank = 0),
            ),
            state(currentSlot = 0, positionMs = 83_000L),
        )

        val rows = dao.loadQueue()

        assertEquals(listOf(0, 1, 2), rows.map { it.slot })
        assertEquals(listOf(2, 0, 1), rows.map { it.shuffleRank })
        assertEquals(listOf("z", "gone", "a"), rows.map { it.songId })
        assertEquals(listOf("Zulu", null, "Alpha"), rows.map { it.song?.title })
    }

    // Review Focus 1. Mutation: make song_id the primary key (and slot a plain column) → the second insert conflicts
    // → RED.
    @Test
    fun loadQueue_keepsDuplicateSongsInTheirOwnSlots() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha")))
        dao.replaceQueue(
            listOf(
                PlaybackQueueEntity(slot = 0, songId = "a", shuffleRank = 1),
                PlaybackQueueEntity(slot = 1, songId = "a", shuffleRank = 0),
            ),
            state(currentSlot = 1, positionMs = 1_000L),
        )

        assertEquals(listOf("Alpha", "Alpha"), dao.loadQueue().map { it.song?.title })
    }

    // Mutation: drop deleteQueue() from replaceQueue → the second insert hits the old slot 0 and fails with
    // SQLiteConstraintException (default @Insert ABORT) → RED.
    @Test
    fun replaceQueue_replacesTheWholeQueueAndTheState() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha"), song("b", "Bravo"), song("c", "Charlie")))
        dao.replaceQueue(
            listOf(
                PlaybackQueueEntity(0, "a", 0),
                PlaybackQueueEntity(1, "b", 1),
                PlaybackQueueEntity(2, "c", 2),
            ),
            state(currentSlot = 2, positionMs = 10L),
        )

        dao.replaceQueue(
            listOf(PlaybackQueueEntity(0, "c", 1), PlaybackQueueEntity(1, "a", 0)),
            state(currentSlot = 1, positionMs = 20L, repeatMode = "ONE"),
        )

        assertEquals(listOf("c", "a"), dao.loadQueue().map { it.songId })
        assertEquals(state(currentSlot = 1, positionMs = 20L, repeatMode = "ONE"), dao.loadState())
    }

    // Mutation: remove @Transaction from replaceQueue → the delete commits before the failing insert, so the first
    // queue is gone → RED.
    @Test
    fun replaceQueue_isAtomic_aFailedInsertKeepsThePreviousSave() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha"), song("b", "Bravo")))
        dao.replaceQueue(listOf(PlaybackQueueEntity(0, "a", 0)), state(currentSlot = 0, positionMs = 10L))

        try {
            dao.replaceQueue(
                listOf(PlaybackQueueEntity(0, "b", 0), PlaybackQueueEntity(0, "a", 1)),
                state(currentSlot = 0, positionMs = 99L),
            )
            fail("A duplicate slot must fail the insert")
        } catch (expected: SQLiteConstraintException) {
            // The transaction rolled back.
        }

        assertEquals(listOf("a"), dao.loadQueue().map { it.songId })
        assertEquals(10L, dao.loadState()?.positionMs)
    }

    // Mutation: `@Upsert` → `@Insert` on upsertState → the second call conflicts on id 0 → RED.
    @Test
    fun upsertState_keepsASingleRow() = runTest {
        dao.upsertState(state(currentSlot = 3, positionMs = 1L))
        dao.upsertState(state(currentSlot = 4, positionMs = 2L))

        assertEquals(state(currentSlot = 4, positionMs = 2L), dao.loadState())
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM playback_state").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    // Mutation: drop deleteState() from clear → the state row survives → RED.
    @Test
    fun clear_emptiesTheQueueAndTheState() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha")))
        dao.replaceQueue(listOf(PlaybackQueueEntity(0, "a", 0)), state(currentSlot = 0, positionMs = 1L))

        dao.clear()

        assertEquals(emptyList<Any>(), dao.loadQueue())
        assertNull(dao.loadState())
    }

    // Mutation: loadSnapshot returns PlaybackSnapshot(loadQueue(), null) → RED.
    // The single-transaction property (R8) of loadSnapshot and of clear is not observable in this single-threaded
    // test, and is accepted as unpinned.
    @Test
    fun loadSnapshot_readsTheQueueAndTheState() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha")))
        dao.replaceQueue(listOf(PlaybackQueueEntity(0, "a", 0)), state(currentSlot = 0, positionMs = 42L))

        val snapshot = dao.loadSnapshot()

        assertEquals(listOf("a"), snapshot.queue.map { it.songId })
        assertEquals(42L, snapshot.state?.positionMs)
    }
}
