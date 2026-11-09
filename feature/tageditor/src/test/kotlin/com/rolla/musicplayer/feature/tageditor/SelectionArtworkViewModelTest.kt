package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.feature.tageditor.io.ArtworkLoader
import com.rolla.musicplayer.feature.tageditor.io.PickedArtwork
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagWriter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

private const val PICKED_IMAGE_URI = "content://media/picker/0/com.android.providers.media.photopicker/media/77"
private const val API_29 = 29

private fun song(id: String): Song = Song(
    id = id,
    title = "Song $id",
    artist = "Artist $id",
    album = "Album $id",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2020,
    contentUri = "content://media/external/audio/media/$id",
    artworkUri = "rolla-artwork://song/$id?v=1",
)

private fun mp3CopyFor(song: Song): File = File("cache/tag_editor_edits/${song.id}_edit.mp3")

/**
 * [SelectionArtworkViewModel]: the Tracks tab's multi-track Set / Remove artwork (spec §5.5, plan Task 8). House style
 * of [BatchTagEditorViewModelTest]: MockK mocks of the concrete IO classes, the real [FakeSongRepository]. The main
 * dispatcher is a [StandardTestDispatcher], so each step runs only when the test drains the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectionArtworkViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val fakeSongRepository = FakeSongRepository()
    private val songFileResolver: SongFileResolver = mockk()
    private val tagWriter: TagWriter = mockk()
    private val artworkLoader: ArtworkLoader = mockk()
    private val tagSaveFinalizer: TagSaveFinalizer = mockk(relaxed = true)

    private val song1 = song("9542")
    private val song2 = song("9543")
    private val song3 = song("9544")
    private val songs = listOf(song1, song2, song3)
    private val ids = songs.map { it.id.toLong() }
    private val artwork = PickedArtwork(bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg", width = 1, height = 1)

    private val createdViewModels = mutableListOf<SelectionArtworkViewModel>()

    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel -> viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun newViewModel(): SelectionArtworkViewModel = SelectionArtworkViewModel(
        songRepository = fakeSongRepository,
        songFileResolver = songFileResolver,
        tagWriter = tagWriter,
        artworkLoader = artworkLoader,
        tagSaveFinalizer = tagSaveFinalizer,
    ).also { createdViewModels += it }

    /** The library holds [songs]; each copies to an MP3, writes and persists cleanly; the picked image loads. */
    private fun stubLibrary() {
        fakeSongRepository.emit(songs)
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns artwork
        songs.forEach { song ->
            coEvery { songFileResolver.createEditableCopy(song) } returns mp3CopyFor(song)
            coEvery { songFileResolver.persistEditedCopy(song, mp3CopyFor(song)) } just Runs
        }
        coEvery { songFileResolver.deleteEditableCopy(any()) } just Runs
        coEvery { tagWriter.writeArtwork(any(), any()) } just Runs
        coEvery { tagWriter.removeArtwork(any()) } just Runs
    }

    /** Picks the image for [songIds], then grants the one consent prompt; drains the scheduler after each step. */
    private fun TestScope.setAndGrant(viewModel: SelectionArtworkViewModel, songIds: List<Long> = ids) {
        viewModel.onSetArtwork(songIds, PICKED_IMAGE_URI)
        advanceUntilIdle()
        viewModel.onConsentGranted()
        advanceUntilIdle()
    }

    // Spec §5.5: one prompt for every selected file, all written, the finalizer runs once, the selection clears.
    // Trivial stub: no consent request and no message → RED on the consent assertion. Mutation: drop the
    // onSongsSaved call → RED on the finalizer verify.
    @Test
    fun setArtwork_allSucceed_messageAndEmptyFailedSet() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val viewModel = newViewModel()

        viewModel.onSetArtwork(ids, PICKED_IMAGE_URI)
        advanceUntilIdle()
        assertEquals(
            "one consent request covers every selected track",
            songs.map(Song::contentUri),
            viewModel.uiState.value.consentRequest,
        )
        assertTrue("the selection bar shows progress while the consent prompt is up", viewModel.uiState.value.isWorking)

        viewModel.onConsentGranted()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("the all-succeeded message", "Artwork set on 3 tracks", state.message)
        assertEquals("nothing failed, so the selection clears", SelectionArtworkResult(emptySet()), state.result)
        assertFalse("work is over", state.isWorking)
        songs.forEach { song ->
            coVerify(exactly = 1) { tagWriter.writeArtwork(mp3CopyFor(song), artwork) }
            coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, mp3CopyFor(song)) }
            coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(mp3CopyFor(song)) }
        }
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(songs) }
    }

    // Spec §5.5: a failure on one file does not stop the others; the message is K of N and only the failed track stays
    // selected. Trivial stub: no message → RED. Mutation: count a persist failure as succeeded → "Artwork set on 3
    // tracks" → RED.
    @Test
    fun setArtwork_oneFails_kOfNAndKeepsTheFailed() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        coEvery { songFileResolver.persistEditedCopy(song3, mp3CopyFor(song3)) } throws IOException("disk full")
        val viewModel = newViewModel()

        setAndGrant(viewModel)

        val state = viewModel.uiState.value
        assertEquals("the K-of-N message", "Artwork set on 2 of 3 tracks", state.message)
        assertEquals("only 9544 failed", SelectionArtworkResult(setOf("9544")), state.result)
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1, song2)) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(mp3CopyFor(song3)) }
    }

    // Review Focus 4 / spec goal 9: a WAV is not written (the copy's extension decides) and counts as failed, so it
    // stays selected. Trivial stub: no message → RED. Mutation: drop the supportsArtwork() skip → the WAV is written
    // and the message says 3 tracks → RED.
    @Test
    fun setArtwork_unsupportedFormat_countsAsFailedAndStaysSelected() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val wavCopy = File("cache/tag_editor_edits/x.wav")
        coEvery { songFileResolver.createEditableCopy(song2) } returns wavCopy
        coEvery { songFileResolver.persistEditedCopy(song2, wavCopy) } just Runs
        val viewModel = newViewModel()

        setAndGrant(viewModel)

        val state = viewModel.uiState.value
        assertEquals("the WAV counts as not written", "Artwork set on 2 of 3 tracks", state.message)
        assertEquals("the WAV stays selected", SelectionArtworkResult(setOf("9543")), state.result)
        coVerify(exactly = 0) { tagWriter.writeArtwork(wavCopy, any()) }
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(song2, wavCopy) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(wavCopy) }
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1, song3)) }
    }

    // Spec §5.7: N uses tracksCountLabel, so one track reads "1 track"; Remove calls removeArtwork, never writeArtwork.
    // Trivial stub: no message → RED. Mutation: "$n tracks" instead of tracksCountLabel → "1 tracks" → RED.
    @Test
    fun removeArtwork_singleTrack_usesSingular() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val viewModel = newViewModel()

        viewModel.onRemoveArtwork(listOf(9542L))
        advanceUntilIdle()
        assertEquals(
            "one consent request for the one track",
            listOf(song1.contentUri),
            viewModel.uiState.value.consentRequest,
        )
        viewModel.onConsentGranted()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("the singular message", "Artwork removed from 1 track", state.message)
        assertEquals("nothing failed", SelectionArtworkResult(emptySet()), state.result)
        coVerify(exactly = 1) { tagWriter.removeArtwork(mp3CopyFor(song1)) }
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
    }

    // Spec §5.5: declining the one prompt writes nothing and keeps the selection (no result). Trivial stub: no message
    // → RED. Mutation: the decline publishes an empty result (which would clear the selection) → RED.
    @Test
    fun declined_writesNothingAndKeepsSelection() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val viewModel = newViewModel()
        viewModel.onSetArtwork(ids, PICKED_IMAGE_URI)
        advanceUntilIdle()
        assertTrue("control: the consent prompt is requested", viewModel.uiState.value.consentRequest != null)

        viewModel.onConsentDenied()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("the declined message", "Changes weren't saved — permission declined.", state.message)
        assertNull("no result: the selection stays as it is", state.result)
        assertNull("the consent request is consumed", state.consentRequest)
        assertFalse("work is over", state.isWorking)
        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
        coVerify(exactly = 0) { tagSaveFinalizer.onSongsSaved(any()) }
    }

    // ArtworkLoader returns null for an unusable pick: the message shows and nothing else happens. Trivial stub: no
    // message → RED. Mutation: request consent even when the load fails → consentRequest is set → RED.
    @Test
    fun unusableImage_noConsentRequested() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns null
        val viewModel = newViewModel()

        viewModel.onSetArtwork(ids, PICKED_IMAGE_URI)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("the unusable-image message", "Couldn't use that image as artwork.", state.message)
        assertNull("no consent prompt for an unusable image", state.consentRequest)
        assertNull("no result", state.result)
        assertFalse("work is over", state.isWorking)
        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
    }

    // Ruling P25: when none of the selected ids is in the library any more (the race where every selected track
    // vanished), the batch editor's existing copy shows and an empty failed set clears the selection; nothing is asked
    // or written. Control: the library holds other songs. Trivial stub: no message → RED. Mutation: no result on this
    // path (the stale ids would stay selected) → RED.
    @Test
    fun unknownIds_noTracksFoundAndSelectionCleared() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val viewModel = newViewModel()

        viewModel.onRemoveArtwork(listOf(1L, 2L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("the not-found message", "None of the selected tracks could be found.", state.message)
        assertEquals("the stale ids are dropped from the selection", SelectionArtworkResult(emptySet()), state.result)
        assertNull("no consent prompt", state.consentRequest)
        assertFalse("work is over", state.isWorking)
        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
    }

    // BatchTagEditorViewModel's API 29 semantics: declining one song's recovery prompt counts only that song as not
    // written (its edited copy deleted) and the loop resumes with the next. Trivial stub: no recovery request → RED.
    // Mutation: a recovery decline aborts the rest (song 3 never written) → RED on the message.
    @Test
    fun api29RecoveryDeclined_onlyThatSongFailsAndTheLoopResumes() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val recoverable = mockk<RecoverableSecurityException>()
        coEvery { songFileResolver.persistEditedCopy(song2, mp3CopyFor(song2)) } throws recoverable
        val viewModel = newViewModel().apply { sdkIntProvider = { API_29 } }

        setAndGrant(viewModel)
        assertSame("song 2's write asks for API 29 recovery", recoverable, viewModel.uiState.value.recoveryRequest)

        viewModel.onConsentDenied()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("only song 2 is not written", "Artwork set on 2 of 3 tracks", state.message)
        assertEquals("song 2 stays selected", SelectionArtworkResult(setOf("9543")), state.result)
        assertNull("the recovery request is consumed", state.recoveryRequest)
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(mp3CopyFor(song2)) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song3, mp3CopyFor(song3)) }
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1, song3)) }
    }

    // BatchTagEditorViewModel's API 29 semantics: a RecoverableSecurityException pauses the loop on that song; granting
    // recovery retries only its persist (no new copy, no second write), then the loop resumes. Trivial stub: no
    // recovery request → RED. Mutation: onRecoveryGranted restarts the loop at index 0 → song 1 is persisted twice
    // → RED.
    @Test
    fun api29Recovery_retriesOnlyThatSong() = runTest(mainDispatcherRule.testDispatcher) {
        stubLibrary()
        val recoverable = mockk<RecoverableSecurityException>()
        var song2Persists = 0
        coEvery { songFileResolver.persistEditedCopy(song2, mp3CopyFor(song2)) } coAnswers {
            if (song2Persists++ == 0) throw recoverable
        }
        val viewModel = newViewModel().apply { sdkIntProvider = { API_29 } }

        setAndGrant(viewModel)
        assertSame("song 2's write asks for API 29 recovery", recoverable, viewModel.uiState.value.recoveryRequest)
        assertNull("no message while paused", viewModel.uiState.value.message)
        coVerify(exactly = 0) { songFileResolver.createEditableCopy(song3) }

        viewModel.onRecoveryGranted()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all three written after recovery", "Artwork set on 3 tracks", state.message)
        assertEquals("nothing failed", SelectionArtworkResult(emptySet()), state.result)
        assertNull("the recovery request is consumed", state.recoveryRequest)
        coVerify(exactly = 1) { songFileResolver.createEditableCopy(song2) }
        coVerify(exactly = 1) { tagWriter.writeArtwork(mp3CopyFor(song2), artwork) }
        coVerify(exactly = 2) { songFileResolver.persistEditedCopy(song2, mp3CopyFor(song2)) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song1, mp3CopyFor(song1)) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song3, mp3CopyFor(song3)) }
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(songs) }
    }
}
