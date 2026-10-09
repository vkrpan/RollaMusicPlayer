@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.rolla.musicplayer.feature.library

import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

// Its first nextInt(3) is non-zero (asserted in the test), so a hardcoded start index of 0 fails.
private const val SHUFFLE_SEED = 7L

@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── Dependencies ──────────────────────────────────────────────────────────

    private val fakeRepository = FakeSongRepository()

    private val fakePlaylistRepository = FakePlaylistRepository()

    // Relaxed mock: connect() is called in TracksViewModel.init; we don't want to
    // stub it manually in every test.
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private lateinit var viewModel: TracksViewModel

    @Before
    fun setup() {
        viewModel = TracksViewModel(
            songRepository = fakeRepository,
            playbackController = playbackController,
            playlistRepository = fakePlaylistRepository,
        )
    }

    // ── songs StateFlow ───────────────────────────────────────────────────────

    @Test
    fun `songs_emitsFromRepository`() = runTest {
        val expectedSongs = listOf(
            Song(
                id = "1",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                albumId = 10L,
                durationMs = 354_000L,
                trackNumber = 11,
                year = 1975,
                contentUri = "content://media/external/audio/media/1",
                artworkUri = "content://media/external/audio/albumart/10",
            ),
        )
        val received = mutableListOf<List<Song>>()

        // Subscribing starts the WhileSubscribed upstream from fakeRepository.
        val collectJob = launch { viewModel.songs.collect { received.add(it) } }

        fakeRepository.emit(expectedSongs)
        advanceUntilIdle()

        assertEquals(
            "Last emission must equal the songs pushed into fakeRepository",
            expectedSongs,
            received.last(),
        )
        collectJob.cancel()
    }

    // ── play ──────────────────────────────────────────────────────────────────

    @Test
    fun `play_delegatesToPlaybackController`() {
        val song = Song(
            id = "42",
            title = "Stairway to Heaven",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            albumId = 2L,
            durationMs = 482_000L,
            trackNumber = 4,
            year = 1971,
            contentUri = "content://media/external/audio/media/42",
            artworkUri = "",
        )

        viewModel.play(song)

        verify(exactly = 1) { playbackController.play(song) }
    }

    // ── playAll / shuffleAll ──────────────────────────────────────────────────

    @Test
    fun `playAll_turnsShuffleOffAndPlaysFromTheFirstSong`() = runTest {
        val songs = listOf(song("1"), song("2"), song("3"))
        val job = launch { viewModel.songs.collect {} }
        fakeRepository.emit(songs)
        advanceUntilIdle()
        viewModel.playAll()
        verifyOrder {
            playbackController.setShuffle(ShuffleMode.OFF)
            playbackController.playAll(songs, 0)
        }
        job.cancel()
    }

    @Test
    fun `shuffleAll_turnsShuffleOnAndPlaysAllFromARandomStart`() = runTest {
        val songs = listOf(song("1"), song("2"), song("3"))
        viewModel.random = Random(SHUFFLE_SEED)
        val expectedStart = Random(SHUFFLE_SEED).nextInt(songs.size)
        assertNotEquals("Seed must pick a non-zero start, or a hardcoded 0 would pass", 0, expectedStart)
        val job = launch { viewModel.songs.collect {} }
        fakeRepository.emit(songs)
        advanceUntilIdle()
        viewModel.shuffleAll()
        verifyOrder {
            playbackController.setShuffle(ShuffleMode.ON)
            playbackController.playAll(songs, expectedStart)
        }
        job.cancel()
    }

    @Test
    fun `playAll_and_shuffleAll_onEmptyLibrary_doNothing`() = runTest {
        // A real, indexed-but-empty library: the subscription is live and the repository has emitted.
        val job = launch { viewModel.songs.collect {} }
        fakeRepository.emit(emptyList())
        advanceUntilIdle()
        viewModel.playAll()
        viewModel.shuffleAll()
        verify(exactly = 0) { playbackController.playAll(any(), any()) }
        verify(exactly = 0) { playbackController.setShuffle(any()) }
        job.cancel()
    }

    // ── userPlaylists ─────────────────────────────────────────────────────────

    @Test
    fun `userPlaylists_emitsFromRepository`() = runTest {
        val playlists = listOf(
            Playlist(id = 1L, name = "Road trip", songCount = 2, createdAt = 1L, updatedAt = 1L),
        )
        val received = mutableListOf<List<Playlist>>()

        val collectJob = launch { viewModel.userPlaylists.collect { received.add(it) } }

        fakePlaylistRepository.emitPlaylists(playlists)
        advanceUntilIdle()

        assertEquals(
            "Last emission must equal the playlists pushed into fakePlaylistRepository",
            playlists,
            received.last(),
        )
        collectJob.cancel()
    }

    // ── addSongToPlaylist ─────────────────────────────────────────────────────

    @Test
    fun `addSongToPlaylist_callsRepositoryAddSongsWithGivenIds`() = runTest {
        viewModel.addSongToPlaylist(songId = "song-1", playlistId = 7L)
        advanceUntilIdle()

        assertEquals(
            "addSongs must be called once with the given playlistId and songId",
            listOf(7L to listOf("song-1")),
            fakePlaylistRepository.addSongsCalls,
        )
    }

    // ── createPlaylistAndAddSong ──────────────────────────────────────────────

    @Test
    fun `createPlaylistAndAddSong_createsPlaylistThenAddsSong`() = runTest {
        viewModel.createPlaylistAndAddSong(name = "My Mix", songId = "song-1")
        advanceUntilIdle()

        val newPlaylist = fakePlaylistRepository.observePlaylists().first().firstOrNull { it.name == "My Mix" }
        assertTrue("A new playlist named 'My Mix' must have been created", newPlaylist != null)
        assertEquals(
            "addSongs must be called with the newly created playlist's id and the given songId",
            listOf(newPlaylist!!.id to listOf("song-1")),
            fakePlaylistRepository.addSongsCalls,
        )
    }

    @Test
    fun `createPlaylistAndAddSong_givenBlankName_isNoOp`() = runTest {
        viewModel.createPlaylistAndAddSong(name = "   ", songId = "song-1")
        advanceUntilIdle()

        assertTrue(
            "No playlist should be created for a blank name",
            fakePlaylistRepository.observePlaylists().first().isEmpty(),
        )
        assertTrue(
            "addSongs must not be called for a blank playlist name",
            fakePlaylistRepository.addSongsCalls.isEmpty(),
        )
    }

    private fun song(id: String) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        album = "Album",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = id.toIntOrNull(),
        year = 2001,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "",
    )
}
