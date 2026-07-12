package com.rolla.musicplayer.core.media

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import org.junit.Test

class PlaybackUpdateDispatcherTest {

    @Test
    fun `dispatch invokes every contributed hook`() {
        val hookA: PlaybackUpdateHook = mockk(relaxed = true)
        val hookB: PlaybackUpdateHook = mockk(relaxed = true)
        val dispatcher = PlaybackUpdateDispatcher(setOf(hookA, hookB))

        dispatcher.dispatch()

        verify(exactly = 1) { hookA.onPlaybackStateChanged() }
        verify(exactly = 1) { hookB.onPlaybackStateChanged() }
    }

    @Test
    fun `dispatch with an empty hook set does not throw`() {
        val dispatcher = PlaybackUpdateDispatcher(emptySet())

        dispatcher.dispatch()
    }

    @Test
    fun `a throwing hook does not prevent other hooks from running`() {
        val throwingHook: PlaybackUpdateHook = mockk {
            every { onPlaybackStateChanged() } throws RuntimeException("boom")
        }
        val healthyHook: PlaybackUpdateHook = mockk(relaxed = true)
        // LinkedHashSet iteration order (setOf preserves insertion order) puts the throwing hook
        // first, so this also proves a mid-iteration failure doesn't abort the remaining hooks.
        val dispatcher = PlaybackUpdateDispatcher(setOf(throwingHook, healthyHook))

        dispatcher.dispatch()

        verify(exactly = 1) { healthyHook.onPlaybackStateChanged() }
    }

    @Test
    fun `a throwing hook is contained and does not crash dispatch`() {
        val throwingHook: PlaybackUpdateHook = mockk {
            every { onPlaybackStateChanged() } throws IllegalStateException("boom")
        }
        val dispatcher = PlaybackUpdateDispatcher(setOf(throwingHook))

        // Reaching the end of this test (no exception escaping dispatch()) is the assertion.
        dispatcher.dispatch()
    }

    @Test(expected = CancellationException::class)
    fun `dispatch rethrows CancellationException instead of containing it`() {
        val cancellingHook: PlaybackUpdateHook = mockk {
            every { onPlaybackStateChanged() } throws CancellationException("cancelled")
        }
        val dispatcher = PlaybackUpdateDispatcher(setOf(cancellingHook))

        dispatcher.dispatch()
    }
}
