package com.rolla.musicplayer.feature.tageditor.managetags

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.CustomTagRepositoryImpl
import com.rolla.musicplayer.core.database.entity.SongCustomTagCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.tagio.CustomTagListJson
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.tagio.RollaTagsJson
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.core.testing.audio.AudioFixtures
import com.rolla.musicplayer.core.testing.audio.M4aFixture
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.feature.tageditor.TagSaveFinalizer
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val ITUNES_MEAN = "com.apple.iTunes"

/**
 * Unit tests for [ManageCustomTagsViewModel].
 *
 * The lists, counts and links come from the real [CustomTagRepositoryImpl] over [FakeCustomTagDao] (songs and links
 * seeded there), and the songs from [FakeSongRepository]. Each song's file is a real MP3 or M4A fixture in a temporary
 * folder ([originals]); the mocked [SongFileResolver] copies it into [cacheDir] as the editable copy, persists by
 * copying the copy back over it, and deletes copies for real, so a test reads the file's `ROLLA_TAGS` field as it is on
 * disk and sees any copy left behind. The field is read and written by a spy on the real [RollaTagsFileIo].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageCustomTagsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val fakeSongRepository = FakeSongRepository()
    private val songFileResolver: SongFileResolver = mockk()
    private val tagSaveFinalizer: TagSaveFinalizer = mockk(relaxed = true)
    private val customTagDao = FakeCustomTagDao()
    private val customTagRepository = CustomTagRepositoryImpl(
        customTagDao = customTagDao,
        sortDispatcher = mainDispatcherRule.testDispatcher,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )
    private val rollaTagsFileIo: RollaTagsFileIo = spyk(RollaTagsFileIo())
    private val documents = FakeCustomTagListDocuments()

    /** Each library song's own file, by song id: what the resolver copies from and persists back to. */
    private val originals = mutableMapOf<String, File>()
    private val librarySongs = mutableListOf<Song>()
    private val cacheDir: File by lazy { folder.newFolder("cache") }
    private val createdViewModels = mutableListOf<ManageCustomTagsViewModel>()

    @Before
    fun stubResolver() {
        coEvery { songFileResolver.createEditableCopy(any()) } coAnswers {
            val song = firstArg<Song>()
            val original = originals.getValue(song.id)
            original.copyTo(File(cacheDir, "${song.id}_edit.${original.extension}"))
        }
        coEvery { songFileResolver.persistEditedCopy(any(), any()) } coAnswers {
            secondArg<File>().copyTo(originals.getValue(firstArg<Song>().id), overwrite = true)
            Unit
        }
        coEvery { songFileResolver.deleteEditableCopy(any()) } coAnswers {
            firstArg<File>().delete()
            Unit
        }
    }

    // Cancels every ViewModel's scope so no coroutine of one test resumes during a later test's dispatcher swap.
    @After
    fun tearDown() = runBlocking {
        createdViewModels.forEach { viewModel -> viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun TestScope.newViewModel(): ManageCustomTagsViewModel = ManageCustomTagsViewModel(
        songRepository = fakeSongRepository,
        songFileResolver = songFileResolver,
        tagSaveFinalizer = tagSaveFinalizer,
        customTagRepository = customTagRepository,
        rollaTagsFileIo = rollaTagsFileIo,
        customTagListDocuments = documents,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    ).also {
        createdViewModels += it
        advanceUntilIdle()
    }

    /** Creates [names] in [kind]'s list in order, so they get IDs 1, 2, 3 and so on; returns the entries. */
    private suspend fun createEntries(kind: CustomTagKind, vararg names: String): List<CustomTag> =
        names.mapIndexed { index, name ->
            customTagRepository.create(kind, name)
            CustomTag(kind, index + 1L, name)
        }

    /** An MP3 whose `ROLLA_TAGS` field holds [ids] (no field when empty). */
    private fun mp3Carrying(ids: CustomTagIds): ByteArray {
        val file = File.createTempFile("fixture", ".mp3", folder.root).apply { writeBytes(AudioFixtures.mp3Bytes()) }
        if (!ids.isEmpty) RollaTagsFileIo().writeCustomTagIds(file, ids)
        return file.readBytes()
    }

    /** An M4A whose `ROLLA_TAGS` freeform atom holds [ids], as another program would write it. */
    private fun m4aCarrying(ids: CustomTagIds): ByteArray {
        val atom = M4aFixture.FreeformAtom(ITUNES_MEAN, RollaTagsJson.FIELD_NAME, RollaTagsJson.encode(ids))
        return M4aFixture.bytes(freeformAtoms = listOf(atom))
    }

    /**
     * Adds library song [id] whose file is [bytes] with [extension], and links it, in Room, to every ID in [links]
     * (what the scan would have read, which need not match the file).
     */
    private suspend fun seedSong(
        id: String,
        bytes: ByteArray = AudioFixtures.mp3Bytes(),
        extension: String = "mp3",
        links: CustomTagIds = CustomTagIds.EMPTY,
    ): Song {
        val song = Song(
            id = id,
            title = "Title $id",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200_000L,
            trackNumber = 1,
            year = 2020,
            contentUri = "content://media/external/audio/media/$id",
            artworkUri = "rolla-artwork://song/$id?v=1000",
        )
        originals[id] = File(folder.root, "$id.$extension").apply { writeBytes(bytes) }
        librarySongs += song
        fakeSongRepository.emit(librarySongs.toList())
        customTagDao.seedSongs(listOf(song.toEntity()))
        val refs = links.tagIds.map { SongCustomTagCrossRef(id, CustomTagKind.TAG, it) } +
            links.subTagIds.map { SongCustomTagCrossRef(id, CustomTagKind.SUBTAG, it) }
        if (refs.isNotEmpty()) customTagDao.insertLinks(refs)
        return song
    }

    private fun idsOnDisk(songId: String): CustomTagIds =
        RollaTagsFileIo().read(originals.getValue(songId)).customTagIds

    private fun tagIds(vararg ids: Long) = CustomTagIds(tagIds = ids.toSet(), subTagIds = emptySet())

    /** Makes [song]'s first persist throw a [RecoverableSecurityException] (API 29); later ones persist as usual. */
    private fun refuseFirstPersist(song: Song): RecoverableSecurityException {
        val recoverable = mockk<RecoverableSecurityException>()
        var refused = false
        coEvery { songFileResolver.persistEditedCopy(song, any()) } coAnswers {
            if (!refused) {
                refused = true
                throw recoverable
            }
            secondArg<File>().copyTo(originals.getValue(song.id), overwrite = true)
            Unit
        }
        return recoverable
    }

    /** Opens the confirmation for [tag], confirms it and grants the one consent prompt. */
    private fun TestScope.deleteWithConsent(viewModel: ManageCustomTagsViewModel, tag: CustomTag) {
        viewModel.onDeleteClick(tag)
        viewModel.onDeleteConfirm()
        advanceUntilIdle()
        viewModel.onConsentGranted()
        advanceUntilIdle()
    }

    @Test
    fun lists_showTrackCountsInLibraryOrder() = runTest {
        val (techno, acid) = createEntries(CustomTagKind.TAG, "Techno", "Acid")
        val (hard) = createEntries(CustomTagKind.SUBTAG, "Hard")
        seedSong("1", links = CustomTagIds(tagIds = setOf(1L), subTagIds = setOf(1L)))
        seedSong("2", links = tagIds(1L, 99L))
        seedSong("3", links = tagIds(1L))

        val state = newViewModel().uiState.value

        assertEquals(listOf(ManagedTag(acid, 0), ManagedTag(techno, 3)), state.tags)
        assertEquals(listOf(ManagedTag(hard, 1)), state.subTags)
    }

    @Test
    fun rename_savedClosesDialog_duplicateShowsError() = runTest {
        val (techno) = createEntries(CustomTagKind.TAG, "Techno", "Acid")
        val viewModel = newViewModel()

        viewModel.onRenameClick(techno)
        val opened = CustomTagNameDialogState(kind = CustomTagKind.TAG, initialName = "Techno")
        assertEquals(opened, viewModel.uiState.value.nameDialog)
        assertEquals(techno, viewModel.uiState.value.renaming)

        viewModel.onNameConfirm("acid")
        advanceUntilIdle()
        assertEquals("That name is already in the list", viewModel.uiState.value.nameDialog?.error)
        assertEquals(techno, viewModel.uiState.value.renaming)

        viewModel.onNameConfirm("Jazz")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertNull(state.nameDialog)
        assertNull(state.renaming)
        assertEquals(listOf("Acid", "Jazz"), state.tags.map { it.tag.name })
        assertEquals(CustomTag(CustomTagKind.TAG, 1L, "Jazz"), state.tags.last().tag)
    }

    @Test
    fun create_savedAddsEntry() = runTest {
        val viewModel = newViewModel()

        viewModel.onCreateClick(CustomTagKind.SUBTAG, "Hard")
        val opened = CustomTagNameDialogState(kind = CustomTagKind.SUBTAG, initialName = "Hard")
        assertEquals(opened, viewModel.uiState.value.nameDialog)
        assertNull(viewModel.uiState.value.renaming)
        viewModel.onNameConfirm("Hard")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.nameDialog)
        assertEquals(listOf(ManagedTag(CustomTag(CustomTagKind.SUBTAG, 1L, "Hard"), 0)), state.subTags)
        assertEquals(emptyList<ManagedTag>(), state.tags)
    }

    @Test
    fun deleteClick_showsConfirmWithCount() = runTest {
        val (hard) = createEntries(CustomTagKind.TAG, "Hard")
        listOf("1", "2", "3").forEach { seedSong(it, links = tagIds(1L)) }
        val viewModel = newViewModel()

        viewModel.onDeleteClick(hard)

        assertEquals(DeleteConfirm(hard, 3), viewModel.uiState.value.deleteConfirm)
    }

    @Test
    fun delete_noTracks_deletesWithoutConsent() = runTest {
        val (hard) = createEntries(CustomTagKind.TAG, "Hard")
        val viewModel = newViewModel()

        viewModel.onDeleteClick(hard)
        viewModel.onDeleteConfirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.consentRequest)
        assertNull(state.deleteConfirm)
        assertEquals(emptyList<ManagedTag>(), state.tags)
        assertFalse(state.isBusy)
        assertNull(state.message)
    }

    @Test
    fun delete_allMp3_removesIdFromEveryFileAndEntry() = runTest {
        val entries = createEntries(CustomTagKind.TAG, "A", "B", "C", "D", "E")
        val songs = listOf("1", "2").map { seedSong(it, bytes = mp3Carrying(tagIds(2L, 5L)), links = tagIds(2L, 5L)) }
        val viewModel = newViewModel()

        viewModel.onDeleteClick(entries[1])
        viewModel.onDeleteConfirm()
        advanceUntilIdle()
        assertEquals(songs.map(Song::contentUri), viewModel.uiState.value.consentRequest)
        assertTrue(viewModel.uiState.value.isBusy)
        viewModel.onConsentGranted()
        advanceUntilIdle()

        assertEquals(tagIds(5L), idsOnDisk("1"))
        assertEquals(tagIds(5L), idsOnDisk("2"))
        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 3L, 4L, 5L), state.tags.map { it.tag.id })
        assertNull(state.message)
        assertNull(state.progress)
        assertNull(state.consentRequest)
        assertFalse(state.isBusy)
    }

    @Test
    fun delete_keepsUnknownAndOtherIds() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        createEntries(CustomTagKind.SUBTAG, "X", "Y")
        val carried = CustomTagIds(tagIds = setOf(2L, 99L), subTagIds = setOf(2L))
        seedSong("1", bytes = mp3Carrying(carried), links = carried)
        val viewModel = newViewModel()

        deleteWithConsent(viewModel, b)

        assertEquals(CustomTagIds(tagIds = setOf(99L), subTagIds = setOf(2L)), idsOnDisk("1"))
        assertEquals(listOf(1L), viewModel.uiState.value.tags.map { it.tag.id })
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.subTags.map { it.tag.id })
    }

    @Test
    fun delete_mixedFormats_updatesMp3sAndReportsPartial() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        seedSong("1", bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L))
        val m4aBytes = m4aCarrying(tagIds(2L))
        seedSong("2", bytes = m4aBytes, extension = "m4a", links = tagIds(2L))
        val viewModel = newViewModel()

        deleteWithConsent(viewModel, b)

        assertEquals(CustomTagIds.EMPTY, idsOnDisk("1"))
        assertArrayEquals(m4aBytes, originals.getValue("2").readBytes())
        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.tags.map { it.tag.id })
        assertEquals("Updated 1 of 2 tracks.", state.message)
        assertFalse(state.isBusy)
    }

    @Test
    fun delete_consentDenied_deletesEntryAndReportsZero() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        val bytes = mp3Carrying(tagIds(2L))
        listOf("1", "2").forEach { seedSong(it, bytes = bytes, links = tagIds(2L)) }
        val viewModel = newViewModel()

        viewModel.onDeleteClick(b)
        viewModel.onDeleteConfirm()
        advanceUntilIdle()
        viewModel.onConsentDenied()
        advanceUntilIdle()

        coVerify(exactly = 0) { songFileResolver.createEditableCopy(any()) }
        assertArrayEquals(bytes, originals.getValue("1").readBytes())
        assertArrayEquals(bytes, originals.getValue("2").readBytes())
        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.tags.map { it.tag.id })
        assertEquals("Updated 0 of 2 tracks.", state.message)
        assertNull(state.consentRequest)
        assertFalse(state.isBusy)
    }

    // A file that no longer carries the ID (a stale Room link) counts as updated, with no write and no re-index.
    @Test
    fun delete_finalizerGetsOnlySucceededSongs() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        val written = seedSong("1", bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L))
        val stale = seedSong("2", bytes = mp3Carrying(CustomTagIds.EMPTY), links = tagIds(2L))
        seedSong("3", bytes = m4aCarrying(tagIds(2L)), extension = "m4a", links = tagIds(2L))
        val viewModel = newViewModel()

        deleteWithConsent(viewModel, b)

        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(written)) }
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(any()) }
        coVerify(exactly = 0) { songFileResolver.persistEditedCopy(stale, any()) }
        assertEquals("Updated 2 of 3 tracks.", viewModel.uiState.value.message)
    }

    @Test
    fun delete_copyAlwaysDeleted() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        listOf("1", "2").forEach { seedSong(it, bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L)) }
        every { rollaTagsFileIo.writeCustomTagIds(any(), any()) } throws IOException("disk full")
        val viewModel = newViewModel()

        deleteWithConsent(viewModel, b)

        assertEquals(emptyList<String>(), cacheDir.list().orEmpty().toList())
        coVerify(exactly = 2) { songFileResolver.deleteEditableCopy(any()) }
        assertEquals(tagIds(2L), idsOnDisk("1"))
        val state = viewModel.uiState.value
        assertEquals("Updated 0 of 2 tracks.", state.message)
        assertEquals(listOf(1L), state.tags.map { it.tag.id })
    }

    // The copy is verbatim "Updated X of N tracks." (as the batch editor's "Edited X of N tracks."), even for one.
    @Test
    fun delete_singleM4a_reportsLiteralTracks() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        seedSong("1", bytes = m4aCarrying(tagIds(2L)), extension = "m4a", links = tagIds(2L))
        val viewModel = newViewModel()

        deleteWithConsent(viewModel, b)

        assertEquals("Updated 0 of 1 tracks.", viewModel.uiState.value.message)
    }

    // API 29: declining the recovery prompt counts only that file as not updated (its copy deleted); the loop resumes.
    @Test
    fun delete_api29RecoveryDeclined_countsFileNotUpdatedAndResumes() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        val first = seedSong("1", bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L))
        val second = seedSong("2", bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L))
        refuseFirstPersist(first)
        val viewModel = newViewModel().apply { sdkIntProvider = { Build.VERSION_CODES.Q } }

        deleteWithConsent(viewModel, b)
        viewModel.onConsentDenied()
        advanceUntilIdle()

        assertEquals(tagIds(2L), idsOnDisk("1"))
        assertEquals(CustomTagIds.EMPTY, idsOnDisk("2"))
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(second)) }
        val state = viewModel.uiState.value
        assertNull(state.recoveryRequest)
        assertEquals("Updated 1 of 2 tracks.", state.message)
        assertEquals(listOf(1L), state.tags.map { it.tag.id })
        assertEquals(emptyList<String>(), cacheDir.list().orEmpty().toList())
    }

    // API 29: a persist the platform refuses pauses on that file; granting recovery retries only its persist, then
    // the loop resumes with the next file.
    @Test
    fun delete_api29Recovery_retriesPersistAndResumes() = runTest {
        val (_, b) = createEntries(CustomTagKind.TAG, "A", "B")
        val first = seedSong("1", bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L))
        val second = seedSong("2", bytes = mp3Carrying(tagIds(2L)), links = tagIds(2L))
        val recoverable = refuseFirstPersist(first)
        val viewModel = newViewModel().apply { sdkIntProvider = { Build.VERSION_CODES.Q } }

        deleteWithConsent(viewModel, b)
        assertEquals(recoverable, viewModel.uiState.value.recoveryRequest)
        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.tags.map { it.tag.id })

        viewModel.onRecoveryGranted()
        advanceUntilIdle()

        assertEquals(CustomTagIds.EMPTY, idsOnDisk("1"))
        assertEquals(CustomTagIds.EMPTY, idsOnDisk("2"))
        coVerify(exactly = 1) { tagSaveFinalizer.onSongsSaved(listOf(first, second)) }
        val state = viewModel.uiState.value
        assertNull(state.recoveryRequest)
        assertNull(state.message)
        assertEquals(listOf(1L), state.tags.map { it.tag.id })
        assertEquals(emptyList<String>(), cacheDir.list().orEmpty().toList())
    }

    // The chosen document receives the list file's codec output for both lists, then "Tags exported". Mutation: write
    // the field codec (RollaTagsJson) instead → text mismatch → RED.
    @Test
    fun export_writesTheExactJson() = runTest {
        createEntries(CustomTagKind.TAG, "Techno", "Acid")
        createEntries(CustomTagKind.SUBTAG, "Hard")
        val viewModel = newViewModel()

        viewModel.onExportDocumentChosen(EXPORT_URI)
        advanceUntilIdle()

        val expected = CustomTagListFile(
            tags = listOf(CustomTag(CustomTagKind.TAG, 1L, "Techno"), CustomTag(CustomTagKind.TAG, 2L, "Acid")),
            subTags = listOf(CustomTag(CustomTagKind.SUBTAG, 1L, "Hard")),
        )
        assertEquals(mapOf(EXPORT_URI to CustomTagListJson.encode(expected)), documents.written)
        val state = viewModel.uiState.value
        assertEquals("Tags exported", state.message)
        assertFalse(state.isBusy)
    }

    // A document the provider refuses to write: "Couldn't export the tags.", and the screen is free again. Mutation:
    // swallow the failure silently → message null → RED.
    @Test
    fun export_writeFails_showsCouldNotExport() = runTest {
        createEntries(CustomTagKind.TAG, "Techno")
        documents.failWrites = true
        val viewModel = newViewModel()

        viewModel.onExportDocumentChosen(EXPORT_URI)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Couldn't export the tags.", state.message)
        assertFalse(state.isBusy)
    }

    // A file the codec refuses (two tags share ID 1) and a document that cannot be read both say "Couldn't read that
    // file." with no confirmation and the lists unchanged. Mutation: apply what could be read (an empty file) → lists
    // changed → RED.
    @Test
    fun import_malformed_showsCouldNotReadAndChangesNothing() = runTest {
        val (techno) = createEntries(CustomTagKind.TAG, "Techno")
        documents.texts[IMPORT_URI] = """{"format":"rolla-tags","version":1,""" +
            """"tags":[{"id":1,"name":"Jazz"},{"id":1,"name":"House"}],"subTags":[]}"""
        val viewModel = newViewModel()

        viewModel.onImportDocumentChosen(IMPORT_URI)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Couldn't read that file.", state.message)
        assertNull(state.importConfirm)
        assertFalse(state.isBusy)
        assertEquals(listOf(ManagedTag(techno, 0)), state.tags)

        viewModel.dismissMessage()
        viewModel.onImportDocumentChosen(UNREADABLE_URI)
        advanceUntilIdle()

        assertEquals("Couldn't read that file.", viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.importConfirm)
        assertEquals(listOf(ManagedTag(techno, 0)), viewModel.uiState.value.tags)
    }

    // Songs carry Techno (2) and House (5); the file renames 2 to Jazz and lacks 5. The confirmation lists both lines,
    // nothing changes until Import, then both lists are the file's and "Tags imported". Mutation: apply before the
    // confirmation → lists replaced before confirm → RED.
    @Test
    fun import_withClashes_showsLinesThenReplacesOnConfirm() = runTest {
        createEntries(CustomTagKind.TAG, "Acid", "Techno", "Deep", "Dub", "House")
        createEntries(CustomTagKind.SUBTAG, "Hard")
        (1..14).forEach { seedSong("$it", links = tagIds(2L)) }
        (15..17).forEach { seedSong("$it", links = tagIds(5L)) }
        val file = CLASHING_FILE
        documents.texts[IMPORT_URI] = CustomTagListJson.encode(file)
        val viewModel = newViewModel()

        viewModel.onImportDocumentChosen(IMPORT_URI)
        advanceUntilIdle()

        val confirm = viewModel.uiState.value.importConfirm
        assertEquals(file, confirm?.file)
        assertEquals(
            listOf("2: Techno → Jazz, on 14 tracks", "5: House → removed, on 3 tracks"),
            confirm?.clashes?.map { it.warningLine() },
        )
        assertEquals(listOf("Acid", "Deep", "Dub", "House", "Techno"), viewModel.uiState.value.tags.map { it.tag.name })
        assertFalse(viewModel.uiState.value.isBusy)

        viewModel.onImportConfirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.importConfirm)
        assertEquals(file.tags, state.tags.map { it.tag }.sortedBy { it.id })
        assertEquals(file.subTags, state.subTags.map { it.tag })
        assertEquals("Tags imported", state.message)
        assertFalse(state.isBusy)
    }

    // Cancel closes the confirmation and changes nothing. Mutation: apply on dismiss → lists replaced → RED.
    @Test
    fun import_cancel_changesNothing() = runTest {
        val (techno) = createEntries(CustomTagKind.TAG, "Techno")
        seedSong("1", links = tagIds(1L))
        documents.texts[IMPORT_URI] = CustomTagListJson.encode(
            CustomTagListFile(tags = listOf(CustomTag(CustomTagKind.TAG, 1L, "Jazz")), subTags = emptyList()),
        )
        val viewModel = newViewModel()

        viewModel.onImportDocumentChosen(IMPORT_URI)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.importConfirm?.clashes?.size)

        viewModel.onImportDismiss()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.importConfirm)
        assertNull(state.message)
        assertEquals(listOf(ManagedTag(techno, 1)), state.tags)
    }

    private companion object {
        const val EXPORT_URI = "content://com.android.providers.downloads.documents/document/10"
        const val IMPORT_URI = "content://com.android.providers.downloads.documents/document/11"
        const val UNREADABLE_URI = "content://com.android.providers.downloads.documents/document/12"

        /** Keeps Acid (1) and Deep (3), renames Techno (2) to Jazz, lacks Dub (4) and House (5), adds Ambient (7). */
        val CLASHING_FILE = CustomTagListFile(
            tags = listOf(
                CustomTag(CustomTagKind.TAG, 1L, "Acid"),
                CustomTag(CustomTagKind.TAG, 2L, "Jazz"),
                CustomTag(CustomTagKind.TAG, 3L, "Deep"),
                CustomTag(CustomTagKind.TAG, 7L, "Ambient"),
            ),
            subTags = listOf(CustomTag(CustomTagKind.SUBTAG, 1L, "Peak time")),
        )
    }
}

/**
 * The list file's documents in memory: [written] holds each export's text by uri, [texts] serves imports (a uri absent
 * from it cannot be opened), and [failWrites] makes every write fail as a refusing provider would.
 */
private class FakeCustomTagListDocuments : CustomTagListDocuments {
    val written = mutableMapOf<String, String>()
    val texts = mutableMapOf<String, String>()
    var failWrites = false

    override suspend fun write(uri: String, text: String) {
        if (failWrites) throw IOException("The provider refused $uri")
        written[uri] = text
    }

    override suspend fun read(uri: String): String = texts[uri] ?: throw FileNotFoundException(uri)
}

private fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    mediaStoreId = id.toLong(),
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    contentUri = contentUri,
    artworkUri = artworkUri,
    dateModified = 1_000L,
    folderPath = "Music",
)
