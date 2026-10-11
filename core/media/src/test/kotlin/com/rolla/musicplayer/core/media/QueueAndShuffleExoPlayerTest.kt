package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class QueueAndShuffleExoPlayerTest {

    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(n: Int) = (0 until n).map {
        MediaItem.Builder().setMediaId("id$it").setUri("content://media/external/audio/media/$it")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("T$it").setArtist("A$it").build()).build()
    }

    // Mutation: read titles from the wrong window (e.g. `index + 1`) → RED.
    @Test
    fun shuffleOffQueueIsTimelineOrderWithCurrentPosition() {
        player.setMediaItems(items(5), 2, C.TIME_UNSET)
        idle()
        val queue = player.queueState()
        assertEquals(listOf(0, 1, 2, 3, 4), queue.items.map { it.index })
        assertEquals(2, queue.currentIndex)
        assertEquals("T2", queue.items[2].title)
    }

    // Mutation: StartFirstShuffleEnforcer.onMediaItemTransition ignores PLAYLIST_CHANGED → start item not first → RED.
    @Test
    fun enforcedShuffleQueueStartsAtTheCurrentItemAndHoldsEveryItem() {
        // Media3's default order is unseeded; without this pin the mutation above survived 2 of 8 runs (item 3 first by
        // chance). Seed 1 makes Media3's own order start elsewhere, so the kill is deterministic.
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(6), 3, C.TIME_UNSET)
        idle()
        val queue = player.queueState()
        assertEquals(3, queue.items.first().index)
        assertEquals(0, queue.currentIndex)
        assertEquals((0 until 6).toSet(), queue.items.map { it.index }.toSet())
    }

    // Mutation: the enforcer reacts to every transition reason → the seek re-pins the order → RED (ruling 4).
    @Test
    fun skippingInsideAShuffledQueueKeepsTheOrder() {
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(6), 0, C.TIME_UNSET)
        idle()
        val before = player.queueState().items.map { it.index }
        player.seekToDefaultPosition(before[3])
        idle()
        val after = player.queueState()
        assertEquals(before, after.items.map { it.index })
        assertEquals(3, after.currentIndex)
    }

    // Review Focus 2: duplicates keep their own window index and only the current one is current.
    @Test
    fun duplicateMediaIdsKeepDistinctWindowIndices() {
        val dup = items(1).first()
        player.setMediaItems(listOf(dup, items(2)[1], dup), 2, C.TIME_UNSET)
        idle()
        val queue = player.queueState()
        assertEquals(listOf(0, 1, 2), queue.items.map { it.index })
        assertEquals(2, queue.currentIndex)
    }

    // Ruling 1, through the production QueuePublisher wired as PlaybackService wires it (publisher, then enforcer).
    // Mutations (each deletes one QueuePublisher override):
    // - onMediaItemTransition → (b) RED: a same-timeline seek fires only the transition, so currentIndex stays 2;
    // - onTimelineChanged → (c) RED: the enforcer's reshuffle is never republished, so Media3's order stays published;
    // - onShuffleModeEnabledChanged → (d) RED: turning shuffle off changes neither the timeline nor the item.
    @Test
    fun publisherKeepsTheHolderQueueInStepWithThePlayer() {
        val holder = PlaybackStateHolder()
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        player.addListener(QueuePublisher(player, holder))
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        fun published() = holder.queue.value.items.map { it.index }

        player.setMediaItems(items(5), 2, C.TIME_UNSET) // (a) timeline order, current at position 2
        idle()
        assertEquals(listOf(0, 1, 2, 3, 4), published())
        assertEquals(2, holder.queue.value.currentIndex)

        player.seekToDefaultPosition(4) // (b) same order, current moves
        idle()
        assertEquals(listOf(0, 1, 2, 3, 4), published())
        assertEquals(4, holder.queue.value.currentIndex)

        player.shuffleModeEnabled = true // (c) the final published order is the enforcer's start-first one
        idle()
        assertEquals(4, published().first())
        assertEquals(0, holder.queue.value.currentIndex)
        assertEquals((0 until 5).toSet(), published().toSet())

        player.shuffleModeEnabled = false // (d) back to timeline order
        idle()
        assertEquals(listOf(0, 1, 2, 3, 4), published())
        assertEquals(4, holder.queue.value.currentIndex)
    }

    private companion object {
        const val MEDIA3_ORDER_SEED = 1L
    }
}
