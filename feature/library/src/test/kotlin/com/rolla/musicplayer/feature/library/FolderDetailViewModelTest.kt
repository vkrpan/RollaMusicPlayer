package com.rolla.musicplayer.feature.library

import androidx.lifecycle.SavedStateHandle
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeFolderRepository
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

// Seeds the VM's Random test seam; the shuffle test verifies that exact instance is forwarded to the controller.
private const val SHUFFLE_SEED = 3

// An existing playlist id distinct from the fake's first created id (1), so a hardcoded id can't pass by accident.
private const val CHOSEN_PLAYLIST_ID = 9L

/** Mirrors FavouritesTabViewModelTest one-to-one (Phase 4b ruling 8), plus the route-argument tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // observeFolderSongs(path) returns the same flow emitFolderSongs(...) drives, and records the path asked.
    private val folderRepository = FakeFolderRepository()

    private val playlistRepository = FakePlaylistRepository()

    // Relaxed: connect() runs in init and the helpers return Unit.
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private val songA = song(id = "a", title = "Alpha")
    private val songB = song(id = "b", title = "Bravo")
    private val songC = song(id = "c", title = "Charlie")

    private lateinit var viewModel: FolderDetailViewModel

    @Before
    fun setUp() {
        viewModel = FolderDetailViewModel(
            SavedStateHandle(mapOf("folderPath" to "Music/Chill")),
            folderRepository,
            playbackController,
            playlistRepository,
        )
    }

    // Mutation: seed with emptyList() → RED.
    @Test
    fun `songs are null before the first emission`() {
        assertNull(viewModel.songs.value)
    }

    // Mutation: play(song) calls playbackController.play(song) (queue of one) → RED.
    @Test
    fun `tapping a track plays the folder from it`() = runTest {
        val job = launch { viewModel.songs.collect {} }
        folderRepository.emitFolderSongs(listOf(songA, songB, songC))
        advanceUntilIdle()

        viewModel.play(songB)

        verify { playbackController.playFrom(listOf(songA, songB, songC), songB) }
        job.cancel()
    }

    // Mutation: playAll() calls playAll(list, 0) directly → playAllInOrder not called → RED.
    @Test
    fun `play all delegates to playAllInOrder`() = runTest {
        val job = launch { viewModel.songs.collect {} }
        folderRepository.emitFolderSongs(listOf(songA, songB))
        advanceUntilIdle()

        viewModel.playAll()

        verify { playbackController.playAllInOrder(listOf(songA, songB)) }
        job.cancel()
    }

    // Mutation: pass Random.Default instead of the seam → RED.
    @Test
    fun `shuffle all delegates with the view model's random`() = runTest {
        val job = launch { viewModel.songs.collect {} }
        folderRepository.emitFolderSongs(listOf(songA, songB))
        advanceUntilIdle()
        viewModel.random = Random(SHUFFLE_SEED)

        viewModel.shuffleAll()

        verify { playbackController.shuffleAll(listOf(songA, songB), viewModel.random) }
        job.cancel()
    }

    // The helper's own empty no-op is pinned in :core:media (Phase 3 Task 2). This pins that a not-yet-loaded list
    // reaches it as empty instead of throwing. Mutation: songs.value!! → NullPointerException → RED.
    @Test
    fun `play all before the first emission passes an empty list`() {
        viewModel.playAll()
        verify { playbackController.playAllInOrder(emptyList()) }
    }

    // Same pin for Shuffle. Mutation: songs.value!! in shuffleAll → NullPointerException → RED.
    @Test
    fun `shuffle all before the first emission passes an empty list`() {
        viewModel.shuffleAll()
        verify { playbackController.shuffleAll(emptyList(), any()) }
    }

    // The options sheet's playlist picker reads this. Mutation: flowOf(emptyList()) upstream of stateIn → RED.
    @Test
    fun `user playlists mirror the repository`() = runTest {
        val playlists = listOf(
            Playlist(id = CHOSEN_PLAYLIST_ID, name = "Road trip", songCount = 2, createdAt = 1L, updatedAt = 1L),
        )
        playlistRepository.emitPlaylists(playlists)
        val job = launch { viewModel.userPlaylists.collect {} }
        advanceUntilIdle()

        assertEquals(playlists, viewModel.userPlaylists.value)
        job.cancel()
    }

    // Mutation: drop playbackController.connect() from init → RED.
    @Test
    fun `creating the view model connects the controller`() {
        verify { playbackController.connect() }
    }

    // Mutation: drop the blank-name guard → a playlist named "" (the trimmed blank) is created → RED.
    @Test
    fun `createPlaylistAndAddSong ignores a blank name`() = runTest {
        viewModel.createPlaylistAndAddSong(name = "   ", songId = "song-1")
        advanceUntilIdle()
        assertTrue(playlistRepository.observePlaylists().first().isEmpty())
        assertTrue(playlistRepository.addSongsCalls.isEmpty())
    }

    // Mutation: add to playlist 0 instead of the created id → RED.
    @Test
    fun `createPlaylistAndAddSong adds the song to the new playlist`() = runTest {
        viewModel.createPlaylistAndAddSong(name = " My Mix ", songId = "song-1")
        advanceUntilIdle()
        val created = playlistRepository.observePlaylists().first().single { it.name == "My Mix" }
        assertEquals(listOf(created.id to listOf("song-1")), playlistRepository.addSongsCalls)
    }

    // Mutation: swap the arguments (addSongs(songId.toLong(), …)) or drop the call → RED.
    @Test
    fun `addSongToPlaylist adds to the chosen playlist`() = runTest {
        viewModel.addSongToPlaylist(songId = "song-1", playlistId = CHOSEN_PLAYLIST_ID)
        advanceUntilIdle()
        assertEquals(listOf(CHOSEN_PLAYLIST_ID to listOf("song-1")), playlistRepository.addSongsCalls)
    }

    // Mutations: read savedStateHandle["path"] → checkNotNull throws → RED; observeFolderSongs("") → RED.
    @Test
    fun `it observes the route's folder`() {
        assertEquals(listOf("Music/Chill"), folderRepository.requestedFolderPaths)
    }

    // Review Focus 1. Mutations: title = folderPath → "Music/Chill" (and "" for the root) → RED;
    // title = folderPath.substringAfterLast('/') → "Chill" passes, but the root reads "" → RED.
    @Test
    fun `the title is the folder's name from the route`() {
        assertEquals("Chill", viewModel.title)
        val root = FolderDetailViewModel(
            SavedStateHandle(mapOf("folderPath" to "")),
            FakeFolderRepository(),
            playbackController,
            playlistRepository,
        )
        assertEquals("Internal storage", root.title)
    }

    private fun song(id: String, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "",
    )
}
