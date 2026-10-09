package com.rolla.musicplayer.feature.playlists

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

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

    @Test
    fun playAll_turnsShuffleOffThenPlaysAllFromZero() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        // uiState is a WhileSubscribed StateFlow — it only starts collecting the upstream
        // combine/map chain once it has a subscriber, so a collector must be active before
        // uiState.value reflects the fake repository's data.
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playAll()

        verifyOrder {
            playbackController.setShuffle(ShuffleMode.OFF)
            playbackController.playAll(songs, startIndex = 0)
        }
        collectJob.cancel()
    }

    @Test
    fun shuffleAll_turnsShuffleOnThenPlaysAllFromARandomStart() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        viewModel.random = Random(SHUFFLE_SEED)
        val expectedStart = Random(SHUFFLE_SEED).nextInt(songs.size)
        assertNotEquals("Seed must pick a non-zero start, or a hardcoded 0 would pass", 0, expectedStart)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.shuffleAll()

        verifyOrder {
            playbackController.setShuffle(ShuffleMode.ON)
            playbackController.playAll(songs, startIndex = expectedStart)
        }
        collectJob.cancel()
    }

    @Test
    fun playSong_startsQueueAtSelectedSongIndex() = runTest {
        val songs = createTestSongs(3)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playSong(songs[2])

        verify { playbackController.playAll(songs, startIndex = 2) }
        collectJob.cancel()
    }

    @Test
    fun playAll_and_shuffleAll_givenEmptyPlaylist_doNothing() = runTest {
        // favouritesFlow stays at its default empty value — a loaded, empty smart playlist.
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playAll()
        viewModel.shuffleAll()

        verify(exactly = 0) { playbackController.playAll(any(), any()) }
        verify(exactly = 0) { playbackController.setShuffle(any()) }
        collectJob.cancel()
    }

    @Test
    fun playSong_givenSongNotInCurrentList_startsQueueAtIndexZero() = runTest {
        val songs = createTestSongs(2)
        favouritesFlow.value = songs
        val viewModel = smartPlaylistViewModel(SmartPlaylistKind.FAVOURITES)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val songNotInList = createTestSongs(3).last()
        viewModel.playSong(songNotInList)

        verify { playbackController.playAll(songs, startIndex = 0) }
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
