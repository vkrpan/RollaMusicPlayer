package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.feature.tageditor.io.ArtworkLoader
import com.rolla.musicplayer.feature.tageditor.io.PickedArtwork
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagReader
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
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSongRepository = FakeSongRepository()
    private val songFileResolver: SongFileResolver = mockk()
    private val tagReader: TagReader = mockk()
    private val tagWriter: TagWriter = mockk()
    private val artworkLoader: ArtworkLoader = mockk()
    private val tagSaveFinalizer: TagSaveFinalizer = mockk(relaxed = true)

    private val song = testSong()
    private val editableCopy = File("cache/tag_editor_edits/${SONG_ID}_edit.mp3")
    private val loadedTags = testTags()

    // Mirrors EqualizerViewModelTest's teardown: cancels every ViewModel's viewModelScope so no
    // in-flight coroutine from one test can resume during a later test's MainDispatcherRule swap.
    private val createdViewModels = mutableListOf<TagEditorViewModel>()

    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel -> viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun newViewModel(songId: Long = SONG_ID_LONG): TagEditorViewModel = TagEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("songId" to songId)),
        songRepository = fakeSongRepository,
        songFileResolver = songFileResolver,
        tagReader = tagReader,
        tagWriter = tagWriter,
        artworkLoader = artworkLoader,
        tagSaveFinalizer = tagSaveFinalizer,
    ).also { createdViewModels += it }

    /** Emits [song], stubs a successful copy+read, and returns a constructed, loaded ViewModel. */
    private fun TestScope.loadedViewModel(): TagEditorViewModel {
        fakeSongRepository.emit(listOf(song))
        coEvery { songFileResolver.createEditableCopy(song) } returns editableCopy
        coEvery { tagReader.read(editableCopy) } returns loadedTags
        val viewModel = newViewModel()
        advanceUntilIdle()
        return viewModel
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
            assertEquals("Song not found.", state.message)
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
}

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
