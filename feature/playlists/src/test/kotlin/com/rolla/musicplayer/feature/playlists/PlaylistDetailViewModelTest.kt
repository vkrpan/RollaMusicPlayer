package com.rolla.musicplayer.feature.playlists

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

// Seeds the VM's Random test seam; the shuffle test verifies that exact instance is forwarded to the controller.
private const val SHUFFLE_SEED = 7L

/**
 * Unit tests for [PlaylistDetailViewModel].
 *
 * Covers both nav destinations that back the single `PlaylistDetailScreen`: a user playlist
 * (`SavedStateHandle["playlistId"]`) and a smart playlist (`SavedStateHandle["kind"]`). Since
 * `:feature:playlists` must never depend on `:app`'s route classes, these tests construct
 * [SavedStateHandle] directly from a raw `Map<String, Any?>`, exactly mirroring how Navigation
 * Compose's type-safe routes populate it under the hood.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakePlaylistRepository = FakePlaylistRepository()

    private val favouritesFlow = MutableStateFlow<List<Song>>(emptyList())

    /** Relaxed so unrelated suspend calls (toggleFavorite, recordPlaybackStarted) are no-ops. */
    private val songRepository: SongRepository = mockk(relaxed = true) {
        every { observeFavourites() } returns favouritesFlow
    }

    private val playbackController: PlaybackController = mockk(relaxed = true)

    private fun createTestSongs(count: Int): List<Song> = (1..count).map { index ->
        Song(
            id = "song-$index",
            title = "Song $index",
            artist = "Artist $index",
            album = "Album $index",
            albumId = index.toLong(),
            durationMs = 200_000L,
            trackNumber = index,
            year = 2000 + index,
            contentUri = "content://media/external/audio/media/$index",
            artworkUri = "content://media/external/audio/albumart/$index",
        )
    }

    private fun userPlaylistViewModel(playlistId: Long = 1L): PlaylistDetailViewModel =
        PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to playlistId)),
            playlistRepository = fakePlaylistRepository,
            songRepository = songRepository,
            playbackController = playbackController,
        )

    private fun smartPlaylistViewModel(kind: SmartPlaylistKind): PlaylistDetailViewModel =
        PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("kind" to kind.name)),
            playlistRepository = fakePlaylistRepository,
            songRepository = songRepository,
            playbackController = playbackController,
        )

    // ── user playlist ────────────────────────────────────────────────────────

    @Test
    fun uiState_givenUserPlaylist_reflectsTitleAndSongsFromRepository() = runTest {
        val playlist = Playlist(id = 1L, name = "Road trip", songCount = 2, createdAt = 1L, updatedAt = 1L)
        val songs = createTestSongs(2)
        fakePlaylistRepository.emitPlaylists(listOf(playlist))
        fakePlaylistRepository.emitPlaylistSongs(1L, songs)

        val viewModel = userPlaylistViewModel(playlistId = 1L)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals("Road trip", state.title)
            assertEquals(songs, state.songs)
            assertTrue(state.isUserPlaylist)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun uiState_givenUserPlaylist_missingFromRepository_fallsBackToEmptyTitle() = runTest {
        fakePlaylistRepository.emitPlaylistSongs(1L, createTestSongs(1))

        val viewModel = userPlaylistViewModel(playlistId = 1L)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals("", state.title)
            assertTrue(state.isUserPlaylist)
        }
    }

    @Test
    fun removeSong_givenUserPlaylist_callsRepositoryRemoveSong() = runTest {
        val songs = createTestSongs(2)
        fakePlaylistRepository.emitPlaylists(
            listOf(Playlist(id = 1L, name = "Road trip", songCount = 2, createdAt = 1L, updatedAt = 1L)),
        )
        fakePlaylistRepository.emitPlaylistSongs(1L, songs)
        val viewModel = userPlaylistViewModel(playlistId = 1L)
        advanceUntilIdle()

        viewModel.removeSong(songs.first())
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(songs[1]), state.songs)
        }
    }

    @Test
    fun reorder_givenUserPlaylist_persistsNewOrderToRepository() = runTest {
        val songs = createTestSongs(3)
        fakePlaylistRepository.emitPlaylists(
            listOf(Playlist(id = 1L, name = "Road trip", songCount = 3, createdAt = 1L, updatedAt = 1L)),
        )
        fakePlaylistRepository.emitPlaylistSongs(1L, songs)
        val viewModel = userPlaylistViewModel(playlistId = 1L)
        advanceUntilIdle()

        val newOrder = listOf(songs[2].id, songs[0].id, songs[1].id)
        viewModel.reorder(newOrder)
        advanceUntilIdle()

        fakePlaylistRepository.observePlaylistSongs(1L).test {
            val reordered = expectMostRecentItem()
            assertEquals(listOf(songs[2], songs[0], songs[1]), reordered)
        }
    }

    @Test
    fun reorder_givenSmartPlaylist_isNoOpAndDoesNotThrow() = runTest {
        val songs = createTestSongs(2)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        advanceUntilIdle()

        viewModel.reorder(listOf(songs[1].id, songs[0].id))
        advanceUntilIdle()

        // No crash, and no repository mutation should have occurred — songs list is unchanged.
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(songs, state.songs)
        }
    }

    @Test
    fun renamePlaylist_givenUserPlaylist_updatesNameViaRepository() = runTest {
        fakePlaylistRepository.emitPlaylists(
            listOf(Playlist(id = 1L, name = "Road trip", songCount = 0, createdAt = 1L, updatedAt = 1L)),
        )
        val viewModel = userPlaylistViewModel(playlistId = 1L)
        advanceUntilIdle()

        viewModel.renamePlaylist("Summer Mix")
        advanceUntilIdle()

        fakePlaylistRepository.observePlaylists().test {
            val renamed = expectMostRecentItem()
            assertEquals("Summer Mix", renamed.first { it.id == 1L }.name)
        }
    }

    @Test
    fun renamePlaylist_givenBlankName_isNoOp() = runTest {
        fakePlaylistRepository.emitPlaylists(
            listOf(Playlist(id = 1L, name = "Road trip", songCount = 0, createdAt = 1L, updatedAt = 1L)),
        )
        val viewModel = userPlaylistViewModel(playlistId = 1L)
        advanceUntilIdle()

        viewModel.renamePlaylist("   ")
        advanceUntilIdle()

        fakePlaylistRepository.observePlaylists().test {
            val unchanged = expectMostRecentItem()
            assertEquals("Road trip", unchanged.first { it.id == 1L }.name)
        }
    }

    @Test
    fun renamePlaylist_givenSmartPlaylist_isNoOpAndDoesNotThrow() = runTest {
        favouritesFlow.value = createTestSongs(1)
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        advanceUntilIdle()

        viewModel.renamePlaylist("Summer Mix")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals("Favourite tracks", state.title)
        }
    }

    @Test
    fun deletePlaylist_givenUserPlaylist_removesEntryFromRepository() = runTest {
        fakePlaylistRepository.emitPlaylists(
            listOf(Playlist(id = 1L, name = "Road trip", songCount = 0, createdAt = 1L, updatedAt = 1L)),
        )
        val viewModel = userPlaylistViewModel(playlistId = 1L)
        advanceUntilIdle()

        viewModel.deletePlaylist()
        advanceUntilIdle()

        fakePlaylistRepository.observePlaylists().test {
            val remaining = expectMostRecentItem()
            assertTrue(remaining.none { it.id == 1L })
        }
    }

    @Test
    fun deletePlaylist_givenSmartPlaylist_isNoOpAndDoesNotThrow() = runTest {
        favouritesFlow.value = createTestSongs(1)
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        advanceUntilIdle()

        viewModel.deletePlaylist()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isLoading)
        }
    }

    // ── smart playlist ───────────────────────────────────────────────────────

    @Test
    fun uiState_givenSmartPlaylistFavourites_reflectsLabelAndSongs() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs

        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals("Favourite tracks", state.title)
            assertEquals(songs, state.songs)
            assertEquals(false, state.isUserPlaylist)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun removeSong_givenSmartPlaylist_isNoOpAndDoesNotThrow() = runTest {
        favouritesFlow.value = createTestSongs(2)
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        advanceUntilIdle()

        viewModel.removeSong(createTestSongs(2).first())
        advanceUntilIdle()

        // No crash, and no repository mutation should have occurred — songs list is unchanged.
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.songs.size)
        }
    }

    // ── playback actions ─────────────────────────────────────────────────────

    // Mutation: call playAll(list, 0) directly (skipping setShuffle(OFF)) → RED.
    @Test
    fun playAll_delegatesToPlayAllInOrder() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        // uiState is a WhileSubscribed StateFlow — it only starts collecting the upstream
        // combine/map chain once it has a subscriber, so a collector must be active before
        // uiState.value reflects the fake repository's data.
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playAll()

        verify(exactly = 1) { playbackController.playAllInOrder(songs) }
        collectJob.cancel()
    }

    // Mutation: pass Random.Default instead of the injected random → RED.
    @Test
    fun shuffleAll_delegatesToShuffleAllWithTheViewModelsRandom() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        viewModel.random = Random(SHUFFLE_SEED)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.shuffleAll()

        verify(exactly = 1) { playbackController.shuffleAll(songs, viewModel.random) }
        collectJob.cancel()
    }

    // Mutation: keep the old body (playAll(songs, startIndex = songs.indexOf(song).coerceAtLeast(0))) → RED (ruling 5).
    @Test
    fun playSong_playsThePlaylistFromTheTappedSong() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playSong(songs[2])

        verify(exactly = 1) { playbackController.playFrom(songs, songs[2]) }
        collectJob.cancel()
    }

    // Delegation only: the empty-list no-op is pinned by PlaybackControllerTest
    // (`playAllInOrder and shuffleAll on an empty list do nothing`), so the VM forwards the empty list unguarded.
    // Mutation: restore the VM-side `if (list.isEmpty()) return` guard in playAll() → RED.
    @Test
    fun playAll_and_shuffleAll_givenEmptyPlaylist_delegateTheEmptyListToTheHelpers() = runTest {
        // favouritesFlow stays at its default empty value — a loaded, empty smart playlist.
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playAll()
        viewModel.shuffleAll()

        verify(exactly = 1) { playbackController.playAllInOrder(emptyList()) }
        verify(exactly = 1) { playbackController.shuffleAll(emptyList(), viewModel.random) }
        collectJob.cancel()
    }

    // Delegation only. Behaviour change (ruling 5): a song no longer in the list now plays alone instead of starting
    // the list at index 0; that fallback is pinned by PlaybackControllerTest
    // (`playFrom starts the list at the tapped song or plays it alone when absent`).
    // Mutation: restore the old `.coerceAtLeast(0)` body (playAll(songs, startIndex = 0) for an absent song) → RED.
    @Test
    fun playSong_givenSongNotInCurrentList_stillDelegatesToPlayFrom() = runTest {
        val songs = createTestSongs(2)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val songNotInList = createTestSongs(3).last()
        viewModel.playSong(songNotInList)

        verify(exactly = 1) { playbackController.playFrom(songs, songNotInList) }
        verify(exactly = 0) { playbackController.playAll(any(), any()) }
        collectJob.cancel()
    }

    // ── neither playlistId nor smartKind present (fail-safe fallback) ────────

    @Test
    fun uiState_givenNeitherPlaylistIdNorSmartKind_fallsBackToDefaultEmptyState() = runTest {
        val viewModel = PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(),
            playlistRepository = fakePlaylistRepository,
            songRepository = songRepository,
            playbackController = playbackController,
        )

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals("", state.title)
            assertEquals(emptyList<Song>(), state.songs)
            assertEquals(false, state.isUserPlaylist)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun removeSong_givenNeitherPlaylistIdNorSmartKind_isNoOpAndDoesNotThrow() = runTest {
        val viewModel = PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(),
            playlistRepository = fakePlaylistRepository,
            songRepository = songRepository,
            playbackController = playbackController,
        )

        viewModel.removeSong(createTestSongs(1).first())
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(emptyList<Song>(), state.songs)
        }
    }

    // ── userPlaylists ────────────────────────────────────────────────────────

    @Test
    fun userPlaylists_reflectsPlaylistRepository() = runTest {
        val playlists = listOf(
            Playlist(id = 1L, name = "Road trip", songCount = 2, createdAt = 1L, updatedAt = 1L),
        )
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)

        viewModel.userPlaylists.test {
            assertEquals(emptyList<Playlist>(), awaitItem())

            fakePlaylistRepository.emitPlaylists(playlists)

            assertEquals(playlists, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── addSongToPlaylist ────────────────────────────────────────────────────

    @Test
    fun addSongToPlaylist_callsRepositoryAddSongsWithGivenIds() = runTest {
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)

        viewModel.addSongToPlaylist(songId = "song-1", playlistId = 7L)
        advanceUntilIdle()

        assertEquals(
            listOf(7L to listOf("song-1")),
            fakePlaylistRepository.addSongsCalls,
        )
    }

    // ── createPlaylistAndAddSong ─────────────────────────────────────────────

    @Test
    fun createPlaylistAndAddSong_createsPlaylistThenAddsSong() = runTest {
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)

        viewModel.createPlaylistAndAddSong(name = "My Mix", songId = "song-1")
        advanceUntilIdle()

        val newPlaylist = fakePlaylistRepository.observePlaylists().first().firstOrNull { it.name == "My Mix" }
        assertTrue("A new playlist named 'My Mix' must have been created", newPlaylist != null)
        assertEquals(
            listOf(newPlaylist!!.id to listOf("song-1")),
            fakePlaylistRepository.addSongsCalls,
        )
    }

    @Test
    fun createPlaylistAndAddSong_givenBlankName_isNoOp() = runTest {
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)

        viewModel.createPlaylistAndAddSong(name = "   ", songId = "song-1")
        advanceUntilIdle()

        assertTrue(fakePlaylistRepository.observePlaylists().first().isEmpty())
        assertTrue(fakePlaylistRepository.addSongsCalls.isEmpty())
    }
}
