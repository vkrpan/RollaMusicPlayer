package com.rolla.musicplayer.core.ui.customtags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** The custom tag picker sheet (custom tags phase 2, Task 3) and its "New tag" name dialog. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomTagPickerSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The sheet opens on state.page: Subtags shows its own search field, and the Tags page is not composed. Both tab
    // titles are there. Stub: always page 0. Mutation: the pager's initial page 0 → "Search tags" shown → RED.
    @Test
    fun picker_showsTabsAndOpensOnStatePage() {
        setSheet(pickerState(page = CustomTagKind.SUBTAG))
        composeRule.onNodeWithText("Tags").assertExists()
        composeRule.onNodeWithText("Subtags").assertExists()
        composeRule.onNodeWithText("Search subtags").assertIsDisplayed()
        composeRule.onNodeWithText("Search tags").assertDoesNotExist()
    }

    // The whole row is the toggle: tapping the name reports that entry's kind and ID. Stub: no click. Mutation: toggle
    // only on the checkbox → a tap on the name does nothing → RED.
    @Test
    fun picker_rowTapTogglesThatId() {
        val toggles = mutableListOf<Pair<CustomTagKind, Long>>()
        setSheet(pickerState(), onToggle = { kind, id -> toggles += kind to id })
        composeRule.onNodeWithText("Techno").performClick()
        assertEquals(listOf(CustomTagKind.TAG to 2L), toggles)
    }

    // TalkBack reads the row as one toggle ("Techno, checked"). Stub: no semantics. Mutation: drop the toggleable
    // semantics (a plain clickable row) → no ToggleableState → RED.
    @Test
    fun picker_checkedRowAnnouncesChecked() {
        setSheet(pickerState(selected = CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet())))
        composeRule.onNodeWithText("Techno").assertIsOn()
    }

    // A query that matches nothing says so, and the "New tag" row stays first and hands the query to the dialog.
    // Stub: hide the New row on no match. Mutation: pass "" instead of the query → onCreateClick(TAG, "") → RED.
    @Test
    fun picker_noMatch_showsNoMatchesAndNewRow() {
        val creates = mutableListOf<Pair<CustomTagKind, String>>()
        setSheet(pickerState(tagQuery = "zzz"), onCreateClick = { kind, query -> creates += kind to query })
        composeRule.onNodeWithText("No matches").assertIsDisplayed()
        composeRule.onNodeWithText("Techno").assertDoesNotExist()
        composeRule.onNodeWithText("New tag").assertIsDisplayed().performClick()
        assertEquals(listOf(CustomTagKind.TAG to "zzz"), creates)
    }

    // An empty list invites the first entry instead of claiming no match. Stub: blank. Mutation: show "No matches" for
    // an empty list → RED.
    @Test
    fun picker_emptyList_showsNoTagsYet() {
        setSheet(pickerState(tags = emptyList()))
        composeRule.onNodeWithText("No tags yet").assertIsDisplayed()
        composeRule.onNodeWithText("No matches").assertDoesNotExist()
        composeRule.onNodeWithText("New tag").assertIsDisplayed()
    }

    // M1: with an owner that follows onPageChange, a swipe to Subtags shows that page and reports it exactly once (no
    // report of the page it opened on, no echo when the owner's page comes back). Mutation: drop the "differs from
    // state.page" guard on the settle report → the opening Tags page is reported too → RED.
    @Test
    fun picker_swipe_reportsPageOnce() {
        val reported = mutableListOf<CustomTagKind>()
        setFollowingSheet(reported)
        composeRule.onNodeWithText("Techno").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search subtags").assertIsDisplayed()
        assertEquals(listOf(CustomTagKind.SUBTAG), reported)
    }

    // M2: tap "Subtags", then drag back to Tags before the pager settles. The pager settles on Tags, so the owner must
    // hear TAG (state and pager agree again), and a second "Subtags" tap must still move the pager. Control: the drag
    // really won (the Tags page shows after it). Mutation: report only settledPage changes (the old flow) → the page
    // never settled on Subtags, so nothing is reported, state stays SUBTAG, and the second tap does nothing → RED.
    @Test
    fun picker_tabTapThenDragBack_staysInSync() {
        val reported = mutableListOf<CustomTagKind>()
        setFollowingSheet(reported)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Subtags").performClick()
        repeat(TAB_ANIMATION_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.onNodeWithText("Techno").performTouchInput { swipeRight() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search tags").assertIsDisplayed()
        assertEquals("the owner hears the page the pager settled on", CustomTagKind.TAG, reported.last())

        composeRule.onNodeWithText("Subtags").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search subtags").assertIsDisplayed()
    }

    // The dialog shows the caller's error under the field, and Create hands over the trimmed name. Stub: ignore the
    // error. Mutation: don't render the error → not found → RED.
    // LEGACY looper: a dialog hosting a text field never idles under the PAUSED looper (the Robolectric hang
    // OneUiDialogFieldTest and SongOptionsHostTest document).
    @Suppress("DEPRECATION")
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun nameDialog_showsErrorAndCreatesTrimmedText() {
        val created = mutableListOf<String>()
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CustomTagNameDialog(
                    state = CustomTagNameDialogState(
                        kind = CustomTagKind.TAG,
                        initialName = "",
                        error = DUPLICATE_ERROR,
                    ),
                    onCreate = { created += it },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("New tag").assertIsDisplayed()
        composeRule.onNodeWithText(DUPLICATE_ERROR, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Name").performTextInput("  Techno ")
        composeRule.onNodeWithText("Create").performClick()
        assertEquals(listOf("Techno"), created)
    }

    private fun setSheet(
        state: CustomTagPickerState,
        onToggle: (CustomTagKind, Long) -> Unit = { _, _ -> },
        onCreateClick: (CustomTagKind, String) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CustomTagPickerSheet(
                    state = state,
                    onPageChange = {},
                    onQueryChange = { _, _ -> },
                    onToggle = onToggle,
                    onCreateClick = onCreateClick,
                    onDone = {},
                )
            }
        }
    }

    /** The sheet with an owner that follows [CustomTagPickerSheet]'s onPageChange, recording each report. */
    private fun setFollowingSheet(reported: MutableList<CustomTagKind>) {
        var state by mutableStateOf(pickerState())
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CustomTagPickerSheet(
                    state = state,
                    onPageChange = { kind ->
                        reported += kind
                        state = state.copy(page = kind)
                    },
                    onQueryChange = { _, _ -> },
                    onToggle = { _, _ -> },
                    onCreateClick = { _, _ -> },
                    onDone = {},
                )
            }
        }
    }

    private companion object {
        const val DUPLICATE_ERROR = "That name is already in the list"

        // Frames of the tab's page animation before the drag back: it has started, nowhere near settled.
        const val TAB_ANIMATION_FRAMES = 3

        val TAGS = listOf(
            CustomTag(kind = CustomTagKind.TAG, id = 1L, name = "Acid"),
            CustomTag(kind = CustomTagKind.TAG, id = 2L, name = "Techno"),
        )
        val SUBTAGS = listOf(CustomTag(kind = CustomTagKind.SUBTAG, id = 1L, name = "Peak time"))

        fun pickerState(
            tags: List<CustomTag> = TAGS,
            selected: CustomTagIds = CustomTagIds.EMPTY,
            page: CustomTagKind = CustomTagKind.TAG,
            tagQuery: String = "",
        ) = CustomTagPickerState(
            tags = tags,
            subTags = SUBTAGS,
            selected = selected,
            page = page,
            tagQuery = tagQuery,
        )
    }
}
