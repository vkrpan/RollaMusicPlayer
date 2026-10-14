package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepositoryImpl
import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.media.RestoreCoordinator.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Final-review I1: the restore, the persister and the repository on one real player. The saved queue and the state
 * written after a restore must stay in one numbering, even when the restore dropped a song that left the library.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
class QueueRestoreRoundTripTest {

    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder

    // B has left the library since the save; A, C and D survive.
    private val dao = InMemoryPlaybackQueueDao(library = listOf("sA", "sC", "sD").associateWith(::entity))
    private val repository = PlaybackStateRepositoryImpl(dao)

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
    }

    @After
    fun tearDown() = player.release()

    private fun TestScope.settle() {
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    // The save holds A,B,C,D (slots 0-3; ranks [1,3,0,2] differ from slot order, shuffle off) at A, 30 s in. The
    // restore gives A,C,D; the user moves to D (index 2) at 45 s and the service stops. The next load must name D.
    // Mutation: drop the enqueue in QueuePersister.enable() → the old rows A,B,C,D stay, slot 2 resolves to C → RED.
    @Test
    fun aRestoreWithAMissingSong_savesStatesAgainstTheRestoredQueue() = runTest {
        dao.queue = listOf("sA", "sB", "sC", "sD").zip(listOf(1, 3, 0, 2)).mapIndexed { slot, (songId, rank) ->
            PlaybackQueueEntity(slot, songId, rank)
        }
        dao.state = PlaybackStateEntity(
            currentSlot = 0,
            positionMs = 30_000L,
            shuffleEnabled = false,
            repeatMode = "OFF",
            savedAt = 0L,
        )
        val persister = QueuePersister(player, repository, backgroundScope) { 0L }
        player.addListener(persister)
        val coordinator =
            RestoreCoordinator(repository, backgroundScope, StandardTestDispatcher(testScheduler), persister::enable)

        coordinator.restoreInto(player, restoredOrder, PlaybackStateHolder())
        settle()
        assertEquals(Outcome.APPLIED, coordinator.outcome)
        val restoredIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        assertEquals(listOf("sA", "sC", "sD"), restoredIds)

        player.seekTo(2, 45_000L)
        settle()
        persister.saveFinal()
        settle()

        val saved = repository.load()!!
        assertEquals("sD", saved.songs[saved.state.currentIndex].id)
        assertEquals(45_000L, saved.state.positionMs)
    }

    private fun entity(id: String) = SongEntity(
        id = id,
        mediaStoreId = 1L,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/${id.hashCode()}",
        artworkUri = "",
        dateModified = 0L,
    )

    /** The DAO's tables in memory; [loadQueue] is the LEFT JOIN against [library] (a missing song reads as null). */
    private class InMemoryPlaybackQueueDao(private val library: Map<String, SongEntity>) : PlaybackQueueDao {
        var queue: List<PlaybackQueueEntity> = emptyList()
        var state: PlaybackStateEntity? = null

        override suspend fun loadQueue(): List<QueuedSongRow> =
            queue.sortedBy { it.slot }.map { QueuedSongRow(it.slot, it.shuffleRank, it.songId, library[it.songId]) }

        override suspend fun loadState(): PlaybackStateEntity? = state

        override suspend fun upsertState(state: PlaybackStateEntity) {
            this.state = state
        }

        override suspend fun insertQueue(rows: List<PlaybackQueueEntity>) {
            queue = queue + rows
        }

        override suspend fun deleteQueue() {
            queue = emptyList()
        }

        override suspend fun deleteState() {
            state = null
        }
    }
}
