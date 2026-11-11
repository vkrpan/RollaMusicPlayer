package com.rolla.musicplayer.feature.tageditor

import com.rolla.musicplayer.core.data.repository.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.data.repository.CustomTagNameResult
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.CustomTagRepositoryImpl
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A minimal editor state: the custom tag part, and whether the editor lets it change. */
private data class FakeEditorState(
    val editing: CustomTagEditing = CustomTagEditing(
        picker = CustomTagPickerState(tags = emptyList(), subTags = emptyList(), selected = CustomTagIds.EMPTY),
        isPickerOpen = false,
        nameDialog = null,
    ),
    val canEdit: Boolean = true,
)

private object FakeEditorAccess : CustomTagEditingAccess<FakeEditorState> {
    override fun read(state: FakeEditorState): CustomTagEditing = state.editing
    override fun write(state: FakeEditorState, editing: CustomTagEditing): FakeEditorState =
        state.copy(editing = editing)
    override fun canEdit(state: FakeEditorState): Boolean = state.canEdit
}

/** [CustomTagRepository] that counts [create] calls and holds each one until [gate] completes. */
private class GatedCreateRepository(
    private val delegate: CustomTagRepository,
    val gate: CompletableDeferred<Unit> = CompletableDeferred(),
) : CustomTagRepository by delegate {
    var createCalls = 0
        private set

    override suspend fun create(kind: CustomTagKind, name: String): CustomTagNameResult {
        createCalls++
        gate.await()
        return delegate.create(kind, name)
    }
}

/**
 * Unit tests for [CustomTagEditingHandlers], the picker handling both tag editors share, over a [FakeEditorState]. The
 * lists come from the real [CustomTagRepositoryImpl] over [FakeCustomTagDao], so names are validated as in the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomTagEditingHandlersTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val customTagDao = FakeCustomTagDao()
    private val customTagRepository = CustomTagRepositoryImpl(
        customTagDao = customTagDao,
        sortDispatcher = mainDispatcherRule.testDispatcher,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )
    private val scope = CoroutineScope(mainDispatcherRule.testDispatcher + Job())
    private val uiState = MutableStateFlow(FakeEditorState())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun newHandlers(repository: CustomTagRepository = customTagRepository) =
        CustomTagEditingHandlers(uiState, scope, repository, FakeEditorAccess)

    private val editing: CustomTagEditing get() = uiState.value.editing

    /** Opens the name dialog for [kind] and confirms [name]; returns the dialog's refusal text, if any. */
    private fun CustomTagEditingHandlers<FakeEditorState>.createAndReadError(kind: CustomTagKind, name: String) =
        run {
            onCreateClick(kind, name)
            onCreateConfirm(name)
            editing.nameDialog?.error
        }

    // Every refusal shows its reason verbatim in the dialog. Mutation: map Duplicate to "Enter a name" → RED on the
    // duplicate line.
    @Test
    fun create_refusals_showEachReasonVerbatim() = runTest {
        customTagRepository.create(CustomTagKind.TAG, "Techno")
        customTagDao.raiseCounter(CustomTagKind.SUBTAG, MAX_CUSTOM_TAG_ID)
        val handlers = newHandlers()

        assertEquals("Enter a name", handlers.createAndReadError(CustomTagKind.TAG, "   "))
        val tooLong = "x".repeat(CUSTOM_TAG_NAME_MAX_LENGTH + 1)
        assertEquals("Use 40 characters or fewer", handlers.createAndReadError(CustomTagKind.TAG, tooLong))
        assertEquals("That name is already in the list", handlers.createAndReadError(CustomTagKind.TAG, "techno"))
        assertEquals("This list is full", handlers.createAndReadError(CustomTagKind.SUBTAG, "Closing"))
        assertTrue("a refusal keeps the dialog open", editing.nameDialog != null)
    }

    // A saved entry closes the dialog and is ticked. Mutation: close the dialog without ticking → RED.
    @Test
    fun create_saved_ticksTheEntryAndClosesTheDialog() = runTest {
        val handlers = newHandlers()

        handlers.onCreateClick(CustomTagKind.SUBTAG, "Warm-up")
        assertEquals(
            "the dialog opens with the search text (control)",
            CustomTagNameDialogState(kind = CustomTagKind.SUBTAG, initialName = "Warm-up"),
            editing.nameDialog,
        )
        handlers.onCreateConfirm("Warm-up")
        advanceUntilIdle()

        assertNull("a saved entry closes the dialog", editing.nameDialog)
        val warmUp = editing.picker.subTags.single { it.name == "Warm-up" }
        assertEquals("the new entry is ticked", CustomTagIds(emptySet(), setOf(warmUp.id)), editing.picker.selected)
    }

    // A second Create while the first runs creates nothing; once it ends, Create works again. Mutation: drop the
    // guard → two calls → RED.
    @Test
    fun create_doubleTap_createsOnce() = runTest {
        val gated = GatedCreateRepository(customTagRepository)
        val handlers = newHandlers(repository = gated)

        handlers.onCreateClick(CustomTagKind.TAG, "Dub")
        handlers.onCreateConfirm("Dub")
        handlers.onCreateConfirm("Dub")
        assertEquals("the second tap is ignored while the first runs", 1, gated.createCalls)

        gated.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("one entry was created", listOf("Dub"), editing.picker.tags.map { it.name })

        handlers.onCreateClick(CustomTagKind.TAG, "Dub")
        handlers.onCreateConfirm("Dub")
        assertEquals("the guard is released once a Create ends (control)", 2, gated.createCalls)
    }

    // While the editor forbids edits, rows, toggles and Create do nothing; page, search, Done and dismiss still work.
    // Mutation: drop the canEdit check from onRowsClick → the picker opens → RED.
    @Test
    fun canEditFalse_ignoresRowsToggleAndCreate() = runTest {
        customTagRepository.create(CustomTagKind.TAG, "Techno")
        val gated = GatedCreateRepository(customTagRepository).apply { gate.complete(Unit) }
        val handlers = newHandlers(repository = gated)
        uiState.value = uiState.value.copy(
            canEdit = false,
            editing = editing.copy(
                isPickerOpen = false,
                nameDialog = CustomTagNameDialogState(kind = CustomTagKind.TAG, initialName = "Dub"),
            ),
        )

        handlers.onRowsClick(CustomTagKind.SUBTAG)
        assertFalse("a row tap does not open the picker", editing.isPickerOpen)
        handlers.onToggle(CustomTagKind.TAG, 1L)
        assertEquals("a toggle ticks nothing", CustomTagIds.EMPTY, editing.picker.selected)
        handlers.onCreateConfirm("Dub")
        assertEquals("Create creates nothing", 0, gated.createCalls)
        handlers.onNameDialogDismiss()
        handlers.onCreateClick(CustomTagKind.TAG, "Dub")
        assertNull("New tag opens no dialog", editing.nameDialog)

        handlers.onPageChange(CustomTagKind.SUBTAG)
        handlers.onQueryChange(CustomTagKind.SUBTAG, "warm")
        assertEquals("the page still changes (control)", CustomTagKind.SUBTAG, editing.picker.page)
        assertEquals("the search still changes (control)", "warm", editing.picker.subTagQuery)
    }

    // A failing custom tag table reads as two empty lists, not as no lists. Mutation: drop the catch → the lists stay
    // null → RED.
    @Test
    fun listsFlowThrows_givesEmptyLists() = runTest {
        val failing = object : CustomTagRepository by customTagRepository {
            override fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTag>> =
                flow { throw IllegalStateException("custom_tags is broken") }
        }
        val handlers = newHandlers(repository = failing)
        advanceUntilIdle()

        assertEquals(
            "a broken table reads as empty lists",
            CustomTagLists(tags = emptyList(), subTags = emptyList()),
            handlers.lists.value,
        )
        assertEquals(emptyList<CustomTag>(), editing.picker.tags + editing.picker.subTags)
    }
}
