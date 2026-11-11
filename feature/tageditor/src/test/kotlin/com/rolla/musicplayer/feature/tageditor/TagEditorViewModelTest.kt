package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.CustomTagRepositoryImpl
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.tagio.RollaTagsJson
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.core.testing.audio.AudioFixtures
import com.rolla.musicplayer.core.testing.audio.M4aFixture
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.feature.tageditor.io.ArtworkLoader
import com.rolla.musicplayer.feature.tageditor.io.PickedArtwork
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagReader
import com.rolla.musicplayer.feature.tageditor.io.TagWriter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

private const val SONG_ID_LONG = 1L
private const val SONG_ID = "1"

/**
 * Unit tests for [TagEditorViewModel].
 *
 * [songFileResolver]/[tagReader]/[tagWriter] are mocked with MockK -- all three are concrete
 * classes (see their KDoc), never interfaces, but MockK mocks concrete classes elsewhere in this
 * codebase too (e.g. `PlaybackController` in `PlaylistDetailViewModelTest`), so this follows that
 * same established pattern rather than introducing fakes for them.
 *
 * [songRepository] is the real [FakeSongRepository] from `:core:testing`, per the task brief.
 *
 * Custom tags (phase 2): the lists come from the real [CustomTagRepositoryImpl] over [FakeCustomTagDao], so names are
 * validated as in the app, and the `ROLLA_TAGS` field is read and written by a spy on the real [RollaTagsFileIo]. The
 * editable copy is therefore a real fixture file in a temporary folder (an MP3 unless a test passes another), whose
 * field the custom tag tests read back as it is on disk; the field and artwork writers stay mocked.
 */
// LargeClass: one test class per ViewModel, as in the rest of the codebase; the custom tag tests share its fixtures.
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class TagEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val fakeSongRepository = FakeSongRepository()
    private val songFileResolver: SongFileResolver = mockk()
    private val tagReader: TagReader = mockk()
    private val tagWriter: TagWriter = mockk()
    private val artworkLoader: ArtworkLoader = mockk()
    private val tagSaveFinalizer: TagSaveFinalizer = mockk(relaxed = true)
    private val customTagDao = FakeCustomTagDao()
    private val customTagRepository = CustomTagRepositoryImpl(
        customTagDao = customTagDao,
        sortDispatcher = mainDispatcherRule.testDispatcher,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )
    private val rollaTagsFileIo: RollaTagsFileIo = spyk(RollaTagsFileIo())

    private val song = testSong()
    private val editableCopy: File by lazy { fixtureFile("${SONG_ID}_edit.mp3", AudioFixtures.mp3Bytes()) }
    private val loadedTags = testTags()

    // Mirrors EqualizerViewModelTest's teardown: cancels every ViewModel's viewModelScope so no
    // in-flight coroutine from one test can resume during a later test's MainDispatcherRule swap.
    private val createdViewModels = mutableListOf<TagEditorViewModel>()

    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel -> viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun newViewModel(
        songId: Long = SONG_ID_LONG,
        repository: CustomTagRepository = customTagRepository,
        reader: TagReader = tagReader,
        writer: TagWriter = tagWriter,
    ): TagEditorViewModel = TagEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("songId" to songId)),
        songRepository = fakeSongRepository,
        songFileResolver = songFileResolver,
        tagReader = reader,
        tagWriter = writer,
        artworkLoader = artworkLoader,
        tagSaveFinalizer = tagSaveFinalizer,
        customTagRepository = repository,
        rollaTagsFileIo = rollaTagsFileIo,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    ).also { createdViewModels += it }

    private fun fixtureFile(name: String, bytes: ByteArray): File = folder.newFile(name).apply { writeBytes(bytes) }

    /** Fills the TAG list with Acid (ID 1), Techno (2) and Peak time (3), and the SUBTAG list with Warm-up (1). */
    private suspend fun seedCustomTagLists() {
        listOf("Acid", "Techno", "Peak time").forEach { customTagRepository.create(CustomTagKind.TAG, it) }
        customTagRepository.create(CustomTagKind.SUBTAG, "Warm-up")
    }

    /** Puts [ids] into [file]'s `ROLLA_TAGS` field, as another program or an earlier save would have. */
    private fun writeFileIds(file: File, ids: CustomTagIds) = RollaTagsFileIo().writeCustomTagIds(file, ids)

    /**
     * Gives the MP3 [file] an ID3v2.4 tag whose only frame is a `ROLLA_TAGS` user-defined text frame holding [value]
     * verbatim, as a hand edit or another program might leave it (not necessarily what [RollaTagsJson] would write).
     */
    private fun writeRawRollaTags(file: File, value: String) {
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        val mp3 = AudioFileIO.read(file) as MP3File
        val tag = ID3v24Tag()
        tag.setField(
            tag.createFrame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO).apply {
                body = FrameBodyTXXX(TextEncoding.ISO_8859_1, RollaTagsJson.FIELD_NAME, value)
            },
        )
        mp3.setID3v2Tag(tag)
        mp3.commit()
    }

    /** Stubs a persist and clean-up of [copy] that succeed, without touching the file. */
    private fun stubPersist(copy: File) {
        coEvery { songFileResolver.persistEditedCopy(song, copy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(copy) } just Runs
    }

    /** The text of every `ROLLA_TAGS` frame in the MP3 [file]'s ID3v2 tag, as it is on disk. */
    private fun rollaTagsOnDisk(file: File): List<String> {
        val tag = (AudioFileIO.read(file) as MP3File).iD3v2Tag ?: return emptyList()
        return tag.getFields(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO)
            .map { (it as AbstractID3v2Frame).body as FrameBodyTXXX }
            .filter { it.description == RollaTagsJson.FIELD_NAME }
            .map { it.text }
    }

    /**
     * Emits [song], stubs a successful copy+read of [copy] whose tag embeds a picture when [hasArtwork], and returns a
     * constructed, loaded ViewModel.
     */
    private fun TestScope.loadedViewModel(
        copy: File = editableCopy,
        hasArtwork: Boolean = true,
        repository: CustomTagRepository = customTagRepository,
    ): TagEditorViewModel {
        fakeSongRepository.emit(listOf(song))
        coEvery { songFileResolver.createEditableCopy(song) } returns copy
        coEvery { tagReader.read(copy) } returns loadedTags
        coEvery { tagReader.hasArtwork(copy) } returns hasArtwork
        val viewModel = newViewModel(repository = repository)
        advanceUntilIdle()
        return viewModel
    }

    /** Stubs a successful field write, artwork write or removal, persist and clean-up of [editableCopy]. */
    private fun stubSuccessfulSave() {
        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { tagWriter.writeArtwork(editableCopy, any()) } just Runs
        coEvery { tagWriter.removeArtwork(editableCopy) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs
    }

    /** Clicks Save, grants consent and runs the write to completion. */
    private fun TestScope.saveWithConsent(viewModel: TagEditorViewModel) {
        viewModel.onSaveClick()
        viewModel.onConsentGranted()
        advanceUntilIdle()
    }

    // ── load ──────────────────────────────────────────────────────────────

    @Test
    fun uiState_songPresent_populatesFieldsFromReader() = runTest {
        val viewModel = loadedViewModel()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isLoading)
            assertEquals(false, state.loadFailed)
            assertEquals(song.title, state.songTitle)
            assertEquals(song.artist, state.artistName)
            assertEquals(song.artworkUri, state.artworkUri)
            assertEquals(loadedTags, state.tags)
            assertEquals(false, state.isDirty)
            assertEquals(false, state.canSave)
        }
    }

    @Test
    fun uiState_songNotFound_isClosedWithMessage() = runTest {
        // Nothing emitted into fakeSongRepository -- observeSong resolves to null.
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isLoading)
            assertTrue(state.isClosed)
            assertEquals("Track not found.", state.message)
        }
    }

    @Test
    fun uiState_readFailure_showsErrorAndDisablesSave() = runTest {
        fakeSongRepository.emit(listOf(song))
        coEvery { songFileResolver.createEditableCopy(song) } returns editableCopy
        coEvery { tagReader.read(editableCopy) } throws IOException("corrupt file")

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isLoading)
            assertTrue(state.loadFailed)
            assertEquals("Couldn't read tags for this file.", state.message)
            assertEquals(false, state.isClosed)
        }

        // Even if the user then types something, saving must stay disabled -- there is no
        // trustworthy baseline to diff against.
        viewModel.onFieldChanged(TagField.TITLE, "Anything")
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    // ── field edits + validation ─────────────────────────────────────────

    @Test
    fun onFieldChanged_differentValue_marksDirtyAndEnablesSave() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onFieldChanged(TagField.TITLE, "New Title")

        val state = viewModel.uiState.value
        assertEquals("New Title", state.tags.title)
        assertTrue(state.isDirty)
        assertTrue(state.canSave)
    }

    @Test
    fun onFieldChanged_sameValueAsLoaded_isNotDirty() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onFieldChanged(TagField.TITLE, loadedTags.title)

        val state = viewModel.uiState.value
        assertEquals(false, state.isDirty)
        assertEquals(false, state.canSave)
    }

    @Test
    fun onFieldChanged_yearValidation() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onFieldChanged(TagField.YEAR, "1999")
        assertNull(viewModel.uiState.value.yearError)

        viewModel.onFieldChanged(TagField.YEAR, "")
        assertNull(viewModel.uiState.value.yearError)

        viewModel.onFieldChanged(TagField.YEAR, "20000")
        assertTrue(viewModel.uiState.value.yearError != null)
        assertEquals(false, viewModel.uiState.value.canSave)

        viewModel.onFieldChanged(TagField.YEAR, "19ab")
        assertTrue(viewModel.uiState.value.yearError != null)
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    @Test
    fun onFieldChanged_trackNumberValidation() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onFieldChanged(TagField.TRACK_NUMBER, "12")
        assertNull(viewModel.uiState.value.trackNumberError)

        viewModel.onFieldChanged(TagField.TRACK_NUMBER, "")
        assertNull(viewModel.uiState.value.trackNumberError)

        viewModel.onFieldChanged(TagField.TRACK_NUMBER, "12a")
        assertTrue(viewModel.uiState.value.trackNumberError != null)
        assertEquals(false, viewModel.uiState.value.canSave)
    }

    // ── artwork picking ──────────────────────────────────────────────────

    @Test
    fun onArtworkPicked_validImage_previewsItAndEnablesSave() = runTest {
        val viewModel = loadedViewModel()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns pickedArtwork()

        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PICKED_IMAGE_URI, state.pendingArtworkUri)
        assertTrue(state.isDirty)
        assertTrue(state.canSave)
    }

    @Test
    fun onArtworkPicked_unusableImage_showsMessageAndStaysClean() = runTest {
        val viewModel = loadedViewModel()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns null

        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Couldn't use that image as artwork.", state.message)
        assertNull(state.pendingArtworkUri)
        assertEquals(false, state.isDirty)
        assertEquals(false, state.canSave)
    }

    @Test
    fun onArtworkPicked_whileSaving_isIgnored() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick() // isSaving = true; consent not yet resolved.

        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()

        coVerify(exactly = 0) { artworkLoader.load(any()) }
        assertNull(viewModel.uiState.value.pendingArtworkUri)
    }

    @Test
    fun onArtworkPicked_thenSave_writesFieldsThenEmbedsArtworkIntoTheSameCopy() = runTest {
        val viewModel = loadedViewModel()
        val artwork = pickedArtwork()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns artwork
        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()
        viewModel.onSaveClick()

        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { tagWriter.writeArtwork(editableCopy, artwork) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs

        viewModel.onConsentGranted()
        advanceUntilIdle()

        coVerify(exactly = 1) { tagWriter.write(editableCopy, any()) }
        coVerify(exactly = 1) { tagWriter.writeArtwork(editableCopy, artwork) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, editableCopy) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    // ── artwork removal (per-track artwork Task 7) ───────────────────────

    // Remove, then Save: the fields are written, then the picture is removed from the same copy before the persist,
    // and nothing is embedded. Trivial stub: onRemoveArtworkClick does nothing → the state stays clean, Save no-ops
    // and removeArtwork is never called. Mutation: writeAndPersist ignores isArtworkRemoved → RED on removeArtwork.
    @Test
    fun remove_thenSave_callsRemoveArtworkNotWrite() = runTest {
        val viewModel = loadedViewModel(hasArtwork = true)
        stubSuccessfulSave()

        viewModel.onRemoveArtworkClick()
        assertTrue("a removal alone makes the form saveable", viewModel.uiState.value.canSave)
        saveWithConsent(viewModel)

        coVerifyOrder {
            tagWriter.write(editableCopy, any())
            tagWriter.removeArtwork(editableCopy)
            songFileResolver.persistEditedCopy(song, editableCopy)
        }
        coVerify(exactly = 1) { tagWriter.removeArtwork(editableCopy) }
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
        assertTrue("the save completes", viewModel.uiState.value.isSaved)
    }

    // The header previews the placeholder ("") once the picture is removed, and the removal alone is an edit.
    // Control: before the click the header previews the song's artwork and the form is clean. Trivial stub: no state
    // change → the preview stays the song's artwork. Mutation: dirty ignores isArtworkRemoved → RED on isDirty.
    @Test
    fun remove_previewsPlaceholderAndMarksDirty() = runTest {
        val viewModel = loadedViewModel(hasArtwork = true)
        val before = viewModel.uiState.value
        assertEquals("before removal the header previews the song's artwork", song.artworkUri, before.previewArtworkUri)
        assertEquals("the freshly loaded form is clean", false, before.isDirty)
        assertTrue("a file with a picture offers Remove artwork", before.canRemoveArtwork)

        viewModel.onRemoveArtworkClick()

        val after = viewModel.uiState.value
        assertTrue("the removal is recorded", after.isArtworkRemoved)
        assertEquals("after removal the header previews the placeholder", "", after.previewArtworkUri)
        assertTrue("removing the file's picture is an edit", after.isDirty)
        assertTrue("removing the file's picture can be saved", after.canSave)
        assertEquals("Remove artwork is not offered twice", false, after.canRemoveArtwork)
    }

    // The last choice wins: a pick after a removal cancels the removal and is what Save writes. Control: the removal
    // is recorded before the pick. Trivial stub: no removal recorded → RED on the control. Mutation: onArtworkPicked
    // keeps isArtworkRemoved → RED on "the pick cancels the removal".
    @Test
    fun pickAfterRemove_writesThePick() = runTest {
        val viewModel = loadedViewModel(hasArtwork = true)
        val artwork = pickedArtwork()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns artwork
        stubSuccessfulSave()

        viewModel.onRemoveArtworkClick()
        assertTrue("the removal is recorded before the pick (control)", viewModel.uiState.value.isArtworkRemoved)
        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("the pick cancels the removal", false, state.isArtworkRemoved)
        assertEquals("the header previews the pick", PICKED_IMAGE_URI, state.previewArtworkUri)
        saveWithConsent(viewModel)

        coVerify(exactly = 1) { tagWriter.writeArtwork(editableCopy, artwork) }
        coVerify(exactly = 0) { tagWriter.removeArtwork(any()) }
        assertTrue("the save completes", viewModel.uiState.value.isSaved)
    }

    // The last choice wins the other way too: a removal after a pick drops the pick, and Save removes. Trivial stub:
    // the removal leaves the pick pending → RED on assertNull. Mutation: onRemoveArtworkClick clears the shown uri but
    // keeps the picked bytes, so Save still embeds them → RED on removeArtwork.
    @Test
    fun removeAfterPick_removes() = runTest {
        val viewModel = loadedViewModel(hasArtwork = true)
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns pickedArtwork()
        stubSuccessfulSave()

        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()
        assertEquals("the pick is pending (control)", PICKED_IMAGE_URI, viewModel.uiState.value.pendingArtworkUri)
        viewModel.onRemoveArtworkClick()

        val state = viewModel.uiState.value
        assertNull("the removal drops the pending pick", state.pendingArtworkUri)
        assertEquals("the header previews the placeholder", "", state.previewArtworkUri)
        saveWithConsent(viewModel)

        coVerify(exactly = 1) { tagWriter.removeArtwork(editableCopy) }
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
        assertTrue("the save completes", viewModel.uiState.value.isSaved)
    }

    // Ruling P21, last tap wins: a pick still loading when Remove is tapped is dropped when its load completes, and
    // Save removes. Control: the removal is recorded while the load is suspended. Trivial stub (no generation check)
    // → the late pick clears the removal. Mutation: onRemoveArtworkClick no longer invalidates in-flight picks → RED.
    @Test
    fun slowPickThenRemove_removeWins() = runTest {
        val viewModel = loadedViewModel(hasArtwork = true)
        val gate = CompletableDeferred<PickedArtwork?>()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } coAnswers { gate.await() }
        stubSuccessfulSave()

        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()
        viewModel.onRemoveArtworkClick()
        assertTrue("the removal is recorded while the pick loads (control)", viewModel.uiState.value.isArtworkRemoved)
        gate.complete(pickedArtwork())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("a pick that finishes after Remove does not undo it", state.isArtworkRemoved)
        assertNull("the late pick is not pending", state.pendingArtworkUri)
        assertEquals("the header still previews the placeholder", "", state.previewArtworkUri)
        saveWithConsent(viewModel)
        coVerify(exactly = 1) { tagWriter.removeArtwork(editableCopy) }
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
    }

    // Ruling P21: a pick whose load completes after Save started is not applied (the save is already writing the
    // state the user confirmed). Control: Save started with the field edit. Trivial stub (no isSaving check) → the
    // pick lands mid-save and is embedded. Mutation: drop the isSaving check at completion → RED.
    @Test
    fun slowPickThenSave_pickNotApplied() = runTest {
        val viewModel = loadedViewModel(hasArtwork = true)
        val gate = CompletableDeferred<PickedArtwork?>()
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } coAnswers { gate.await() }
        stubSuccessfulSave()

        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()
        viewModel.onSaveClick()
        assertTrue("the save has started (control)", viewModel.uiState.value.isSaving)
        gate.complete(pickedArtwork())
        advanceUntilIdle()

        assertNull("a pick that finishes during a save is not applied", viewModel.uiState.value.pendingArtworkUri)
        viewModel.onConsentGranted()
        advanceUntilIdle()
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
        assertTrue("the save completes with the fields only", viewModel.uiState.value.isSaved)
    }

    // A file with no picture and no pick has nothing to remove; near-miss control: the same file with a pick can
    // remove it, and removing only the pick returns the form to clean. Trivial stub: canRemoveArtwork always true →
    // RED on the first assertion. Mutation: canRemoveArtwork ignores fileHasArtwork → RED on the first assertion.
    @Test
    fun noPictureInFile_cannotRemove() = runTest {
        val viewModel = loadedViewModel(hasArtwork = false)
        coEvery { artworkLoader.load(PICKED_IMAGE_URI) } returns pickedArtwork()

        assertEquals("no picture and no pick: nothing to remove", false, viewModel.uiState.value.canRemoveArtwork)

        viewModel.onArtworkPicked(PICKED_IMAGE_URI)
        advanceUntilIdle()
        assertTrue("a pending pick can be removed", viewModel.uiState.value.canRemoveArtwork)

        viewModel.onRemoveArtworkClick()
        assertEquals(
            "removing only the pick from a picture-less file leaves nothing to save",
            false,
            viewModel.uiState.value.isDirty,
        )
    }

    // A WAV copy cannot carry artwork (spec goal 9): no Change or Remove, even when its tag holds a picture. Control:
    // the MP3 copy of the same song supports artwork. Trivial stub: isArtworkSupported left at true → RED.
    // Mutation: isArtworkSupported = true always → RED.
    @Test
    fun wavFile_hidesArtworkActions() = runTest {
        val mp3 = loadedViewModel(hasArtwork = true)
        assertTrue("an MP3 copy supports artwork (control)", mp3.uiState.value.isArtworkSupported)

        val wavCopy = File("cache/tag_editor_edits/${SONG_ID}_edit.wav")
        val wav = loadedViewModel(copy = wavCopy, hasArtwork = true)

        val state = wav.uiState.value
        assertEquals("a WAV copy does not support artwork", false, state.isArtworkSupported)
        assertEquals("a WAV copy offers no Remove artwork", false, state.canRemoveArtwork)
    }

    // Ruling P9-style isolation: a hasArtwork failure is "no picture"; the load still completes and the form works.
    // Trivial stub: hasArtwork never asked → RED on the coVerify. Mutation: drop the catch (the failure reaches the
    // load's own catch) → RED on loadFailed.
    @Test
    fun hasArtworkThrows_treatsAsNoPictureAndLoads() = runTest {
        fakeSongRepository.emit(listOf(song))
        coEvery { songFileResolver.createEditableCopy(song) } returns editableCopy
        coEvery { tagReader.read(editableCopy) } returns loadedTags
        coEvery { tagReader.hasArtwork(editableCopy) } throws IOException("corrupt picture frame")

        val viewModel = newViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { tagReader.hasArtwork(editableCopy) }
        val state = viewModel.uiState.value
        assertEquals("the load completes", false, state.isLoading)
        assertEquals("a hasArtwork failure is not a load failure", false, state.loadFailed)
        assertNull("no error message is shown", state.message)
        assertEquals("the tags still load", loadedTags, state.tags)
        assertEquals("an unreadable picture counts as no picture", false, state.canRemoveArtwork)
    }

    // Cancellation is never swallowed: a cancelled hasArtwork ends the load instead of completing it as "no picture".
    // Control: the previous test shows the same throw of a plain exception completes the load. Mutation: catch every
    // Throwable (cancellation included) → the load completes → RED on isLoading.
    @Test
    fun hasArtworkCancelled_rethrowsAndDoesNotFinishTheLoad() = runTest {
        fakeSongRepository.emit(listOf(song))
        coEvery { songFileResolver.createEditableCopy(song) } returns editableCopy
        coEvery { tagReader.read(editableCopy) } returns loadedTags
        coEvery { tagReader.hasArtwork(editableCopy) } throws CancellationException("screen left")

        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("a cancelled load never reports itself finished", state.isLoading)
        assertEquals("a cancelled load is not a load failure", false, state.loadFailed)
    }

    // ── save: consent request ─────────────────────────────────────────────

    @Test
    fun onSaveClick_notDirty_isNoOp() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onSaveClick()

        assertNull(viewModel.uiState.value.consentRequest)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun onSaveClick_dirtyAndValid_exposesConsentRequestAndDoesNotWriteYet() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")

        viewModel.onSaveClick()

        assertEquals(listOf(song.contentUri), viewModel.uiState.value.consentRequest)
        assertTrue(viewModel.uiState.value.isSaving)
        coVerify(exactly = 0) { tagWriter.write(any(), any()) }
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(any(), any()) }
    }

    @Test
    fun onSaveClick_invalidField_isNoOp() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onFieldChanged(TagField.YEAR, "notayear")

        viewModel.onSaveClick()

        assertNull(viewModel.uiState.value.consentRequest)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    // ── save: granted ─────────────────────────────────────────────────────

    @Test
    fun onConsentGranted_writesThenPersistsThenCleansUpAndMarksSaved() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs

        viewModel.onConsentGranted()
        advanceUntilIdle()

        coVerify(exactly = 1) { tagWriter.write(editableCopy, match { it.title == "New Title" }) }
        // No artwork was picked, so none is embedded.
        coVerify(exactly = 0) { tagWriter.writeArtwork(any(), any()) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, editableCopy) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(editableCopy) }
        // The successful save re-indexes exactly the saved song (MediaStore notify + targeted
        // re-sync + now-playing refresh all live behind this one call).
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(song)) }

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertTrue(state.isClosed)
        assertEquals(false, state.isSaving)
        assertNull(state.consentRequest)
    }

    @Test
    fun onConsentGranted_withoutPriorSaveClick_isNoOp() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onConsentGranted()
        advanceUntilIdle()

        coVerify(exactly = 0) { tagWriter.write(any(), any()) }
    }

    // ── save: denied ──────────────────────────────────────────────────────

    @Test
    fun onConsentDenied_doesNotWriteAndSetsMessage() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        viewModel.onConsentDenied()
        advanceUntilIdle()

        coVerify(exactly = 0) { tagWriter.write(any(), any()) }
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(any(), any()) }
        coVerify(exactly = 0) { tagSaveFinalizer.onSongsSaved(any()) }

        val state = viewModel.uiState.value
        assertEquals(false, state.isSaving)
        assertEquals("Changes weren't saved — permission declined.", state.message)
        assertEquals(false, state.isClosed)
        assertNull(state.consentRequest)
    }

    // ── save: API 29 recovery ─────────────────────────────────────────────

    @Test
    fun onConsentGranted_persistThrowsRecoverableSecurityExceptionOnApi29_exposesRecoveryRequest_thenRetrySucceeds() =
        runTest {
            val viewModel = loadedViewModel()
            viewModel.sdkIntProvider = { API_29 }
            viewModel.onFieldChanged(TagField.TITLE, "New Title")
            viewModel.onSaveClick()

            val recoverableException = mockk<RecoverableSecurityException>()
            coEvery { tagWriter.write(editableCopy, any()) } just Runs
            coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } throws recoverableException

            viewModel.onConsentGranted()
            advanceUntilIdle()

            assertEquals(recoverableException, viewModel.uiState.value.recoveryRequest)
            assertTrue(viewModel.uiState.value.isSaving)
            coVerify(exactly = 1) { tagWriter.write(any(), any()) }
            coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, editableCopy) }

            // The screen resolved the recovery prompt; retry the persist only, not the write.
            coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } just Runs
            coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs

            viewModel.onRecoveryGranted()
            advanceUntilIdle()

            coVerify(exactly = 1) { tagWriter.write(any(), any()) }
            coVerify(exactly = 2) { songFileResolver.persistEditedCopy(song, editableCopy) }
            val state = viewModel.uiState.value
            assertTrue(state.isSaved)
            assertTrue(state.isClosed)
            assertNull(state.recoveryRequest)
        }

    @Test
    fun onConsentGranted_persistThrowsRecoverableSecurityExceptionNotOnApi29_isGenericFailure() = runTest {
        val viewModel = loadedViewModel()
        // sdkIntProvider defaults to the real Build.VERSION.SDK_INT, which is 0 in a plain JVM
        // unit test -- never equal to API 29 -- so this exercises the "else" branch without
        // overriding the seam.
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        val recoverableException = mockk<RecoverableSecurityException>()
        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } throws recoverableException
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs

        viewModel.onConsentGranted()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.recoveryRequest)
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(editableCopy) }
        assertEquals("Couldn't save changes. Please try again.", viewModel.uiState.value.message)
        assertEquals(false, viewModel.uiState.value.isSaved)
    }

    @Test
    fun onRecoveryGranted_withoutPendingRecoveryRequest_isNoOp() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onRecoveryGranted()
        advanceUntilIdle()

        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(any(), any()) }
    }

    @Test
    fun onConsentDenied_duringApi29Recovery_doesNotRetryPersistAndStaysOpenWithMessage() = runTest {
        val viewModel = loadedViewModel()
        viewModel.sdkIntProvider = { API_29 }
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        val recoverableException = mockk<RecoverableSecurityException>()
        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } throws recoverableException

        viewModel.onConsentGranted()
        advanceUntilIdle()
        assertEquals(recoverableException, viewModel.uiState.value.recoveryRequest)

        // Declining the recovery prompt must not retry the persist that just failed, and must not
        // be confused with a fresh initial-consent decline (there is no consentRequest here).
        viewModel.onConsentDenied()
        advanceUntilIdle()

        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, editableCopy) }
        coVerify(exactly = 0) { tagSaveFinalizer.onSongsSaved(any()) }
        val state = viewModel.uiState.value
        assertNull(state.recoveryRequest)
        assertEquals(false, state.isSaving)
        assertEquals("Changes weren't saved — permission declined.", state.message)
        assertEquals(false, state.isClosed)
        assertEquals(false, state.isSaved)
    }

    // ── save: finalizer ordering ──────────────────────────────────────────

    @Test
    fun onConsentGranted_finalizerRunsBeforeIsSavedAndIsClosedFlip() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs
        var isSavedDuringFinalize: Boolean? = null
        var isClosedDuringFinalize: Boolean? = null
        coEvery { tagSaveFinalizer.onSongsSaved(listOf(song)) } coAnswers {
            isSavedDuringFinalize = viewModel.uiState.value.isSaved
            isClosedDuringFinalize = viewModel.uiState.value.isClosed
        }

        viewModel.onConsentGranted()
        advanceUntilIdle()

        assertEquals(false, isSavedDuringFinalize)
        assertEquals(false, isClosedDuringFinalize)
        assertTrue(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.isClosed)
    }

    // ── save: generic IO failure ─────────────────────────────────────────

    @Test
    fun onConsentGranted_writeThrowsGenericException_showsErrorCleansUpCopyAndDoesNotClose() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        coEvery { tagWriter.write(editableCopy, any()) } throws IOException("disk full")
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs

        viewModel.onConsentGranted()
        advanceUntilIdle()

        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(any(), any()) }
        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(editableCopy) }
        val state = viewModel.uiState.value
        assertEquals(false, state.isSaved)
        assertEquals(false, state.isClosed)
        assertEquals(false, state.isSaving)
        assertEquals("Couldn't save changes. Please try again.", state.message)
    }

    @Test
    fun onConsentGranted_persistThrowsGenericException_showsErrorAndCleansUpCopy() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onSaveClick()

        coEvery { tagWriter.write(editableCopy, any()) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, editableCopy) } throws IOException("disk full")
        coEvery { songFileResolver.deleteEditableCopy(editableCopy) } just Runs

        viewModel.onConsentGranted()
        advanceUntilIdle()

        coVerify(exactly = 1) { songFileResolver.deleteEditableCopy(editableCopy) }
        // A failed persist means the file on disk never changed -- nothing to re-index.
        coVerify(exactly = 0) { tagSaveFinalizer.onSongsSaved(any()) }
        val state = viewModel.uiState.value
        assertEquals(false, state.isSaved)
        assertEquals(false, state.isClosed)
        assertEquals(false, state.isSaving)
        assertEquals("Couldn't save changes. Please try again.", state.message)
    }

    // ── dismissMessage ────────────────────────────────────────────────────

    @Test
    fun dismissMessage_clearsMessage() = runTest {
        val viewModel = newViewModel() // song not found -> message populated
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.message != null)

        viewModel.dismissMessage()

        assertNull(viewModel.uiState.value.message)
    }

    // ── custom tags (phase 2) ─────────────────────────────────────────────

    // The picker opens ticked with what the file carries. Stub: EMPTY. Mutation: skip the ROLLA_TAGS read → EMPTY →
    // RED.
    @Test
    fun load_readsTicksFromTheFile() = runTest {
        seedCustomTagLists()
        writeFileIds(editableCopy, CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()))

        val state = loadedViewModel().uiState.value

        assertEquals(CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()), state.picker.selected)
        val tagNames = state.picker.tags.map { it.name }
        assertEquals("the picker shows the TAG list in library order", listOf("Acid", "Peak time", "Techno"), tagNames)
        assertTrue("an MP3 copy takes custom tags", state.customTagsEditable)
        assertFalse("the loaded ticks are the baseline, not an edit", state.isDirty)
    }

    // M4A is read but not written (user decision 2026-11-10), so its rows are disabled. Control: the MP3 copy is
    // editable. Stub: true. Mutation: decide with isCustomTagExtension (MP3, FLAC and M4A) → m4a editable → RED.
    @Test
    fun load_m4a_disablesCustomTags() = runTest {
        assertTrue("an MP3 copy takes custom tags (control)", loadedViewModel().uiState.value.customTagsEditable)

        val m4aCopy = fixtureFile("${SONG_ID}_edit.m4a", M4aFixture.bytes())
        val state = loadedViewModel(copy = m4aCopy).uiState.value

        assertEquals("an M4A copy cannot take custom tags", false, state.customTagsEditable)
        assertEquals("the M4A copy still loads", false, state.loadFailed)
    }

    // Final review item 9: a format the scan reads no field from (WAV) is never opened for ROLLA_TAGS; the load
    // completes and the rows show nothing, disabled. Control: an MP3 copy is read. Mutation: drop the
    // isCustomTagExtension check → the WAV copy is read → RED on the verify.
    @Test
    fun load_wav_skipsTheCustomTagRead() = runTest {
        loadedViewModel()
        verify(exactly = 1) { rollaTagsFileIo.read(editableCopy) }

        val wavCopy = fixtureFile("${SONG_ID}_edit.wav", AudioFixtures.mp3Bytes())
        val state = loadedViewModel(copy = wavCopy).uiState.value

        verify(exactly = 0) { rollaTagsFileIo.read(wavCopy) }
        assertEquals("the WAV copy still loads", false, state.loadFailed)
        assertEquals("the fields load", loadedTags, state.tags)
        assertEquals("a WAV carries no custom tags", CustomTagIds.EMPTY, state.picker.selected)
        assertEquals("a WAV cannot take custom tags", false, state.customTagsEditable)
    }

    // A tick is an edit; taking it back returns to clean. Stub: never dirty. Mutation: leave the selection out of the
    // dirty check → not dirty after the toggle → RED.
    @Test
    fun toggle_makesDirtyAndUntoggleRestoresClean() = runTest {
        seedCustomTagLists()
        val viewModel = loadedViewModel()
        assertFalse("the freshly loaded form is clean (control)", viewModel.uiState.value.isDirty)

        viewModel.onCustomTagToggle(CustomTagKind.TAG, 3L)
        val ticked = viewModel.uiState.value
        assertEquals(CustomTagIds(tagIds = setOf(3L), subTagIds = emptySet()), ticked.picker.selected)
        assertTrue("a tick is an edit", ticked.isDirty)
        assertTrue("a tick alone can be saved", ticked.canSave)

        viewModel.onCustomTagToggle(CustomTagKind.TAG, 3L)
        val unticked = viewModel.uiState.value
        assertEquals(CustomTagIds.EMPTY, unticked.picker.selected)
        assertFalse("taking the tick back returns to clean", unticked.isDirty)
        assertFalse("nothing left to save", unticked.canSave)
    }

    // Save writes the ticked IDs into the copy's field, after the field write and before the persist. Stub: no write.
    // Mutation: skip writeCustomTagIds → the field stays {"tags":"2",…} → RED.
    @Test
    fun save_writesTickedIdsToTheCopy() = runTest {
        seedCustomTagLists()
        writeFileIds(editableCopy, CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()))
        val viewModel = loadedViewModel()
        stubSuccessfulSave()

        viewModel.onCustomTagToggle(CustomTagKind.TAG, 3L)
        saveWithConsent(viewModel)

        assertEquals(listOf("""{"tags":"2,3","subTags":""}"""), rollaTagsOnDisk(editableCopy))
        coVerifyOrder {
            tagWriter.write(editableCopy, any())
            rollaTagsFileIo.writeCustomTagIds(editableCopy, any())
            songFileResolver.persistEditedCopy(song, editableCopy)
        }
        assertTrue("the save completes", viewModel.uiState.value.isSaved)
    }

    // An ID the lists do not know (99) is never shown and survives the save. Control: the picker shows only TAG 2.
    // Stub: write the selection only. Mutation: drop the union with the file's unknown IDs → the field is removed →
    // RED.
    @Test
    fun save_keepsUnknownIdsFromTheFile() = runTest {
        seedCustomTagLists()
        writeFileIds(editableCopy, CustomTagIds(tagIds = setOf(2L, 99L), subTagIds = emptySet()))
        val viewModel = loadedViewModel()
        stubSuccessfulSave()
        assertEquals(
            "the picker holds the known ID only (control)",
            CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()),
            viewModel.uiState.value.picker.selected,
        )

        viewModel.onCustomTagToggle(CustomTagKind.TAG, 2L)
        saveWithConsent(viewModel)

        assertEquals(listOf("""{"tags":"99","subTags":""}"""), rollaTagsOnDisk(editableCopy))
        assertTrue("the save completes", viewModel.uiState.value.isSaved)
    }

    // An M4A saves its fields and never has the field written: a tick is refused, so the ticks never differ from the
    // load and Save skips the field. Stub: write anyway. Mutation (fix round 1, the probe moved): drop the editable
    // check from canEditCustomTags → the tick is applied → RED on "the tick is refused". The Save-time editable check
    // is a second guard behind this one; dropping it alone is equivalent since ruling 7 (see task-4-report.md).
    @Test
    fun save_m4a_writesFieldsButNoCustomTags() = runTest {
        val m4aCopy = fixtureFile("${SONG_ID}_edit.m4a", M4aFixture.bytes())
        val viewModel = loadedViewModel(copy = m4aCopy)
        coEvery { tagWriter.write(m4aCopy, any()) } just Runs
        coEvery { songFileResolver.persistEditedCopy(song, m4aCopy) } just Runs
        coEvery { songFileResolver.deleteEditableCopy(m4aCopy) } just Runs

        viewModel.onCustomTagToggle(CustomTagKind.TAG, 2L)
        assertEquals("the tick is refused on an M4A", CustomTagIds.EMPTY, viewModel.uiState.value.picker.selected)
        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        saveWithConsent(viewModel)

        verify(exactly = 0) { rollaTagsFileIo.writeCustomTagIds(any(), any()) }
        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, m4aCopy) }
        assertTrue("the field edit is saved", viewModel.uiState.value.isSaved)
    }

    // "New tag" → Create adds the entry, closes the dialog and ticks it. Stub: don't tick. Mutation: close the dialog
    // only → the new ID is not selected → RED.
    @Test
    fun create_savedTicksTheNewEntry() = runTest {
        seedCustomTagLists()
        val viewModel = loadedViewModel()

        viewModel.onCreateCustomTagClick(CustomTagKind.TAG, "Dub")
        assertEquals(
            "the dialog opens with the search text (control)",
            CustomTagNameDialogState(kind = CustomTagKind.TAG, initialName = "Dub"),
            viewModel.uiState.value.nameDialog,
        )
        viewModel.onCreateCustomTagConfirm("Dub")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("a saved entry closes the dialog", state.nameDialog)
        val dub = state.picker.tags.single { it.name == "Dub" }
        assertTrue("the new entry is ticked", dub.id in state.picker.selected.tagIds)
        assertTrue("ticking the new entry is an edit", state.isDirty)
    }

    // A name already in the list keeps the dialog open with the reason. Stub: close. Mutation: map Duplicate to a null
    // error → RED.
    @Test
    fun create_duplicate_showsErrorAndKeepsDialog() = runTest {
        seedCustomTagLists()
        val viewModel = loadedViewModel()

        viewModel.onCreateCustomTagClick(CustomTagKind.TAG, "techno")
        viewModel.onCreateCustomTagConfirm("techno")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            "the dialog stays open with the reason",
            CustomTagNameDialogState(
                kind = CustomTagKind.TAG,
                initialName = "techno",
                error = "That name is already in the list",
            ),
            state.nameDialog,
        )
        assertEquals("nothing is ticked", CustomTagIds.EMPTY, state.picker.selected)
    }

    // A list that has issued its last ID says so. Stub: other text. Mutation: map LimitReached to "Enter a name" → RED.
    @Test
    fun create_limitReached_showsListFull() = runTest {
        customTagDao.raiseCounter(CustomTagKind.SUBTAG, MAX_CUSTOM_TAG_ID)
        val viewModel = loadedViewModel()

        viewModel.onCreateCustomTagClick(CustomTagKind.SUBTAG, "Closing")
        viewModel.onCreateCustomTagConfirm("Closing")
        advanceUntilIdle()

        assertEquals("This list is full", viewModel.uiState.value.nameDialog?.error)
    }

    // A row tap opens the picker on that row's page; Done closes it. Stub: never open. Mutation: open on TAG whatever
    // the row → page TAG → RED.
    @Test
    fun rowsClick_opensPickerOnThatPageAndDoneCloses() = runTest {
        val viewModel = loadedViewModel()
        assertFalse("the picker starts closed (control)", viewModel.uiState.value.isPickerOpen)

        viewModel.onCustomTagRowsClick(CustomTagKind.SUBTAG)
        assertTrue("a row tap opens the picker", viewModel.uiState.value.isPickerOpen)
        assertEquals(CustomTagKind.SUBTAG, viewModel.uiState.value.picker.page)

        viewModel.onPickerDone()
        assertFalse("Done closes the picker", viewModel.uiState.value.isPickerOpen)
    }

    // ── custom tags: fix round 1 (rulings 7–8) ────────────────────────────

    // Ruling 7: a save that leaves the ticks alone never rewrites the field, so a value this app cannot decode
    // survives a title edit. Control: the field reads as no tags and the form is clean before the edit. Mutation:
    // always write → the undecodable field is deleted (EMPTY) → RED.
    @Test
    fun save_titleOnly_keepsAnUndecodableField() = runTest {
        seedCustomTagLists()
        writeRawRollaTags(editableCopy, NOT_JSON)
        val viewModel = loadedViewModel()
        stubSuccessfulSave()
        val ticks = viewModel.uiState.value.picker.selected
        assertEquals("the undecodable field reads as no ticks (control)", CustomTagIds.EMPTY, ticks)

        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        saveWithConsent(viewModel)

        assertTrue("the title edit is saved", viewModel.uiState.value.isSaved)
        assertEquals(listOf(NOT_JSON), rollaTagsOnDisk(editableCopy))
    }

    // Ruling 7, second shape: a value with a key this app does not write survives a title edit unchanged. Control:
    // the picker ticks TAG 2 from it. Mutation: always write → the value is normalised to {"tags":"2","subTags":""}
    // → RED.
    @Test
    fun save_titleOnly_keepsAFieldWithAnExtraKey() = runTest {
        seedCustomTagLists()
        writeRawRollaTags(editableCopy, EXTRA_KEY_VALUE)
        val viewModel = loadedViewModel()
        stubSuccessfulSave()
        assertEquals(
            "the field's known ID is ticked (control)",
            CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()),
            viewModel.uiState.value.picker.selected,
        )

        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        saveWithConsent(viewModel)

        assertTrue("the title edit is saved", viewModel.uiState.value.isSaved)
        assertEquals(listOf(EXTRA_KEY_VALUE), rollaTagsOnDisk(editableCopy))
    }

    // Ruling 8 (M1): a ROLLA_TAGS read that fails at load does not fail the load; it disables the rows (the note
    // shows), and Save still writes the fields without touching the field. Mutation: drop the catch → the read failure
    // reaches the load's own catch → loadFailed → RED.
    @Test
    fun load_rollaTagsReadFails_disablesRowsAndStillSavesFields() = runTest {
        every { rollaTagsFileIo.read(editableCopy) } throws IOException("unreadable ROLLA_TAGS")
        val viewModel = loadedViewModel()
        stubSuccessfulSave()

        val loaded = viewModel.uiState.value
        assertEquals("a ROLLA_TAGS read failure is not a load failure", false, loaded.loadFailed)
        assertEquals("the fields still load", loadedTags, loaded.tags)
        assertEquals("the rows are disabled, so the note shows", false, loaded.customTagsEditable)

        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        saveWithConsent(viewModel)

        coVerify(exactly = 1) { songFileResolver.persistEditedCopy(song, editableCopy) }
        verify(exactly = 0) { rollaTagsFileIo.writeCustomTagIds(any(), any()) }
        assertTrue("the title edit is saved", viewModel.uiState.value.isSaved)
    }

    // Ruling 8 (M2): a custom tag table that fails degrades to empty lists; the editor loads and the fields save.
    // Mutation: drop the catch on the lists flow → the lists never arrive and the error escapes → RED.
    @Test
    fun listsFlowThrows_editorLoadsWithEmptyLists() = runTest {
        val failing: CustomTagRepository = mockk {
            every { observeCustomTags(any()) } returns flow { throw IllegalStateException("custom_tags is broken") }
        }
        val viewModel = loadedViewModel(repository = failing)

        val state = viewModel.uiState.value
        assertEquals("the editor finishes loading", false, state.isLoading)
        assertEquals("a list failure is not a load failure", false, state.loadFailed)
        assertEquals("the lists read as empty", emptyList<CustomTag>(), state.picker.tags + state.picker.subTags)
        assertEquals("the rows show None", CustomTagIds.EMPTY, state.picker.selected)
    }

    // Ruling 8 (M4): a blank name says "Enter a name" (the dialog disables Create while blank, so the ViewModel is
    // driven directly). Mutation: map Blank to the too-long text → RED.
    @Test
    fun create_blank_showsEnterAName() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onCreateCustomTagClick(CustomTagKind.TAG, "")
        viewModel.onCreateCustomTagConfirm("   ")
        advanceUntilIdle()

        assertEquals("Enter a name", viewModel.uiState.value.nameDialog?.error)
    }

    // Ruling 8 (M4): a 41-character name says "Use 40 characters or fewer". Mutation: map TooLong to "Enter a name"
    // → RED.
    @Test
    fun create_tooLong_showsTheLimit() = runTest {
        val viewModel = loadedViewModel()

        viewModel.onCreateCustomTagClick(CustomTagKind.TAG, "")
        viewModel.onCreateCustomTagConfirm("x".repeat(TOO_LONG_NAME_LENGTH))
        advanceUntilIdle()

        assertEquals("Use 40 characters or fewer", viewModel.uiState.value.nameDialog?.error)
    }

    // Ruling 8 (M5): end to end with the real TagReader and TagWriter on an MP3 fixture carrying an unknown ID (99).
    // A title edit and a tick of TAG 3 are saved: the field re-read after the real field write still holds 99, so the
    // file ends with the new title and {3, 99}. Mutation: build the IDs from the load-time baseline instead of
    // re-reading the copy → 99 is lost → RED.
    @Test
    fun save_realWriter_titleEditKeepsUnknownId() = runTest {
        seedCustomTagLists()
        writeFileIds(editableCopy, CustomTagIds(tagIds = setOf(99L), subTagIds = emptySet()))
        val realReader = TagReader(mainDispatcherRule.testDispatcher)
        fakeSongRepository.emit(listOf(song))
        coEvery { songFileResolver.createEditableCopy(song) } returns editableCopy
        stubPersist(editableCopy)
        val viewModel = newViewModel(reader = realReader, writer = TagWriter(mainDispatcherRule.testDispatcher))
        advanceUntilIdle()
        val ticks = viewModel.uiState.value.picker.selected
        assertEquals("the unknown ID is not shown (control)", CustomTagIds.EMPTY, ticks)

        viewModel.onFieldChanged(TagField.TITLE, "New Title")
        viewModel.onCustomTagToggle(CustomTagKind.TAG, 3L)
        saveWithConsent(viewModel)

        assertTrue("the save completes", viewModel.uiState.value.isSaved)
        assertEquals("New Title", realReader.read(editableCopy).title)
        assertEquals(listOf("""{"tags":"3,99","subTags":""}"""), rollaTagsOnDisk(editableCopy))
    }
}

private const val NOT_JSON = "not-json"
private const val EXTRA_KEY_VALUE = """{"tags":"2","subTags":"","x":"y"}"""
private const val TOO_LONG_NAME_LENGTH = 41

private const val API_29 = 29
private const val PICKED_IMAGE_URI = "content://media/picker/0/com.android.providers.media.photopicker/media/42"

private fun pickedArtwork(): PickedArtwork =
    PickedArtwork(bytes = byteArrayOf(1, 2, 3), mimeType = "image/png", width = 1, height = 1)

private fun testSong(): Song = Song(
    id = SONG_ID,
    title = "Original Title",
    artist = "Original Artist",
    album = "Original Album",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = 3,
    year = 2020,
    contentUri = "content://media/external/audio/media/$SONG_ID",
    artworkUri = "content://media/external/audio/albumart/$SONG_ID",
)

private fun testTags(): SongTags = SongTags(
    title = "Original Title",
    artist = "Original Artist",
    album = "Original Album",
    albumArtist = "Original Album Artist",
    genre = "Rock",
    year = "2020",
    trackNumber = "3",
    composer = "Original Composer",
)
