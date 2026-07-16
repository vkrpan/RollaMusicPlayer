package com.rolla.musicplayer.feature.widget

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [WidgetPlaybackUpdateHook]'s coalescing pipeline. The real Glance render needs an
 * Android runtime, so [WidgetPlaybackUpdateHook.renderWidget] is swapped for counters/gates (the
 * documented test seam); the actual on-launcher re-render is device-only verification.
 */
class WidgetPlaybackUpdateHookTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun singleFire_rendersExactlyOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val hook = WidgetPlaybackUpdateHook(context, dispatcher)
        var renders = 0
        hook.renderWidget = { renders++ }

        hook.onPlaybackStateChanged()
        advanceUntilIdle()

        assertEquals(1, renders)
    }

    @Test
    fun burstDuringInFlightRender_coalescesToAtMostOneFollowUp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val hook = WidgetPlaybackUpdateHook(context, dispatcher)
        val gate = CompletableDeferred<Unit>()
        var renders = 0
        hook.renderWidget = {
            renders++
            if (renders == 1) gate.await()
        }

        hook.onPlaybackStateChanged()
        // Let the collector enter the first (gated) render before bursting.
        testScheduler.runCurrent()
        repeat(5) { hook.onPlaybackStateChanged() }
        gate.complete(Unit)
        advanceUntilIdle()

        // 1 in-flight + at most 1 coalesced follow-up; never one render per fire.
        assertEquals(2, renders)
    }

    @Test
    fun renderFailure_doesNotKillTheCollector() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val hook = WidgetPlaybackUpdateHook(context, dispatcher)
        var attempts = 0
        var succeeded = false
        hook.renderWidget = {
            attempts++
            if (attempts == 1) throw IOException("render failed") else succeeded = true
        }

        hook.onPlaybackStateChanged()
        advanceUntilIdle()
        hook.onPlaybackStateChanged()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertTrue(succeeded)
    }

    @Test
    fun renderCancellation_isRethrownAndStopsTheCollector() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val hook = WidgetPlaybackUpdateHook(context, dispatcher)
        var attempts = 0
        hook.renderWidget = {
            attempts++
            throw CancellationException("scope torn down mid-render")
        }

        hook.onPlaybackStateChanged()
        advanceUntilIdle()
        // Unlike a generic render failure (swallowed, collector keeps running), a
        // CancellationException must propagate out of the collect block -- that cancels the single
        // collector coroutine, so a later fire is never picked up.
        hook.onPlaybackStateChanged()
        advanceUntilIdle()

        assertEquals(
            "a rethrown CancellationException must stop the collector, not be swallowed like other failures",
            1,
            attempts,
        )
    }
}
