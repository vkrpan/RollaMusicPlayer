@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.rolla.musicplayer.feature.library

import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.data.scanner.SyncResult
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── Dependencies ──────────────────────────────────────────────────────────

    private val fakeRepository = FakeSongRepository()

    private val fakePlaylistRepository = FakePlaylistRepository()

    // Strict mock: any un-stubbed call will throw, catching accidental invocations.
    private val libraryIndexer = mockk<LibraryIndexer>()

    // Relaxed mock: connect() is called in LibraryViewModel.init; we don't want to
    // stub it manually in every test.
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        viewModel = LibraryViewModel(
            songRepository = fakeRepository,
            libraryIndexer = libraryIndexer,
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

    // ── onPermissionGranted ───────────────────────────────────────────────────

    @Test
    fun `onPermissionGranted_transitionsToScanningThenDone`() = runTest {
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 2, removed = 0)

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        assertEquals(
            "scanState must be Done(2, 0) after a successful sync",
            ScanState.Done(added = 2, removed = 0),
            viewModel.scanState.value,
        )
    }

    @Test
    fun `onPermissionGranted_whenAlreadyScanning_isIgnored`() = runTest {
        // Block sync() indefinitely so the ViewModel stays in Scanning state.
        val blockingSync = CompletableDeferred<SyncResult>()
        coEvery { libraryIndexer.sync() } coAnswers { blockingSync.await() }

        // First call: transitions to Scanning, then suspends inside sync().
        viewModel.onPermissionGranted()
        // Second call: the guard (_scanState is Scanning) short-circuits immediately.
        viewModel.onPermissionGranted()

        coVerify(exactly = 1) { libraryIndexer.sync() }

        // Unblock the in-flight coroutine so viewModelScope can finish cleanly.
        blockingSync.complete(SyncResult(added = 0, removed = 0))
        advanceUntilIdle()
    }

    @Test
    fun `onPermissionGranted_whenDone_isIgnored`() = runTest {
        // Once a scan completes successfully, subsequent calls (e.g. from config-change
        // recomposition) must be no-ops so users don't see a spinner flash on rotation.
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 1, removed = 0)

        viewModel.onPermissionGranted()
        advanceUntilIdle()
        assertTrue(
            "First scan must complete and reach Done",
            viewModel.scanState.value is ScanState.Done,
        )

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryIndexer.sync() }
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
}
