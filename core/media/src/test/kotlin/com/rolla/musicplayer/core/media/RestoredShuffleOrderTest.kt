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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RestoredShuffleOrderTest {

    private lateinit var player: ExoPlayer
    private lateinit var restored: RestoredShuffleOrder

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        // Production order (PlaybackService): the enforcer first, then RestoredShuffleOrder.
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restored = RestoredShuffleOrder(player) { 99L }
        player.addListener(restored)
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(n: Int) = (0 until n).map {
        MediaItem.Builder().setMediaId("id$it").setUri("content://media/external/audio/media/$it")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("T$it").build()).build()
    }

    private fun order() = player.queueState().items.map { it.index }

    /** Media3's own seeded order for [n] items starting at [startIndex], read from a bare player with no listeners. */
    private fun media3Order(n: Int, startIndex: Int): List<Int> {
        val bare = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            bare.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
            bare.shuffleModeEnabled = true
            bare.setMediaItems(items(n), startIndex, C.TIME_UNSET)
            idle()
            return bare.queueState().items.map { it.index }
        } finally {
            bare.release()
        }
    }

    // Ruling R3's sequence. Media3's seeded order does not start at the current window (2), so the enforcer reshuffles
    // first (the control below proves it fires) and RestoredShuffleOrder must replace that order. The saved order does
    // not start at window 2 either. Mutations: register RestoredShuffleOrder BEFORE the enforcer (in setUp) → the
    // enforcer reshuffles the saved order away → RED; drop the setShuffleOrder call → the enforcer's order stays → RED.
    @Test
    fun theSavedOrderSurvivesTheEnforcer() {
        restored.arm(listOf(3, 0, 4, 1, 2))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 2, 83_000L)
        idle()

        assertEquals(listOf(3, 0, 4, 1, 2), order())
        assertEquals(4, player.queueState().currentIndex)
        assertFalse(restored.isArmed)
    }

    // Control for the fixture above: with nothing armed, the enforcer replaces Media3's seeded order (which starts
    // elsewhere) with one that starts at the current window. Mutation: don't register the enforcer in setUp → Media3's
    // order stays → RED.
    @Test
    fun withNothingArmedTheEnforcerStartsTheOrderAtTheCurrentWindow() {
        val media3 = media3Order(5, startIndex = 2)
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 2, 83_000L)
        idle()

        assertNotEquals(2, media3.first())
        assertEquals(2, order().first())
        assertNotEquals(media3, order())
    }

    // One-shot: the next queue the user starts gets the enforcer's start-first order again. Mutation: never clear
    // `pending` → the second same-size queue gets the saved order → RED.
    @Test
    fun itAppliesOnceThenTheEnforcerOwnsLaterQueues() {
        restored.arm(listOf(3, 0, 4, 1, 2))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 1, C.TIME_UNSET)
        idle()

        player.setMediaItems(items(5), 2, C.TIME_UNSET)
        idle()

        assertEquals(2, order().first())
    }

    // Only a playlist replacement consumes it. Starts at window 2 so the baseline order differs from slot order.
    // Mutations: drop the PLAYLIST_CHANGED check → the seek applies the armed order → RED; clear `pending` before that
    // check (consume on any transition, apply only on a playlist change) → no longer armed → RED.
    @Test
    fun aSeekDoesNotConsumeIt() {
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 2, C.TIME_UNSET)
        idle()
        val before = order()
        assertNotEquals((0 until 5).toList(), before)

        restored.arm(listOf(4, 3, 2, 1, 0))
        player.seekToDefaultPosition(0)
        idle()

        assertEquals(before, order())
        assertTrue(restored.isArmed)
    }

    private companion object {
        const val MEDIA3_ORDER_SEED = 1L
    }
}
