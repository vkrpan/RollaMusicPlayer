package com.rolla.musicplayer.feature.widget

import com.rolla.musicplayer.core.media.PlaybackController
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Glance runs `ActionCallback.onAction` on `Dispatchers.Default`, but `MediaController` throws
 * `IllegalStateException("MediaController method is called from a wrong thread")` for any call off
 * its application (main) thread -- and Glance swallows that exception, so the buttons looked dead.
 * [dispatchToController] must therefore run both `connect()` and the command on the main dispatcher.
 */
class DispatchToControllerTest {

    private val fakeMainExecutor = Executors.newSingleThreadExecutor { Thread(it, FAKE_MAIN_THREAD) }
    private val fakeMain = fakeMainExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        fakeMain.close()
    }

    @Test
    fun `connect and the command both run on the main dispatcher, connect first`() = runTest {
        val calls = mutableListOf<String>()
        val controller = mockk<PlaybackController> {
            every { connect() } answers { calls += "connect@${Thread.currentThread().name}" }
            every { next() } answers { calls += "next@${Thread.currentThread().name}" }
        }

        dispatchToController(controller, fakeMain) { it.next() }

        assertEquals(listOf("connect@$FAKE_MAIN_THREAD", "next@$FAKE_MAIN_THREAD"), calls)
    }

    @Test
    fun `the command is dispatched exactly once`() = runTest {
        val controller = mockk<PlaybackController> {
            every { connect() } just Runs
            every { togglePlayPause() } just Runs
        }
        var dispatched = 0

        dispatchToController(controller, fakeMain) {
            dispatched++
            it.togglePlayPause()
        }

        assertEquals(1, dispatched)
    }

    private companion object {
        const val FAKE_MAIN_THREAD = "fake-main"
    }
}
