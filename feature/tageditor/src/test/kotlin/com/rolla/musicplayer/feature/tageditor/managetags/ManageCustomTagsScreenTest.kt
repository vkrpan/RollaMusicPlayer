package com.rolla.musicplayer.feature.tageditor.managetags

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.data.repository.ImportClash
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * The Manage tags screen (custom tags phase 3, Task 3), driven through [Harness]: a stand-in for
 * [ManageCustomTagsViewModel] that applies each event to the state the way the ViewModel does (a row tap opens the
 * name dialog prefilled with the entry's name, Delete opens the confirmation with its track count), so each test
 * exercises the screen's half of the round trip.
 */
// NATIVE graphics: under LEGACY, Robolectric measures text at a fixed size, which makes the wrap geometry meaningless.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManageCustomTagsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The Tags page: the tab row, its search field, the "New tag" row, then each entry with its "N tracks" subtitle,
    // one TalkBack node ("Techno, 14 tracks") with Rename and Delete actions; the Subtags tab moves to the Subtags
    // page. Stub: an empty screen → "Tags" missing. Mutation: drop the subtitle → "3 tracks" not found → RED.
    @Test
    fun showsTabsSearchAndRowsWithCounts() {
        val harness = Harness(listsState())
        setScreen(harness)

        composeRule.onNodeWithText("Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Subtags").assertIsDisplayed()
        composeRule.onNodeWithText("Search tags", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("New tag").assertIsDisplayed()
        composeRule.onNodeWithText("3 tracks", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasText("Hard") and hasText("3 tracks")).assertIsDisplayed()
        composeRule.onNode(hasText("Techno") and hasText("14 tracks"))
            .assertIsDisplayed()
            .assert(customActionLabels(listOf("Rename", "Delete")))

        composeRule.onNodeWithText("Subtags").performClick()
        assertEquals("the tab reports its page", CustomTagKind.SUBTAG, harness.state.page)
        composeRule.onNodeWithText("Search subtags", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("New subtag").assertIsDisplayed()
        composeRule.onNode(hasText("Peak time") and hasText("1 track")).assertIsDisplayed()
    }

    // A row tap opens the rename dialog, titled for its list, its field prefilled with the entry's name. Stub: no row
    // → nothing to tap. Mutation: open an empty dialog (the screen hands the dialog initialName "") → field text "" →
    // RED. LEGACY looper: a dialog hosting a text field never idles under the PAUSED looper (the Robolectric hang
    // OneUiDialogFieldTest and CustomTagPickerSheetTest document).
    @Suppress("DEPRECATION")
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun rowTap_opensRenameDialogPrefilled() {
        val harness = Harness(listsState())
        setScreen(harness)

        composeRule.onNodeWithText("Hard").performClick()

        assertEquals("the tap renames the tapped entry", listOf(HARD), harness.renamed)
        composeRule.onNodeWithText("Rename tag").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Hard")))
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
    }

    // The row's ⋮ → Delete opens the confirmation with the entry's name and track count; its Delete confirms. Stub: no
    // ⋮. Mutation: a static body ("Remove this tag from its tracks?") → text mismatch → RED.
    @Test
    fun rowMenu_delete_opensConfirmWithCount() {
        val harness = Harness(listsState())
        setScreen(harness)

        composeRule.onNodeWithContentDescription("More options for Hard").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals("Delete asks to delete the row's entry", listOf(HARD), harness.deleteClicks)
        composeRule.onNodeWithText("Delete \"Hard\"?").assertIsDisplayed()
        composeRule.onNodeWithText("Remove \"Hard\" from 3 tracks?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals("the dialog's Delete confirms", 1, harness.deleteConfirms)
    }

    // An entry no track carries: the confirmation has its title and buttons but no body. Control: the title shows.
    // Mutation: always show the body → "from 0 tracks" found → RED.
    @Test
    fun deleteConfirm_zeroTracks_hasNoBody() {
        val harness = Harness(listsState().copy(deleteConfirm = DeleteConfirm(ACID, songCount = 0)))
        setScreen(harness)

        composeRule.onNodeWithText("Delete \"Acid\"?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNode(hasText("from 0 tracks", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasText("Remove", substring = true)).assertDoesNotExist()
    }

    // "Updated X of N tracks." shows as a transient line, then is reported dismissed and goes. Stub: nothing shown.
    // Mutation: never dismiss → the dismissal is never reported → RED.
    @Test
    fun message_isShownAndDismissed() {
        val harness = Harness(listsState())
        setScreen(harness)

        composeRule.runOnIdle { harness.state = harness.state.copy(message = MESSAGE) }
        composeRule.onNodeWithText(MESSAGE).assertIsDisplayed()
        assertEquals("not dismissed while shown", 0, harness.dismissedMessages)

        composeRule.mainClock.advanceTimeBy(SNACKBAR_SHOWN_MS)
        composeRule.waitForIdle()
        assertEquals("the shown message is reported dismissed once", 1, harness.dismissedMessages)
        composeRule.onNodeWithText(MESSAGE).assertDoesNotExist()
    }

    // 360 dp at fontScale 1.5: a 40-character name wraps onto more lines instead of ellipsizing. Stub: no row.
    // Mutation: maxLines = 1 with an ellipsis on the name → hasVisualOverflow == true → RED.
    @Test
    fun largeFont_rowsWrapWithoutOverflow() {
        val long = "Progressive House And Melodic Techno Mix"
        check(long.length == 40)
        val harness = Harness(
            ManageCustomTagsUiState(
                tags = listOf(ManagedTag(CustomTag(CustomTagKind.TAG, id = 9L, name = long), songCount = 12)),
            ),
        )
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT)) {
                    HarnessScreen(harness)
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(long, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("the 40-character name is not clipped or ellipsized", layouts.single().hasVisualOverflow)
        assertEquals("the name wraps", true, layouts.single().lineCount > 1)
        composeRule.onNodeWithText("12 tracks", useUnmergedTree = true).assertIsDisplayed()
    }

    // The search filters the rows (the ViewModel keeps only the query); a query matching nothing says "No matches",
    // an empty list "No tags yet". Control: "Techno" stays. Mutation: show the unfiltered list → "Hard" found → RED.
    @Test
    fun query_filtersRows_andEmptyStates() {
        val harness = Harness(listsState().copy(tagQuery = "tech"))
        setScreen(harness)

        composeRule.onNode(hasText("Techno") and hasText("14 tracks")).assertIsDisplayed()
        composeRule.onNodeWithText("Hard").assertDoesNotExist()

        composeRule.runOnIdle { harness.state = harness.state.copy(tagQuery = "zzz") }
        composeRule.onNodeWithText("No matches").assertIsDisplayed()

        composeRule.runOnIdle { harness.state = harness.state.copy(tags = emptyList(), tagQuery = "") }
        composeRule.onNodeWithText("No tags yet").assertIsDisplayed()
    }

    // While a delete runs (isBusy) the screen cannot be left: system Back is swallowed and the bar's Navigate up is
    // disabled, so the loop is never cancelled mid-way. Control: when not busy, Back reaches the host and Navigate up
    // works. Mutation: drop the screen's BackHandler → the host sees the busy Back → RED.
    @Test
    fun busy_locksLeavingTheScreen() {
        val harness = Harness(listsState().copy(isBusy = true))
        var hostBacks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                BackHandler { hostBacks++ }
                HarnessScreen(harness)
            }
        }

        composeRule.onNodeWithContentDescription("Navigate up").assertIsNotEnabled()
        pressBack()
        assertEquals("busy: Back is swallowed", 0, hostBacks)

        composeRule.runOnIdle { harness.state = harness.state.copy(isBusy = false) }
        composeRule.waitForIdle()
        pressBack()
        assertEquals("not busy: Back reaches the host", 1, hostBacks)
        composeRule.onNodeWithContentDescription("Navigate up").assertIsEnabled().performClick()
        assertEquals("not busy: Navigate up leaves", 1, harness.navigateUps)
    }

    // The import confirmation: its title, the replace line, then "These tracks will show different tags:" and one line
    // per clash; Import confirms and Cancel dismisses. With no clashes only the replace line shows. Stub: no dialog.
    // Mutation: omit the lines → "2: Techno → Jazz, on 14 tracks" not found → RED.
    @Test
    fun importScreen_confirmDialogListsClashes() {
        val clashes = listOf(
            ImportClash(CustomTagKind.TAG, id = 2L, currentName = "Techno", importedName = "Jazz", songCount = 14),
            ImportClash(CustomTagKind.TAG, id = 5L, currentName = "House", importedName = null, songCount = 3),
        )
        val harness = Harness(listsState().copy(importConfirm = ImportConfirm(IMPORTED, clashes)))
        setScreen(harness)

        composeRule.onNodeWithText("Replace your tags?").assertIsDisplayed()
        composeRule.onNodeWithText("Importing replaces your tags and subtags.").assertIsDisplayed()
        composeRule.onNodeWithText("These tracks will show different tags:").assertIsDisplayed()
        composeRule.onNodeWithText("2: Techno → Jazz, on 14 tracks").assertIsDisplayed()
        composeRule.onNodeWithText("5: House → removed, on 3 tracks").assertIsDisplayed()
        composeRule.onNodeWithText("Import").performClick()
        assertEquals("the dialog's Import confirms", 1, harness.importConfirms)

        val noClashes = ImportConfirm(IMPORTED, clashes = emptyList())
        composeRule.runOnIdle { harness.state = harness.state.copy(importConfirm = noClashes) }
        composeRule.onNodeWithText("Importing replaces your tags and subtags.").assertIsDisplayed()
        composeRule.onNodeWithText("These tracks will show different tags:").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals("Cancel dismisses", 1, harness.importDismisses)
        composeRule.onNodeWithText("Replace your tags?").assertDoesNotExist()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun setScreen(harness: Harness) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { HarnessScreen(harness) }
        }
    }

    private fun customActionLabels(labels: List<String>): SemanticsMatcher =
        SemanticsMatcher("custom actions $labels") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label } == labels
        }

    /** The screen's state and every event, applied the way [ManageCustomTagsViewModel] applies them. */
    private class Harness(initial: ManageCustomTagsUiState) {
        var state by mutableStateOf(initial)
        val renamed = mutableListOf<CustomTag>()
        val deleteClicks = mutableListOf<CustomTag>()
        var deleteConfirms = 0
        var dismissedMessages = 0
        var navigateUps = 0
        var importConfirms = 0
        var importDismisses = 0
    }

    // LongMethod: one lambda per screen event, each the ViewModel's state change.
    @Suppress("LongMethod")
    @Composable
    private fun HarnessScreen(harness: Harness) {
        ManageCustomTagsScreen(
            uiState = harness.state,
            onNavigateUp = { harness.navigateUps++ },
            onPageChange = { harness.state = harness.state.copy(page = it) },
            onQueryChange = { kind, query ->
                harness.state = when (kind) {
                    CustomTagKind.TAG -> harness.state.copy(tagQuery = query)
                    CustomTagKind.SUBTAG -> harness.state.copy(subTagQuery = query)
                }
            },
            onCreateClick = { _, _ -> },
            onRenameClick = { tag ->
                harness.renamed += tag
                harness.state = harness.state.copy(
                    nameDialog = CustomTagNameDialogState(kind = tag.kind, initialName = tag.name),
                    renaming = tag,
                )
            },
            onNameConfirm = {},
            onNameDialogDismiss = { harness.state = harness.state.copy(nameDialog = null, renaming = null) },
            onDeleteClick = { tag ->
                harness.deleteClicks += tag
                val count = (harness.state.tags + harness.state.subTags).first { it.tag == tag }.songCount
                harness.state = harness.state.copy(deleteConfirm = DeleteConfirm(tag, count))
            },
            onDeleteConfirm = {
                harness.deleteConfirms++
                harness.state = harness.state.copy(deleteConfirm = null)
            },
            onDeleteDismiss = { harness.state = harness.state.copy(deleteConfirm = null) },
            onDismissMessage = {
                harness.dismissedMessages++
                harness.state = harness.state.copy(message = null)
            },
            onExportClick = {},
            onImportClick = {},
            onImportConfirm = {
                harness.importConfirms++
                harness.state = harness.state.copy(importConfirm = null)
            },
            onImportDismiss = {
                harness.importDismisses++
                harness.state = harness.state.copy(importConfirm = null)
            },
        )
    }

    private companion object {
        val ACID = CustomTag(CustomTagKind.TAG, id = 6L, name = "Acid")
        val HARD = CustomTag(CustomTagKind.TAG, id = 3L, name = "Hard")
        val TECHNO = CustomTag(CustomTagKind.TAG, id = 2L, name = "Techno")
        val PEAK_TIME = CustomTag(CustomTagKind.SUBTAG, id = 1L, name = "Peak time")
        val IMPORTED = CustomTagListFile(tags = listOf(CustomTag(CustomTagKind.TAG, 2L, "Jazz")), subTags = emptyList())
        const val MESSAGE = "Updated 2 of 3 tracks."
        const val LARGE_FONT = 1.5f

        /** Longer than a short snackbar (4 s) stays up, accessibility timeouts aside. */
        const val SNACKBAR_SHOWN_MS = 10_000L

        /** Both lists in library order with their counts, on the Tags page. */
        fun listsState() = ManageCustomTagsUiState(
            tags = listOf(
                ManagedTag(ACID, songCount = 0),
                ManagedTag(HARD, songCount = 3),
                ManagedTag(TECHNO, songCount = 14),
            ),
            subTags = listOf(ManagedTag(PEAK_TIME, songCount = 1)),
        )
    }
}
