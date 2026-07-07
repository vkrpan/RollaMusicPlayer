package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

private const val API_29 = 29

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

private fun editableCopyFor(song: Song): File = File("cache/tag_editor_edits/${song.id}_edit.mp3")

/**
 * Unit tests for [BatchTagEditorViewModel]. Mirrors [TagEditorViewModelTest]'s house style:
 * [songFileResolver]/[tagWriter] are MockK mocks of concrete classes, [fakeSongRepository] is the
 * real in-memory [FakeSongRepository] from `:core:testing`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchTagEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSongRepository = FakeSongRepository()
    private val songFileResolver: SongFileResolver = mockk()
    private val tagWriter: TagWriter = mockk()
    private val tagSaveFinalizer: TagSaveFinalizer = mockk(relaxed = true)

    private val song1 = testSong("1")
    private val song2 = testSong("2")
    private val song3 = testSong("3")

    private val createdViewModels = mutableListOf<BatchTagEditorViewModel>()

    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel -> viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun newViewModel(songIds: List<Long> = listOf(1L, 2L, 3L)): BatchTagEditorViewModel =
        BatchTagEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("songIds" to songIds)),
            songRepository = fakeSongRepository,
            songFileResolver = songFileResolver,
            tagWriter = tagWriter,
            tagSaveFinalizer = tagSaveFinalizer,
        ).also { createdViewModels += it }

    /** Stubs a clean, all-succeed write+persist+delete cycle for every song in [songs]. */
    private fun stubSuccessfulBatch(songs: List<Song>, fields: Map<TagField, String>) {
        songs.forEach { song ->
            val copy = editableCopyFor(song)
            coEvery { songFileResolver.createEditableCopy(song) } returns copy
            coEvery { tagWriter.writeFields(copy, fields) } just Runs
            coEvery { songFileResolver.persistEditedCopy(song, copy) } just Runs
            coEvery { songFileResolver.deleteEditableCopy(copy) } just Runs
        }
    }

    /**
     * Stubs [songs][0] succeeding, [songs][1] throwing a [RecoverableSecurityException] on persist
     * (the API 29 recovery case), and [songs][2] succeeding -- shared by both Q-recovery tests.
     */
    private fun stubApi29RecoveryScenario(
        songs: List<Song>,
        fields: Map<TagField, String>,
    ): RecoverableSecurityException {
        songs.forEach { song ->
            coEvery { songFileResolver.createEditableCopy(song) } returns editableCopyFor(song)
            coEvery { tagWriter.writeFields(editableCopyFor(song), fields) } just Runs
        }
        coEvery { songFileResolver.persistEditedCopy(songs[0], editableCopyFor(songs[0])) } just Runs
        val recoverableException = mockk<RecoverableSecurityException>()
        coEvery { songFileResolver.persistEditedCopy(songs[1], editableCopyFor(songs[1])) } throws recoverableException
        coEvery { songFileResolver.persistEditedCopy(songs[2], editableCopyFor(songs[2])) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(any()) } just Runs
        return recoverableException
    }

    /** Verifies [song] went through the full write+persist+delete cycle exactly once, with [fields]. */
    private fun verifyFullyWritten(song: Song, fields: Map<TagField, String>) {
        val copy = editableCopyFor(song)
        coVerify(exactly = 1) { tagWriter.writeFields(copy, fields) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, copy) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(copy) }
    }

    /** Applies TITLE with [title], triggers save, and drains the test scheduler. */
    private suspend fun TestScope.applyTitleAndSave(viewModel: BatchTagEditorViewModel, title: String = "New Title") {
        viewModel.onFieldValueChanged(TagField.TITLE, title)
        viewModel.onFieldApplyToggled(TagField.TITLE, true)
        viewModel.onSaveClick()
        advanceUntilIdle()
    }

    /** Grants consent and drains the test scheduler. */
    private suspend fun TestScope.grantConsent(viewModel: BatchTagEditorViewModel) {
        viewModel.onConsentGranted()
        advanceUntilIdle()
    }

    // -- songCount / songIds reading -----------------------------------------

    @Test
    fun initialState_songCountReflectsRouteArgumentSize() = runTest {
        val viewModel = newViewModel(songIds = listOf(1L, 2L, 3L))

        assertEquals(3, viewModel.uiState.value.songCount)
    }

    @Test
    fun songIds_asLongArray_isReadRobustly() = runTest {
        val viewModel = BatchTagEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("songIds" to longArrayOf(1L, 2L, 3L))),
            songRepository = fakeSongRepository,
            songFileResolver = songFileResolver,
            tagWriter = tagWriter,
            tagSaveFinalizer = tagSaveFinalizer,
        ).also { createdViewModels += it }

        assertEquals(3, viewModel.uiState.value.songCount)
    }

    // -- field edits + apply-toggle gating ------------------------------------

    @Test
    fun onFieldApplyToggled_gatesCanSave() = runTest {
        val viewModel = newViewModel()
        viewModel.onFieldValueChanged(TagField.TITLE, "New Title")
        assertEquals(false, viewModel.uiState.value.canSave)

        viewModel.onFieldApplyToggled(TagField.TITLE, true)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onFieldApplyToggled(TagField.TITLE, false)
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    @Test
    fun onFieldApplyToggled_off_doesNotEraseTypedValue() = runTest {
        val viewModel = newViewModel()
        viewModel.onFieldValueChanged(TagField.TITLE, "New Title")
        viewModel.onFieldApplyToggled(TagField.TITLE, true)

        viewModel.onFieldApplyToggled(TagField.TITLE, false)

        assertEquals("New Title", viewModel.uiState.value.fields.title.value)
        assertEquals(false, viewModel.uiState.value.fields.title.applied)
    }

    @Test
    fun onFieldValueChanged_yearValidation_onlyEnforcedWhenApplied() = runTest {
        val viewModel = newViewModel()

        viewModel.onFieldValueChanged(TagField.YEAR, "20000")
        assertNull(viewModel.uiState.value.yearError)

        viewModel.onFieldApplyToggled(TagField.YEAR, true)
        assertTrue(viewModel.uiState.value.yearError != null)
        assertEquals(false, viewModel.uiState.value.canSave)

        viewModel.onFieldApplyToggled(TagField.YEAR, false)
        assertNull(viewModel.uiState.value.yearError)
    }

    @Test
    fun onFieldValueChanged_trackNumberValidation_onlyEnforcedWhenApplied() = runTest {
        val viewModel = newViewModel()

        viewModel.onFieldValueChanged(TagField.TRACK_NUMBER, "12a")
        assertNull(viewModel.uiState.value.trackNumberError)

        viewModel.onFieldApplyToggled(TagField.TRACK_NUMBER, true)
        assertTrue(viewModel.uiState.value.trackNumberError != null)
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    // -- save: consent request -------------------------------------------------

    @Test
    fun onSaveClick_nothingApplied_isNoOp() = runTest {
        val viewModel = newViewModel()

        viewModel.onSaveClick()

        assertNull(viewModel.uiState.value.consentRequest)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun onSaveClick_appliedAndValid_exposesOneConsentRequestWithAllUris() = runTest {
        fakeSongRepository.emit(listOf(song1, song2, song3))
        val viewModel = newViewModel()

        applyTitleAndSave(viewModel)

        val expectedUris = listOf(song1.contentUri, song2.contentUri, song3.contentUri)
        assertEquals(expectedUris, viewModel.uiState.value.consentRequest)
        assertTrue(viewModel.uiState.value.isSaving)
        coVerify(exactly = 0) { tagWriter.writeFields(any(), any()) }
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(any(), any()) }
    }

    @Test
    fun onSaveClick_someIdsMissing_areDroppedFromConsentRequestButNotSongCount() = runTest {
        // song2's id (2L) is requested but never emitted -- simulates a since-deleted song.
        fakeSongRepository.emit(listOf(song1, song3))
        val viewModel = newViewModel(songIds = listOf(1L, 2L, 3L))

        applyTitleAndSave(viewModel)

        assertEquals(3, viewModel.uiState.value.songCount)
        assertEquals(listOf(song1.contentUri, song3.contentUri), viewModel.uiState.value.consentRequest)
    }

    @Test
    fun onSaveClick_noSongsResolved_closesWithMessageAndWritesNothing() = runTest {
        // Nothing emitted -- every id is missing.
        val viewModel = newViewModel(songIds = listOf(1L, 2L, 3L))

        applyTitleAndSave(viewModel)

        val state = viewModel.uiState.value
        assertEquals(false, state.isSaving)
        assertTrue(state.isClosed)
        assertEquals("None of the selected songs could be found.", state.message)
        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
    }

    // -- save: granted, full success -------------------------------------------

    @Test
    fun onConsentGranted_writesOnlyAppliedFieldsPerSong_progressAdvances_cleansUpTemps_closesOnFullSuccess() = runTest {
        val songs = listOf(song1, song2, song3)
        fakeSongRepository.emit(songs)
        val fields = mapOf(TagField.TITLE to "New Title")
        stubSuccessfulBatch(songs, fields)
        lateinit var viewModel: BatchTagEditorViewModel
        val progressSnapshots = mutableListOf<BatchProgress?>()
        songs.forEach { song ->
            coEvery { songFileResolver.createEditableCopy(song) } coAnswers {
                progressSnapshots += viewModel.uiState.value.progress
                editableCopyFor(song)
            }
        }

        viewModel = newViewModel()
        applyTitleAndSave(viewModel)
        grantConsent(viewModel)

        // song1 starts with no prior progress; song2/song3 start after 1 and 2 prior completions.
        assertEquals(listOf(null, BatchProgress(1, 3), BatchProgress(2, 3)), progressSnapshots)
        songs.forEach { song -> verifyFullyWritten(song, fields) }
        // One re-index for the whole batch, carrying every song that was actually written.
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(songs) }
        val state = viewModel.uiState.value
        assertNull(state.progress)
        assertTrue(state.isClosed)
        assertNull(state.message)
        assertEquals(false, state.isSaving)
    }

    @Test
    fun onConsentGranted_withoutPriorSaveClick_isNoOp() = runTest {
        val viewModel = newViewModel()

        viewModel.onConsentGranted()
        advanceUntilIdle()

        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
    }

    @Test
    fun onConsentGranted_multipleFieldsApplied_onlyAppliedFieldsWritten_unappliedTypedValueExcluded() = runTest {
        fakeSongRepository.emit(listOf(song1))
        val copy = editableCopyFor(song1)
        val expectedFields = mapOf(TagField.TITLE to "New Title", TagField.GENRE to "Jazz")
        coEvery { songFileResolver.createEditableCopy(song1) } returns copy
        coEvery { tagWriter.writeFields(copy, expectedFields) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song1, copy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(copy) } just Runs

        val viewModel = newViewModel(songIds = listOf(1L))
        viewModel.onFieldValueChanged(TagField.TITLE, "New Title")
        viewModel.onFieldApplyToggled(TagField.TITLE, true)
        viewModel.onFieldValueChanged(TagField.GENRE, "Jazz")
        viewModel.onFieldApplyToggled(TagField.GENRE, true)
        // Typed but never applied -- must be entirely absent from the write map, not written blank.
        viewModel.onFieldValueChanged(TagField.ARTIST, "Should Not Be Written")
        viewModel.onSaveClick()
        advanceUntilIdle()
        grantConsent(viewModel)

        coVerify(exactly = 1) { tagWriter.writeFields(copy, expectedFields) }
        assertTrue(viewModel.uiState.value.isClosed)
    }

    @Test
    fun onConsentGranted_finalizerRunsBeforeIsClosedFlipsOnFullSuccess() = runTest {
        val songs = listOf(song1, song2, song3)
        fakeSongRepository.emit(songs)
        val fields = mapOf(TagField.TITLE to "New Title")
        stubSuccessfulBatch(songs, fields)
        lateinit var viewModel: BatchTagEditorViewModel
        var isClosedDuringFinalize: Boolean? = null
        coEvery { tagSaveFinalizer.onSongsSaved(songs) } coAnswers {
            isClosedDuringFinalize = viewModel.uiState.value.isClosed
        }

        viewModel = newViewModel()
        applyTitleAndSave(viewModel)
        grantConsent(viewModel)

        assertEquals(false, isClosedDuringFinalize)
        assertTrue(viewModel.uiState.value.isClosed)
    }

    // -- save: per-file failure continues --------------------------------------

    @Test
    fun onConsentGranted_oneSongFailsWrite_continuesRestAndReportsPartialFailure() = runTest {
        val songs = listOf(song1, song2, song3)
        fakeSongRepository.emit(songs)
        val fields = mapOf(TagField.TITLE to "New Title")
        songs.forEach { song -> coEvery { songFileResolver.createEditableCopy(song) } returns editableCopyFor(song) }
        coEvery { tagWriter.writeFields(editableCopyFor(song1), fields) } just Runs
        coEvery { tagWriter.writeFields(editableCopyFor(song2), fields) } throws IOException("disk full")
        coEvery { tagWriter.writeFields(editableCopyFor(song3), fields) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song1, editableCopyFor(song1)) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song3, editableCopyFor(song3)) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(any()) } just Runs

        val viewModel = newViewModel()
        applyTitleAndSave(viewModel)
        grantConsent(viewModel)

        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(song2, any()) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(editableCopyFor(song2)) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song3, editableCopyFor(song3)) }
        // Only the songs whose files actually changed are re-indexed -- song2 was never written.
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1, song3)) }
        val state = viewModel.uiState.value
        assertEquals(false, state.isSaving)
        assertEquals(false, state.isClosed)
        assertEquals("Edited 2 of 3 songs.", state.message)
        assertNull(state.progress)
    }

    // -- save: API 29 recovery pauses then resumes -----------------------------

    @Test
    fun onConsentGranted_recoverableSecurityExceptionOnApi29_pausesThenResumesRestOfBatch() = runTest {
        val songs = listOf(song1, song2, song3)
        fakeSongRepository.emit(songs)
        val fields = mapOf(TagField.TITLE to "New Title")
        val recoverableException = stubApi29RecoveryScenario(songs, fields)

        val viewModel = newViewModel()
        viewModel.sdkIntProvider = { API_29 }
        applyTitleAndSave(viewModel)
        grantConsent(viewModel)

        assertEquals(recoverableException, viewModel.uiState.value.recoveryRequest)
        assertTrue(viewModel.uiState.value.isSaving)
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song1, any()) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song2, any()) }
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(song3, any()) }

        // Screen resolved the recovery prompt; retry the persist only for song2, then batch resumes.
        coEvery { songFileResolver.persistEditedCopy(song2, editableCopyFor(song2)) } just Runs
        viewModel.onRecoveryGranted()
        advanceUntilIdle()

        coVerify(exactly = 2) { songFileResolver.persistEditedCopy(song2, any()) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song3, any()) }
        // song2's recovered retry counts as written, in completion order.
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1, song2, song3)) }
        val state = viewModel.uiState.value
        assertNull(state.recoveryRequest)
        assertTrue(state.isClosed)
        assertNull(state.message)
        assertEquals(false, state.isSaving)
    }

    @Test
    fun onConsentDenied_duringRecovery_skipsOnlyThatSongAndContinuesRest() = runTest {
        val songs = listOf(song1, song2, song3)
        fakeSongRepository.emit(songs)
        val fields = mapOf(TagField.TITLE to "New Title")
        stubApi29RecoveryScenario(songs, fields)

        val viewModel = newViewModel()
        viewModel.sdkIntProvider = { API_29 }
        applyTitleAndSave(viewModel)
        grantConsent(viewModel)

        viewModel.onConsentDenied()
        advanceUntilIdle()

        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(editableCopyFor(song2)) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song3, editableCopyFor(song3)) }
        // The recovery-declined song2 was never written, so it is excluded from the re-index.
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1, song3)) }
        val state = viewModel.uiState.value
        assertNull(state.recoveryRequest)
        assertEquals(false, state.isClosed)
        assertEquals("Edited 2 of 3 songs.", state.message)
        assertEquals(false, state.isSaving)
    }

    @Test
    fun onRecoveryGranted_withoutPendingRecoveryRequest_isNoOp() = runTest {
        val viewModel = newViewModel()

        viewModel.onRecoveryGranted()
        advanceUntilIdle()

        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(any(), any()) }
    }

    // -- save: initial consent denied -------------------------------------------

    @Test
    fun onConsentDenied_initialPrompt_writesNothingAndStaysOpen() = runTest {
        fakeSongRepository.emit(listOf(song1))
        val viewModel = newViewModel(songIds = listOf(1L))
        applyTitleAndSave(viewModel)

        viewModel.onConsentDenied()
        advanceUntilIdle()

        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
        coVerify(exactly = 0) { tagWriter.writeFields(any(), any()) }
        coVerify(exactly = 0) { tagSaveFinalizer.onSongsSaved(any()) }
        val state = viewModel.uiState.value
        assertEquals(false, state.isSaving)
        assertEquals("Changes weren't saved — permission declined.", state.message)
        assertEquals(false, state.isClosed)
        assertNull(state.consentRequest)
    }

    // -- dismissMessage ----------------------------------------------------------

    @Test
    fun dismissMessage_clearsMessage() = runTest {
        val viewModel = newViewModel(songIds = listOf(1L))
        applyTitleAndSave(viewModel)
        // Nothing emitted for id 1 -- resolves to no songs, which sets a message.
        assertTrue(viewModel.uiState.value.message != null)

        viewModel.dismissMessage()

        assertNull(viewModel.uiState.value.message)
    }
}
