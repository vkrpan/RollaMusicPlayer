package com.rolla.musicplayer.feature.playlists

import app.cash.turbine.test
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [PlaylistsViewModel].
 *
 * Note ([FakeSongRepository] limitation): its four smart-playlist `observe*()` methods
 * ([FakeSongRepository.observeRecentlyPlayed], [FakeSongRepository.observeFavourites],
 * [FakeSongRepository.observeMostPlayed], [FakeSongRepository.observeRecentlyAdded]) all alias a
 * single shared backing flow ([FakeSongRepository.emit] pushes to all four at once). That makes
 * it impossible to assert *distinct* counts/previews per smart-playlist kind with this fake —
 * pushing one list updates all four identically. These tests therefore assert what the fake can
 * support: correct [SmartPlaylistKind]/label/ordering, and count/preview derived correctly from
 * a shared song list. Distinct-per-kind count assertions would require the fake to expose four
 * independent backing flows; see the FakeSongRepository docstring for context, this is
 * `:core:data`'s test-double to evolve, not this ViewModel's.
 *
 * The `smartPlaylists` assertions below deliberately collect into a list and assert on
 * `.last()` after `advanceUntilIdle()` rather than sequential Turbine `awaitItem()` calls: because
 * all four upstream flows are the *same* [FakeSongRepository] instance (see above), `combine()`
 * recomputes once per upstream collector as each independently observes the new value under
 * [MainDispatcherRule]'s `UnconfinedTestDispatcher`, producing transient intermediate
 * recombinations before settling. `.last()` reliably captures the settled state; sequential
 * `awaitItem()` calls are flaky against this fake for that reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()

    private lateinit var viewModel: PlaylistsViewModel

    @Before
    fun setUp() {
        viewModel = PlaylistsViewModel(
            playlistRepository = fakePlaylistRepository,
            songRepository = fakeSongRepository,
        )
    }

    // ── smartPlaylists ───────────────────────────────────────────────────────

    @Test
    fun smartPlaylists_givenEmptyLibrary_emitsFourZeroCountSummariesInOrder() = runTest {
        val received = mutableListOf<List<SmartPlaylistSummary>>()
        val collectJob = launch { viewModel.smartPlaylists.collect { received.add(it) } }
        advanceUntilIdle()

        val summaries = received.last()
        assertEquals("smartPlaylists must always expose exactly 4 entries", 4, summaries.size)
        assertEquals(
            "Entries must be ordered: recently played, favourites, most played, recently added",
            listOf(
                SmartPlaylistKind.RECENTLY_PLAYED,
                SmartPlaylistKind.FAVOURITES,
                SmartPlaylistKind.MOST_PLAYED,
                SmartPlaylistKind.RECENTLY_ADDED,
            ),
            summaries.map { it.kind },
        )
        assertEquals(
            listOf("Recently played", "Favourites", "Most played", "Recently added"),
            summaries.map { it.label },
        )
        summaries.forEach { summary ->
            assertEquals(0, summary.count)
            assertEquals(emptyList<String>(), summary.previewArtworkUris)
        }

        collectJob.cancel()
    }

    @Test
    fun smartPlaylists_whenSongsEmitted_reflectsCountsAndPreviewsInOrder() = runTest {
        val songs = createTestSongs(count = 5)
        val received = mutableListOf<List<SmartPlaylistSummary>>()
        val collectJob = launch { viewModel.smartPlaylists.collect { received.add(it) } }

        fakeSongRepository.emit(songs)
        advanceUntilIdle()

        val summaries = received.last()
        assertEquals(
            "Entries must be ordered: recently played, favourites, most played, recently added",
            listOf(
                SmartPlaylistKind.RECENTLY_PLAYED,
                SmartPlaylistKind.FAVOURITES,
                SmartPlaylistKind.MOST_PLAYED,
                SmartPlaylistKind.RECENTLY_ADDED,
            ),
            summaries.map { it.kind },
        )
        assertEquals(
            listOf("Recently played", "Favourites", "Most played", "Recently added"),
            summaries.map { it.label },
        )
        // The fake aliases all four repository flows to one shared list (see class doc), so
        // every summary reflects the same underlying song list here.
        summaries.forEach { summary ->
            assertEquals(songs.size, summary.count)
            assertEquals(songs.take(4).map { it.artworkUri }, summary.previewArtworkUris)
        }

        collectJob.cancel()
    }

    @Test
    fun smartPlaylists_previewArtworkUris_isCappedAtFour() = runTest {
        val songs = createTestSongs(count = 10)
        val received = mutableListOf<List<SmartPlaylistSummary>>()
        val collectJob = launch { viewModel.smartPlaylists.collect { received.add(it) } }

        fakeSongRepository.emit(songs)
        advanceUntilIdle()

        val summaries = received.last()
        assertEquals(songs.size, summaries.first().count)
        summaries.forEach { summary ->
            assertEquals(4, summary.previewArtworkUris.size)
        }

        collectJob.cancel()
    }

    // ── userPlaylists ────────────────────────────────────────────────────────

    @Test
    fun userPlaylists_givenNoEmission_emitsInitialEmptyList() = runTest {
        viewModel.userPlaylists.test {
            assertEquals(emptyList<Playlist>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun userPlaylists_reflectsPlaylistRepository() = runTest {
        val playlists = listOf(
            Playlist(id = 1L, name = "Road trip", songCount = 12, createdAt = 1L, updatedAt = 1L),
            Playlist(id = 2L, name = "Chill", songCount = 4, createdAt = 2L, updatedAt = 2L),
        )

        viewModel.userPlaylists.test {
            assertEquals(emptyList<Playlist>(), awaitItem())

            fakePlaylistRepository.emitPlaylists(playlists)

            assertEquals(playlists, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

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
}
