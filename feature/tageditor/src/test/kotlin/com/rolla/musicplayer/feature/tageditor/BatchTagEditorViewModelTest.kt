package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.CustomTagRepositoryImpl
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.tagio.RollaTagsJson
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.core.testing.audio.AudioFixtures
import com.rolla.musicplayer.core.testing.audio.M4aFixture
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagWriter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
 *
 * Custom tags (phase 2): the lists come from the real [CustomTagRepositoryImpl] over [FakeCustomTagDao], and the
 * `ROLLA_TAGS` field is read and written by a spy on the real [RollaTagsFileIo]. The tests that add custom tags give
 * each song a real fixture file in a temporary folder as its editable copy and read the field back as it is on disk;
 * the other tests keep their nonexistent copies, which nothing opens while no custom tag is chosen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchTagEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val fakeSongRepository = FakeSongRepository()
    private val songFileResolver: SongFileResolver = mockk()
    private val tagWriter: TagWriter = mockk()
    private val tagSaveFinalizer: TagSaveFinalizer = mockk(relaxed = true)
    private val customTagRepository = CustomTagRepositoryImpl(
        customTagDao = FakeCustomTagDao(),
        sortDispatcher = mainDispatcherRule.testDispatcher,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )
    private val rollaTagsFileIo: RollaTagsFileIo = spyk(RollaTagsFileIo())

    private val song1 = testSong("1")
    private val song2 = testSong("2")
    private val song3 = testSong("3")

    private val createdViewModels = mutableListOf<BatchTagEditorViewModel>()

    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel -> viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun newViewModel(
        songIds: List<Long> = listOf(1L, 2L, 3L),
        writer: TagWriter = tagWriter,
    ): BatchTagEditorViewModel = BatchTagEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("songIds" to songIds)),
        songRepository = fakeSongRepository,
        songFileResolver = songFileResolver,
        tagWriter = writer,
        tagSaveFinalizer = tagSaveFinalizer,
        customTagRepository = customTagRepository,
        rollaTagsFileIo = rollaTagsFileIo,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    ).also { createdViewModels += it }

    /** Fills the TAG list with Acid (ID 1), Techno (2) and Peak time (3), and the SUBTAG list with Warm-up (1). */
    private suspend fun seedCustomTagLists() {
        listOf("Acid", "Techno", "Peak time").forEach { customTagRepository.create(CustomTagKind.TAG, it) }
        customTagRepository.create(CustomTagKind.SUBTAG, "Warm-up")
    }

    /**
     * A real [bytes] file named [name] in the temporary folder, made [song]'s editable copy, whose persist and
     * clean-up succeed without touching it. [ids], when given, are put in its `ROLLA_TAGS` field first.
     */
    private fun fixtureCopy(
        song: Song,
        name: String,
        bytes: ByteArray = AudioFixtures.mp3Bytes(),
        ids: CustomTagIds? = null,
    ): File {
        val copy = folder.newFile(name).apply { writeBytes(bytes) }
        ids?.let { RollaTagsFileIo().writeCustomTagIds(copy, it) }
        coEvery { songFileResolver.createEditableCopy(song) } returns copy
        coEvery { songFileResolver.persistEditedCopy(song, copy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(copy) } just Runs
        return copy
    }

    /** The text of every `ROLLA_TAGS` frame in the MP3 [file]'s ID3v2 tag, as it is on disk. */
    private fun rollaTagsOnDisk(file: File): List<String> {
        val tag = (AudioFileIO.read(file) as MP3File).iD3v2Tag ?: return emptyList()
        return tag.getFields(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO)
            .map { (it as AbstractID3v2Frame).body as FrameBodyTXXX }
            .filter { it.description == RollaTagsJson.FIELD_NAME }
            .map { it.text }
    }

    /** Chooses TAG [tagId] to add, clicks Save, grants consent and runs the batch to completion. */
    private suspend fun TestScope.addTagAndSave(viewModel: BatchTagEditorViewModel, tagId: Long) {
        advanceUntilIdle()
        viewModel.onCustomTagToggle(CustomTagKind.TAG, tagId)
        viewModel.onSaveClick()
        advanceUntilIdle()
        grantConsent(viewModel)
    }

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
            customTagRepository = customTagRepository,
            rollaTagsFileIo = rollaTagsFileIo,
            ioDispatcher = mainDispatcherRule.testDispatcher,
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
        assertEquals("None of the selected tracks could be found.", state.message)
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
        assertEquals("Edited 2 of 3 tracks.", state.message)
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
        assertEquals("Edited 2 of 3 tracks.", state.message)
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

    // -- custom tags: Save adds the chosen tags to every selected track ----------

    // Stub: write the chosen IDs only → "2". Mutation: write the chosen IDs only (no union with the file's) → RED.
    @Test
    fun batchSave_addsAndKeepsExisting() = runTest {
        seedCustomTagLists()
        fakeSongRepository.emit(listOf(song1))
        val copy = fixtureCopy(song1, "1_edit.mp3", ids = CustomTagIds(tagIds = setOf(1L), subTagIds = emptySet()))
        val viewModel = newViewModel(songIds = listOf(1L))

        addTagAndSave(viewModel, tagId = 2L)

        assertEquals(listOf("""{"tags":"1,2","subTags":""}"""), rollaTagsOnDisk(copy))
        assertTrue("the batch completes", viewModel.uiState.value.isClosed)
    }

    // Song B carries TAG 5 (unknown to the lists) and SUBTAG 1; choosing TAG 2 adds it and removes nothing. Stub and
    // mutation: write the chosen IDs only → TAG 5 and SUBTAG 1 lost → RED.
    @Test
    fun batchSave_neverRemoves() = runTest {
        seedCustomTagLists()
        fakeSongRepository.emit(listOf(song2))
        val copy = fixtureCopy(song2, "2_edit.mp3", ids = CustomTagIds(tagIds = setOf(5L), subTagIds = setOf(1L)))
        val viewModel = newViewModel(songIds = listOf(2L))

        addTagAndSave(viewModel, tagId = 2L)

        assertEquals(listOf("""{"tags":"2,5","subTags":"1"}"""), rollaTagsOnDisk(copy))
    }

    // No field switched on, one tag chosen: Save is offered and writes only ROLLA_TAGS. Control: nothing chosen, no
    // Save. Stub: canSave false. Mutation: canSave needs an applied field → RED on the canSave assertion.
    @Test
    fun batchSave_tagsOnly_enablesSave() = runTest {
        seedCustomTagLists()
        fakeSongRepository.emit(listOf(song1))
        val copy = fixtureCopy(song1, "1_edit.mp3")
        val viewModel = newViewModel(songIds = listOf(1L))
        advanceUntilIdle()
        assertEquals("nothing applied or chosen: no Save (control)", false, viewModel.uiState.value.canSave)

        viewModel.onCustomTagToggle(CustomTagKind.TAG, 2L)
        assertTrue("one chosen tag is enough to save", viewModel.uiState.value.canSave)
        viewModel.onSaveClick()
        advanceUntilIdle()
        grantConsent(viewModel)

        coVerify(exactly = 0) { tagWriter.writeFields(any(), any()) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song1, copy) }
        assertEquals(listOf("""{"tags":"2","subTags":""}"""), rollaTagsOnDisk(copy))
        assertTrue(viewModel.uiState.value.isClosed)
    }

    // An MP3 and an M4A, Title switched on and TAG 2 chosen: the M4A fails before any write, its title included (the
    // real TagWriter would change its bytes), and is reported. Stub and mutation: skip the M4A silently as a success →
    // no message ("Edited 2 of 2 tracks." is never shown: the screen closes) → RED.
    @Test
    fun batchSave_m4aCountsAsNotUpdated() = runTest {
        seedCustomTagLists()
        fakeSongRepository.emit(listOf(song1, song2))
        val mp3 = fixtureCopy(song1, "1_edit.mp3")
        val m4aBytes = M4aFixture.bytes(title = "Original")
        val m4a = fixtureCopy(song2, "2_edit.m4a", bytes = m4aBytes)
        val viewModel = newViewModel(songIds = listOf(1L, 2L), writer = TagWriter(mainDispatcherRule.testDispatcher))
        viewModel.onFieldValueChanged(TagField.TITLE, "New Title")
        viewModel.onFieldApplyToggled(TagField.TITLE, true)

        addTagAndSave(viewModel, tagId = 2L)

        val state = viewModel.uiState.value
        assertEquals("Edited 1 of 2 tracks.", state.message)
        assertEquals(false, state.isClosed)
        assertArrayEquals("the M4A copy is never written, title included", m4aBytes, m4a.readBytes())
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(song2, any()) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(m4a) }
        assertEquals("the MP3 is edited (control)", listOf("""{"tags":"2","subTags":""}"""), rollaTagsOnDisk(mp3))
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song1)) }
    }

    // Song 1 already carries TAG 2, song 2 does not; no field applied. Song 1's file would not change, so it is
    // neither written nor persisted, yet it counts as edited (no "Edited X of N" message). Stub: always write.
    // Mutation: drop the difference check → song 1 persisted → RED.
    @Test
    fun batchSave_alreadyCarried_skipsTheWrite() = runTest {
        seedCustomTagLists()
        fakeSongRepository.emit(listOf(song1, song2))
        val carried = CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet())
        val copy1 = fixtureCopy(song1, "1_edit.mp3", ids = carried)
        val copy2 = fixtureCopy(song2, "2_edit.mp3")
        val viewModel = newViewModel(songIds = listOf(1L, 2L))

        addTagAndSave(viewModel, tagId = 2L)

        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(song1, any()) }
        verify(exactly = 0) { rollaTagsFileIo.writeCustomTagIds(copy1, any()) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(copy1) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song2, copy2) }
        val state = viewModel.uiState.value
        assertNull("both count as edited", state.message)
        assertTrue(state.isClosed)
        // Only a file that changed is re-indexed.
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song2)) }
    }

    // "New tag" in the picker: a saved entry closes the dialog and is chosen at once. Stub and mutation: close the
    // dialog only → the new ID is not chosen → RED.
    @Test
    fun batchCreate_savedEntryIsChosen() = runTest {
        seedCustomTagLists()
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.onCustomTagRowsClick(CustomTagKind.SUBTAG)
        assertTrue("the row opens the picker", viewModel.uiState.value.isPickerOpen)
        assertEquals(CustomTagKind.SUBTAG, viewModel.uiState.value.picker.page)

        viewModel.onCreateCustomTagClick(CustomTagKind.SUBTAG, "Closing")
        viewModel.onCreateCustomTagConfirm("Closing")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("the dialog closes", state.nameDialog)
        assertEquals(CustomTagIds(tagIds = emptySet(), subTagIds = setOf(2L)), state.picker.selected)
        assertTrue("the new entry is listed", state.picker.subTags.any { it.name == "Closing" })
        assertTrue(state.canSave)
    }
}
