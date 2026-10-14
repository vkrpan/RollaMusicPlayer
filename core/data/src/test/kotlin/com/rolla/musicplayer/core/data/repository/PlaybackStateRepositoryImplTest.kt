package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.PlaybackSnapshot
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlaybackState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateRepositoryImplTest {

    private val dao = mockk<PlaybackQueueDao>(relaxed = true)
    private val repository = PlaybackStateRepositoryImpl(dao)
    private val state = SavedPlaybackState(
        currentIndex = 2,
        positionMs = 83_000L,
        shuffleEnabled = true,
        repeatMode = RepeatMode.ALL,
    )

    // shuffleOrder [2,0,1] is not its own inverse: ranks are slot0→1, slot1→2, slot2→0. Mutation: rank = shuffleOrder[slot]
    // (the inverse mix-up) → ranks [2,0,1] → RED.
    @Test
    fun saveQueue_writesEachSlotWithItsShuffleRankAndTheState() = runTest {
        val rows = slot<List<PlaybackQueueEntity>>()
        val written = slot<PlaybackStateEntity>()
        coEvery { dao.replaceQueue(capture(rows), capture(written)) } returns Unit

        repository.saveQueue(listOf("a", "b", "c"), listOf(2, 0, 1), state)

        assertEquals(
            listOf(PlaybackQueueEntity(0, "a", 1), PlaybackQueueEntity(1, "b", 2), PlaybackQueueEntity(2, "c", 0)),
            rows.captured,
        )
        assertEquals(2, written.captured.currentSlot)
        assertEquals(83_000L, written.captured.positionMs)
        assertEquals(true, written.captured.shuffleEnabled)
        assertEquals("ALL", written.captured.repeatMode)
    }

    // [0,0] is not a permutation of [0,1]. Mutation: delete the `require` → the save goes through with ranks [1,0] →
    // nothing is thrown and replaceQueue is called → RED.
    @Test
    fun saveQueue_rejectsAShuffleOrderThatIsNotAPermutation() = runTest {
        val failure = runCatching { repository.saveQueue(listOf("a", "b"), listOf(0, 0), state) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)
        coVerify(exactly = 0) { dao.replaceQueue(any(), any()) }
    }

    // The saved state has shuffle off, unlike the fixture's default (on). Mutations: write repeatMode = "OFF" → RED;
    // write shuffleEnabled = true as a constant → RED.
    @Test
    fun saveState_upsertsTheStateRow() = runTest {
        val written = slot<PlaybackStateEntity>()
        coEvery { dao.upsertState(capture(written)) } returns Unit

        repository.saveState(state.copy(shuffleEnabled = false, repeatMode = RepeatMode.ONE))

        assertEquals("ONE", written.captured.repeatMode)
        assertEquals(false, written.captured.shuffleEnabled)
        assertEquals(PlaybackStateEntity.SINGLE_ROW_ID, written.captured.id)
    }

    // Mutation: drop the dao.clear() call in load → RED.
    @Test
    fun load_whenNoSongSurvives_returnsNullAndClears() = runTest {
        coEvery { dao.loadSnapshot() } returns PlaybackSnapshot(
            queue = listOf(QueuedSongRow(0, 0, "gone", null)),
            state = PlaybackStateEntity(
                currentSlot = 0,
                positionMs = 1L,
                shuffleEnabled = false,
                repeatMode = "OFF",
                savedAt = 0L,
            ),
        )

        assertNull(repository.load())
        coVerify(exactly = 1) { dao.clear() }
    }

    // The queue holds a surviving song, so only the missing state row can make the result null (an empty queue would
    // give null either way). Mutation: treat a missing state as "restore with defaults" (state ?: default entity) → a
    // non-null result → RED.
    @Test
    fun load_withoutAStateRow_returnsNull() = runTest {
        val survivor = SongEntity(
            id = "s0",
            mediaStoreId = 1L,
            title = "Title",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 1_000L,
            trackNumber = null,
            year = null,
            contentUri = "content://media/external/audio/media/1",
            artworkUri = "",
            dateModified = 0L,
        )
        coEvery { dao.loadSnapshot() } returns PlaybackSnapshot(
            queue = listOf(QueuedSongRow(0, 0, "s0", survivor)),
            state = null,
        )

        assertNull(repository.load())
    }

    // Controller ruling P1: a failed read keeps the saved queue. Mutation: wrap the read in runCatching that clears on
    // failure (`runCatching { dao.loadSnapshot() }.onFailure { dao.clear() }.getOrThrow()`) → clear() called → RED.
    @Test
    fun load_whenTheReadFails_propagatesAndDoesNotClear() = runTest {
        coEvery { dao.loadSnapshot() } throws IllegalStateException("database is locked")

        val failure = runCatching { repository.load() }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $failure", failure is IllegalStateException)
        coVerify(exactly = 0) { dao.clear() }
    }
}
