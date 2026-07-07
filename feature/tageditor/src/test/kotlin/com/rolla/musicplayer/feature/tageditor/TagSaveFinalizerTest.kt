package com.rolla.musicplayer.feature.tageditor

import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.data.scanner.SyncResult
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.feature.tageditor.io.MediaScanNotifier
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [TagSaveFinalizer] -- the post-save pipeline ordering (MediaStore notify BEFORE
 * the targeted re-sync, playback refresh last, from re-synced data) and its best-effort contract.
 * All collaborators are concrete classes mocked with MockK, per this module's established test
 * style; [FakeSongRepository] supplies the "re-synced" library state the playback refresh reads.
 */
class TagSaveFinalizerTest {

    private val songFileResolver: SongFileResolver = mockk()
    private val mediaScanNotifier: MediaScanNotifier = mockk()
    private val libraryIndexer: LibraryIndexer = mockk()
    private val fakeSongRepository = FakeSongRepository()
    private val playbackController: PlaybackController = mockk()

    private val finalizer = TagSaveFinalizer(
        songFileResolver = songFileResolver,
        mediaScanNotifier = mediaScanNotifier,
        libraryIndexer = libraryIndexer,
        songRepository = fakeSongRepository,
        playbackController = playbackController,
    )

    private val song1 = testSong("1")
    private val song2 = testSong("2")

    @Before
    fun setUp() {
        coEvery { songFileResolver.resolveFilePath(song1) } returns "/music/1.mp3"
        coEvery { songFileResolver.resolveFilePath(song2) } returns "/music/2.mp3"
        coEvery { mediaScanNotifier.awaitScan(any()) } just Runs
        coEvery { libraryIndexer.syncSongs(any()) } returns SyncResult(added = 1, removed = 0)
        every { playbackController.updateSongMetadata(any()) } just Runs
    }

    @Test
    fun onSongsSaved_notifiesMediaStoreThenReindexesThenRefreshesPlayback() = runTest {
        fakeSongRepository.emit(listOf(song1, song2))

        finalizer.onSongsSaved(listOf(song1, song2))

        coVerifyOrder {
            mediaScanNotifier.awaitScan(listOf("/music/1.mp3", "/music/2.mp3"))
            libraryIndexer.syncSongs(listOf(1L, 2L))
            playbackController.updateSongMetadata(song1)
            playbackController.updateSongMetadata(song2)
        }
    }

    @Test
    fun onSongsSaved_pushesTheReSyncedRowToPlayback_notTheStalePreEditSong() = runTest {
        // What the re-sync wrote back to Room -- the title the tag write changed.
        val reSynced = song1.copy(title = "Retitled")
        fakeSongRepository.emit(listOf(reSynced))

        finalizer.onSongsSaved(listOf(song1))

        verify(exactly = 1) { playbackController.updateSongMetadata(reSynced) }
    }

    @Test
    fun onSongsSaved_unresolvablePath_isSkippedFromTheScanButStillReindexed() = runTest {
        coEvery { songFileResolver.resolveFilePath(song1) } returns null
        fakeSongRepository.emit(listOf(song1, song2))

        finalizer.onSongsSaved(listOf(song1, song2))

        coVerify(exactly = 1) { mediaScanNotifier.awaitScan(listOf("/music/2.mp3")) }
        coVerify(exactly = 1) { libraryIndexer.syncSongs(listOf(1L, 2L)) }
    }

    @Test
    fun onSongsSaved_songGoneAfterReSync_isNotPushedToPlayback() = runTest {
        // The re-sync found the file deleted and removed its row -- nothing to refresh.
        fakeSongRepository.emit(emptyList())

        finalizer.onSongsSaved(listOf(song1))

        verify(exactly = 0) { playbackController.updateSongMetadata(any()) }
    }

    @Test
    fun onSongsSaved_emptyList_touchesNothing() = runTest {
        finalizer.onSongsSaved(emptyList())

        coVerify(exactly = 0) { songFileResolver.resolveFilePath(any()) }
        coVerify(exactly = 0) { mediaScanNotifier.awaitScan(any()) }
        coVerify(exactly = 0) { libraryIndexer.syncSongs(any()) }
    }

    @Test
    fun onSongsSaved_reindexFailure_isSwallowed() = runTest {
        // Best-effort contract: the file write already succeeded, so a re-index failure must not
        // propagate into the caller's save flow (it would masquerade as a failed save).
        coEvery { libraryIndexer.syncSongs(any()) } throws IOException("db locked")
        fakeSongRepository.emit(listOf(song1))

        finalizer.onSongsSaved(listOf(song1))

        verify(exactly = 0) { playbackController.updateSongMetadata(any()) }
    }
}

private fun testSong(id: String): Song = Song(
    id = id,
    title = "Song $id",
    artist = "Artist $id",
    album = "Album $id",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/$id",
    artworkUri = "content://media/external/audio/albumart/$id",
)
