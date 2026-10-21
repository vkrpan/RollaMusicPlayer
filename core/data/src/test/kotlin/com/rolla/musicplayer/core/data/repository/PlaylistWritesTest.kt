package com.rolla.musicplayer.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistWritesTest {
    private val repository: PlaylistRepository = mockk(relaxed = true)

    // The name is trimmed, the playlist created, the track added to that id. Control: addSongs gets the created id
    // (42), not a constant. Mutation: addSongs(0L, …) → RED.
    @Test
    fun createsTrimmedPlaylistAndAddsTheTrack() = runTest {
        coEvery { repository.createPlaylist("Road trip") } returns 42L
        assertEquals(
            "createPlaylistWithSong returns the id createPlaylist gave the trimmed name",
            42L,
            repository.createPlaylistWithSong("  Road trip  ", "7"),
        )
        coVerify(exactly = 1) { repository.addSongs(42L, listOf("7")) }
    }

    // A blank name writes nothing. Control: a valid name first writes once (returns 42, one create, one add), so a
    // stub that never writes fails here. Then a blank name returns null and leaves both counts at one.
    // Mutations: the trivial stub `= null` → RED at the control; drop the blank check → RED in the second half.
    @Test
    fun aBlankNameWritesNothing() = runTest {
        fun verifying(message: String, check: () -> Unit) =
            try {
                check()
            } catch (e: AssertionError) {
                throw AssertionError("$message: ${e.message}", e)
            }
        coEvery { repository.createPlaylist("Road trip") } returns 42L
        assertEquals(
            "control: a valid name returns the created id",
            42L,
            repository.createPlaylistWithSong("Road trip", "7"),
        )
        verifying("control: a valid name creates one playlist") {
            coVerify(exactly = 1) { repository.createPlaylist(any()) }
        }
        verifying("control: a valid name adds one track") {
            coVerify(exactly = 1) { repository.addSongs(any(), any()) }
        }

        assertNull("a blank name returns null", repository.createPlaylistWithSong("   ", "7"))
        verifying("a blank name creates no further playlist") {
            coVerify(exactly = 1) { repository.createPlaylist(any()) }
        }
        verifying("a blank name adds no further track") {
            coVerify(exactly = 1) { repository.addSongs(any(), any()) }
        }
    }
}
