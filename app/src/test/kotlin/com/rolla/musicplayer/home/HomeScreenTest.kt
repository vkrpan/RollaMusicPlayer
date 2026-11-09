package com.rolla.musicplayer.home

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.chipLabel
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.ui.SongSelectionState
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkActions
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkResult
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkUiState
import com.rolla.musicplayer.feature.tageditor.SelectionArtworkViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

// NATIVE graphics: the swipe test relies on real layout (the gesture spans the page's measured width), and LEGACY mode
// measures text lines at ~36 dp / ~1 px per char. Nothing here captures pixels. The plain Application keeps
// Robolectric from booting RollaApp's real Hilt graph (PlaybackController, artwork cache) for a stateless screen test.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var pagerState: PagerState
    private lateinit var selection: SongSelectionState
    private lateinit var listState: LazyListState

    private var searchClicks = 0
    private var settingsClicks = 0
    private var createPlaylistClicks = 0
    private var editTagsClicks = 0
    private var rowTaps = 0
    private var rowLongClicks = 0

    // Ruling 8: Home's access state, fed to HomeScreen by both helpers. Tests flip it with runOnIdle (one setContent).
    private var accessGranted by mutableStateOf(true)

    // setHome's composition view, so a test can dispatch its own window insets (Robolectric reports none), and the
    // list bottom padding the Tracks page reads inside the panel.
    private lateinit var view: View
    private var pageListBottomPadding = Dp.Unspecified

    // Captured inside the theme by setHome: songTitle's line height, and labelLarge's, which is M3's default menu-item
    // text style (MenuTokens.ListItemLabelTextFont). The Home menu test tells the two apart by rendered text height.
    private var songTitleLineHeight = Dp.Unspecified
    private var menuDefaultLineHeight = Dp.Unspecified

    // setHomeWithActions: what HomeScreen hands the artwork-actions slot, the working flag fed back to it, and a
    // snackbar host with a scope to show messages on.
    private var actionIds: List<Long> = emptyList()
    private var actionResult: (SelectionArtworkResult) -> Unit = {}
    private var busy by mutableStateOf(false)

    // setHomeAtLargeFont: its font scale (one composition flips it) and the two title sizes read inside the theme.
    private var fontScale by mutableStateOf(LARGE_FONT)
    private var screenTitleSize = TextUnit.Unspecified
    private var chipLabelSize = TextUnit.Unspecified
    private val snackbarHostState = SnackbarHostState()
    private lateinit var snackbarScope: CoroutineScope

    @Test
    fun startsOnTracksAndTitleIsRollaMusic() {
        setHome()

        composeRule.onNodeWithText("page-TRACKS").assertIsDisplayed()
        composeRule.onNodeWithText("Rolla Music")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Tracks").assertIsSelected()
        assertEquals(HomeTab.TRACKS.ordinal, pagerState.currentPage)
    }

    // Mutation: HomeTabsAndPager's titles drop the last entry → "Folders" is missing → RED. (Opening on Tracks is
    // pinned by startsOnTracksAndTitleIsRollaMusic.)
    @Test
    fun `home shows all six tab titles`() {
        setHome()

        HomeTab.entries.forEach { composeRule.onNodeWithText(it.title).assertExists() }
    }

    // Mutation: swap ALBUMS and ARTISTS in the HomeTab enum → the first swipe lands on page-ARTISTS → RED. (The test
    // host renders "page-<NAME>" itself, so HomeRoute's when-dispatch is not visible here; the device check covers it.)
    @Test
    fun `swiping left from Tracks reaches Albums then Artists`() {
        setHome()

        composeRule.onNodeWithText("page-TRACKS").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("page-ALBUMS").assertIsDisplayed()
        composeRule.onNodeWithText("page-ALBUMS").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("page-ARTISTS").assertIsDisplayed()
    }

    @Test
    fun addButtonOnlyOnPlaylistsAndOpensCreate() {
        setHome()
        composeRule.onNodeWithContentDescription("Create playlist").assertDoesNotExist()

        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("page-PLAYLISTS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Create playlist").assertIsDisplayed().performClick()
        assertEquals("+ must request the create dialog once", 1, createPlaylistClicks)
    }

    // Ruling P22: Home's panel ends at the navigation bar's top (the black background shows behind the bar, as on the
    // reference), and its lists pad for the pill alone. The test sends a 48 dp navigation bar. Control: the panel
    // content starts below the tab row and the root reaches the window bottom. Mutations: drop the pager's bottom
    // navigationBars inset → the panel reaches the root bottom → RED; drop the LocalNavigationBarPadded provider → the
    // page's list padding includes the bar (48 dp) → RED.
    @Test
    fun panelEndsAtTheNavigationBarTopAndListsPadForThePillAlone() {
        setHome()
        val navigationBar = 48.dp
        sendNavigationBar(navigationBar)
        val root = composeRule.onRoot().getBoundsInRoot()
        val page = composeRule.onNodeWithTag("${PAGE_TAG_PREFIX}TRACKS").getBoundsInRoot()
        assertTrue("control: the panel content starts below the tab row (${page.top})", page.top > 0.dp)
        page.bottom.assertIsEqualTo(root.bottom - navigationBar, "panel bottom", 0.5.dp)
        composeRule.runOnIdle {
            assertEquals("list bottom padding inside the panel (dp)", 0f, pageListBottomPadding.value, 0.01f)
        }
    }

    // Ruling P26: the permission prompt, which the gate draws in place of the tabs and pager (pagerContainer), also
    // ends at the navigation bar's top, so spec §7.4's "Home's content ends at the navigation bar" holds with access
    // denied. Control: the prompt starts below the header (fails if the container drew nothing). Mutations: keep the
    // inset on the pager only → the prompt reaches the root bottom → RED; drop the provider around pagerContainer →
    // the prompt's list padding includes the bar → RED.
    @Test
    fun permissionPromptAlsoEndsAtTheNavigationBarTop() {
        var promptListBottomPadding = Dp.Unspecified
        setHomeWithPrompt { promptListBottomPadding = it }
        val navigationBar = 48.dp
        sendNavigationBar(navigationBar)
        val root = composeRule.onRoot().getBoundsInRoot()
        val prompt = composeRule.onNodeWithTag(PROMPT_TAG).getBoundsInRoot()
        assertTrue("control: the prompt starts below the header (${prompt.top})", prompt.top > 0.dp)
        prompt.bottom.assertIsEqualTo(root.bottom - navigationBar, "prompt bottom", 0.5.dp)
        composeRule.runOnIdle {
            assertEquals("list bottom padding inside the prompt (dp)", 0f, promptListBottomPadding.value, 0.01f)
        }
    }

    // Phase 7 calibration: the + draws in the larger header icon box (28 dp, ≈ 18 dp glyph, as measured); Search
    // keeps iconSize. The unmerged Icon node's bounds are the glyph box. Control: Search's box is iconSize, so the two
    // differ.
    // Mutation: drop iconSize = headerActionIconLarge from the + button (24 dp) → RED.
    @Test
    fun addIconUsesTheLargeHeaderIconBox() {
        setHome()
        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        val add = composeRule.onNodeWithContentDescription("Create playlist", useUnmergedTree = true).getBoundsInRoot()
        val search = composeRule.onNodeWithContentDescription("Search", useUnmergedTree = true).getBoundsInRoot()
        (search.right - search.left).assertIsEqualTo(RollaDimens.iconSize, "control: Search glyph box", 0.5.dp)
        (add.right - add.left).assertIsEqualTo(RollaDimens.headerActionIconLarge, "+ glyph box", 0.5.dp)
    }

    // Ruling 8: after a restore with access denied, + would open a dialog PlaylistsTab never composes (a dead control).
    // Trivial stub: + always shown on Playlists → it still exists after the flip. Mutation: ignore accessGranted for
    // + → RED on the last assertion.
    @Test
    fun createPlaylistIsHiddenWhileAccessIsDenied() {
        setHome()
        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        assertEquals("the pager settles on Playlists", HomeTab.PLAYLISTS.ordinal, pagerState.settledPage)
        composeRule.onNodeWithContentDescription("Create playlist")
            .assertExists("with access granted, Playlists shows +")

        composeRule.runOnIdle { accessGranted = false }
        assertEquals("with access denied, Playlists shows no +", 0, descriptionCount("Create playlist"))
    }

    // T2 M1: HomeRoute's create-dialog flag is saveable, so a flag set while access was granted would pop the dialog
    // after a re-grant. HomeRoute needs Hilt (its ViewModel and the tabs'), so the reset lives in HomeScreen behind
    // onAccessLost, and the host (setHomeWithCreateDialog) keeps the flag the way HomeRoute does. Trivial stub:
    // onAccessLost never called → the dialog returns with access. Mutation: drop the onAccessLost() call → RED on the
    // last assertion. Control: + opens the dialog first.
    @Test
    fun aCreateDialogRequestedBeforeAccessWasLostDoesNotReturnWithAccess() {
        setHomeWithCreateDialog()
        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Create playlist").performClick()
        assertEquals("control: + opens the create dialog while access is granted", 1, textCount(CREATE_DIALOG))

        composeRule.runOnIdle { accessGranted = false }
        assertEquals("with access denied the prompt replaces the pages", 1, textCount("prompt"))

        composeRule.runOnIdle { accessGranted = true }
        composeRule.onNodeWithText("page-PLAYLISTS").assertExists("with access back, Playlists shows again")
        assertEquals(
            "a create dialog requested before access was lost does not return with access",
            0,
            textCount(CREATE_DIALOG),
        )
    }

    // Ruling 8: a selection must not act on rows hidden behind the prompt; losing access clears it rather than hiding
    // the bar. Trivial stub: the selection bar regardless of access → "1 selected" survives the flip. Mutation: hide
    // the bar without clearing → RED on the last assertion ("1 selected" returns with access).
    @Test
    fun deniedAccessClearsTheSelectionAndShowsTheHeader() {
        setHomeWithTappableRows()
        composeRule.onNodeWithTag(rowTag(0)).performTouchInput { longClick() }
        composeRule.onNodeWithText("1 selected").assertExists("a long press selects the row")

        composeRule.runOnIdle { accessGranted = false }
        composeRule.onNodeWithText("Rolla Music").assertExists("with access denied, Home shows its header")
        assertEquals("with access denied, there is no selection bar", 0, textCount("1 selected"))

        composeRule.runOnIdle { accessGranted = true }
        assertEquals("access lost cleared the selection: it does not return with access", 0, textCount("1 selected"))
        composeRule.onNodeWithText("Rolla Music").assertExists("with access back, the header stays")
    }

    @Test
    fun selectionTurnsHeaderIntoSelectionBarAndBackClears() {
        setHome()

        composeRule.onNodeWithText("select").performClick()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close selection").assertIsDisplayed()
        composeRule.onNodeWithText("Rolla Music").assertDoesNotExist()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertFalse("Back must clear the selection", selection.isActive)
        composeRule.onNodeWithText("Rolla Music").assertIsDisplayed()
    }

    @Test
    fun editTagsInSelectionBarInvokesCallback() {
        setHome()

        composeRule.onNodeWithText("select").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()

        assertEquals("Edit tags must fire once", 1, editTagsClicks)
    }

    @Test
    fun swipeIsDisabledWhileSelecting() {
        setHome()
        composeRule.onNodeWithText("select").performClick()

        // Tab clicks are locked too: the page must not move, and the tab reports disabled (no dead tabs).
        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        assertEquals("A tab click while selecting must not page", HomeTab.TRACKS.ordinal, pagerState.currentPage)
        composeRule.onNodeWithText("Playlists").assertIsNotEnabled()

        // The page text spans the page's width, so the swipe crosses the whole pager (its ancestor receives it).
        composeRule.onNodeWithText("page-TRACKS").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals("A swipe while selecting must not change the page", HomeTab.TRACKS.ordinal, pagerState.currentPage)

        composeRule.runOnIdle { selection.clear() }
        composeRule.onNodeWithText("page-TRACKS").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals("A swipe with no selection must page", HomeTab.PLAYLISTS.ordinal, pagerState.currentPage)
    }

    // Device defect D2: with paging disabled nothing consumed a horizontal drag, so a sideways swipe that stayed
    // inside a full-width row was delivered to the row's combinedClickable as a tap ("2 selected" became "3").
    @Test
    fun horizontalSwipeWhileSelectingNeitherTapsRowNorPages() {
        setHomeWithTappableRows()
        composeRule.runOnIdle { selection.toggle("x") }

        composeRule.onNodeWithTag(rowTag(0)).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(rowTag(0)).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals("A sideways swipe while selecting must not tap the row", 0, rowTaps)
        assertEquals("A swipe while selecting must not change the page", HomeTab.TRACKS.ordinal, pagerState.currentPage)

        composeRule.onNodeWithTag(rowTag(0)).performClick()
        composeRule.waitForIdle()
        assertEquals("A plain tap while selecting must still reach the row", 1, rowTaps)
    }

    // The drag consumer must not swallow a stationary press: long-press still reaches the row while selecting.
    @Test
    fun longPressWhileSelectingReachesRowAndDoesNotPage() {
        setHomeWithTappableRows()
        composeRule.runOnIdle { selection.toggle("x") }

        composeRule.onNodeWithTag(rowTag(0)).performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals("A long press while selecting must reach the row", 1, rowLongClicks)
        assertEquals("A long press must not also tap the row", 0, rowTaps)
        assertEquals("A long press must not change the page", HomeTab.TRACKS.ordinal, pagerState.currentPage)
    }

    @Test
    fun verticalScrollStillWorksWhileSelecting() {
        setHomeWithTappableRows()
        composeRule.runOnIdle { selection.toggle("x") }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertTrue("The Tracks list must still scroll while selecting", listState.firstVisibleItemIndex > 0)
        assertEquals("A vertical scroll must not tap a row", 0, rowTaps)
    }

    @Test
    fun overflowOpensSettings() {
        setHome()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Settings").performClick()

        assertEquals("Settings must fire once", 1, settingsClicks)
    }

    // Ruling 2: the Home ⋮ menu is the One UI menu: its item is drawn in songTitle and opens Settings. Control: the
    // menu is closed before the tap. Mutation: OneUiMenuItem → stock DropdownMenuItem with default text style → the
    // item's text height differs from songTitle's line height → RED.
    @Test
    fun `the overflow menu is the one ui menu and opens settings`() {
        setHome()
        // The fixture must separate what the probe distinguishes: songTitle (22 sp) vs the default labelLarge (20 sp).
        assertTrue(
            "songTitle ($songTitleLineHeight) and the default menu style ($menuDefaultLineHeight) must differ",
            abs(songTitleLineHeight.value - menuDefaultLineHeight.value) > TEXT_HEIGHT_TOLERANCE_DP,
        )
        composeRule.onNodeWithText("Settings").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("More options").performClick()

        // The item's clickable merges its Text; the unmerged tree finds the Text node itself.
        val text = composeRule.onNodeWithText("Settings", useUnmergedTree = true)
            .assertExists("The open menu must show Settings")
            .getUnclippedBoundsInRoot()
        assertEquals(
            "The menu item's text must be one songTitle line tall",
            songTitleLineHeight.value,
            (text.bottom - text.top).value,
            TEXT_HEIGHT_TOLERANCE_DP,
        )
        assertEquals("Settings must not fire before its tap", 0, settingsClicks)
        composeRule.onNodeWithText("Settings").performClick()
        assertEquals("Settings must fire once", 1, settingsClicks)
    }

    @Test
    fun searchOpensSearch() {
        setHome()

        composeRule.onNodeWithContentDescription("Search").performClick()

        assertEquals("Search must fire once", 1, searchClicks)
    }

    // Spec §5.5 / ruling P2: the selection bar hands the selection to the artwork actions as MediaStore ids, and their
    // result keeps only the failed tracks selected; an empty failed set clears the selection. The fake slot holds the
    // callback HomeScreen gives it. Trivial stub: the slot never composed → no ids → RED. Mutation: clear on every
    // result → "2" is not kept → RED.
    @Test
    fun result_keepsOnlyFailedSelected() {
        setHomeWithActions()
        composeRule.runOnIdle {
            selection.toggle("1")
            selection.toggle("2")
        }
        composeRule.runOnIdle {
            assertEquals("the actions get the selection as ids", listOf(1L, 2L), actionIds)
            actionResult(SelectionArtworkResult(setOf("2")))
        }
        composeRule.runOnIdle {
            assertEquals("only the failed track stays selected", setOf("2"), selection.selectedIds)
        }
        composeRule.onNodeWithText("1 selected").assertExists("the bar counts the kept track")

        composeRule.runOnIdle { actionResult(SelectionArtworkResult(emptySet())) }
        composeRule.runOnIdle { assertFalse("control: an empty failed set clears the selection", selection.isActive) }
    }

    // Plan Task 8: while the actions are working, ✕, Back and "Edit tags" are disabled, and the bar stays even if the
    // selection empties (its effects deliver the result). Control: all three work while idle. Trivial stub: busy
    // ignored → ✕ stays enabled → RED. Mutations: drop navigationEnabled → RED on ✕; Back clears while busy → RED.
    @Test
    fun busy_locksCloseEditTagsAndBack() {
        setHomeWithActions()
        composeRule.runOnIdle { selection.toggle("1") }
        composeRule.onNodeWithContentDescription("Close selection").assertIsEnabled()
        composeRule.onNodeWithText("Edit tags").assertIsEnabled()

        composeRule.runOnIdle { busy = true }
        composeRule.onNodeWithContentDescription("Close selection").assertIsNotEnabled()
        composeRule.onNodeWithText("Edit tags").assertIsNotEnabled()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("Back does not clear the selection while working", selection.isActive)
        composeRule.runOnIdle { selection.clear() }
        composeRule.onNodeWithContentDescription("Close selection").assertExists("the bar stays while working")

        composeRule.runOnIdle { busy = false }
        composeRule.waitForIdle() // the lock is released by the recomposition that follows busy = false
        composeRule.runOnIdle { selection.toggle("1") }
        composeRule.runOnIdle { assertTrue("control: selecting again works after the work", selection.isActive) }
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse("control: Back clears once the work is over", selection.isActive)
    }

    // Insets table (Home row): the snackbar host sits inside HomeBelowHeader, padded by homeListBottomPadding(), so its
    // container ends exactly on top of the pill (64 dp here) above the navigation bar (48 dp). The host's own bounds
    // are measured, not the text's. Control: the message shows inside the host. Trivial stub: no SnackbarHost → RED.
    // Mutation: drop the bottom padding → the host ends at the navigation bar → RED.
    @Test
    fun snackbar_clearsTheNavigationBarAndThePill() {
        setHomeWithActions()
        val navigationBar = 48.dp
        sendNavigationBar(navigationBar)
        composeRule.runOnIdle { snackbarScope.launch { snackbarHostState.showSnackbar(SNACKBAR_TEXT) } }

        val root = composeRule.onRoot().getBoundsInRoot()
        val text = composeRule.onNodeWithText(SNACKBAR_TEXT).assertExists("the message shows").getBoundsInRoot()
        val host = composeRule.onNodeWithTag(HOME_SNACKBAR_HOST_TAG).getBoundsInRoot()
        assertTrue("control: the message is inside the host", text.top >= host.top && text.bottom <= host.bottom)
        host.bottom.assertIsEqualTo(root.bottom - navigationBar - PILL, "snackbar host bottom", 0.5.dp)
    }

    // Ruling P23 (spec §5.5: the selection stays until the write ends): while busy, tapping a selected row keeps it
    // selected and long-pressing an unselected row does not select it. The rows toggle the way TracksTab's do. Control:
    // the same gestures toggle once the work is over. Trivial stub: lock ignored → the tap deselects → RED. Mutations:
    // toggle() ignores isLocked → RED; HomeScreen never sets the lock → RED.
    @Test
    fun busy_freezesTheSelection() {
        setHomeWithActions(page = { tab -> if (tab == HomeTab.TRACKS) SelectableRows() else TaggedPage(tab) })
        composeRule.onNodeWithTag(rowTag(0)).performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals("control: a long press selects", setOf(rowTag(0)), selection.selectedIds) }

        composeRule.runOnIdle { busy = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(rowTag(0)).performClick()
        composeRule.onNodeWithTag(rowTag(1)).performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals("while busy the selection does not change", setOf(rowTag(0)), selection.selectedIds)
        }

        composeRule.runOnIdle { busy = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(rowTag(1)).performClick()
        composeRule.runOnIdle {
            assertEquals(
                "control: taps toggle again after the work",
                setOf(rowTag(0), rowTag(1)),
                selection.selectedIds,
            )
        }
    }

    // Ruling P23: the tabs and pager stay locked while busy, even if the selection empties (only the result, rescan
    // pruning or access loss can empty it). Control: unlocked once the work is over. Trivial stub: lock follows the
    // selection only → the tab click pages → RED. Mutation: HomeTabsAndPager locked = selection.isActive → RED.
    @Test
    fun busy_keepsThePagerLocked() {
        setHomeWithActions()
        composeRule.runOnIdle {
            selection.toggle("1")
            busy = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selection.clear() }
        composeRule.onNodeWithText("Playlists").assertIsNotEnabled()
        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.onNodeWithText("page-TRACKS").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals("the pager stays on Tracks while busy", HomeTab.TRACKS.ordinal, pagerState.currentPage)

        composeRule.runOnIdle { busy = false }
        composeRule.onNodeWithText("Playlists").performClick()
        composeRule.waitForIdle()
        assertEquals("control: a tab click pages after the work", HomeTab.PLAYLISTS.ordinal, pagerState.currentPage)
    }

    // Observation from Task 7: at fontScale 1.5 on a 360 dp width the bar's controls ("N selected", ✕, "Edit tags", ⋮)
    // must not overlap or leave the screen. The real SelectionArtworkActions draws the ⋮, on a stubbed ViewModel.
    // The title's own fit is selectionBar_titleStepsDownOnlyWhenItWouldNotFit's (ruling P24); this test pins the
    // controls. Trivial stub: no ⋮ → RED. Mutation: drop the title's weight(1f) → the title takes the row and "Edit
    // tags" and ⋮ are squeezed → RED on their sizes.
    @Test
    fun selectionBar_atLargeFont_controlsDoNotOverlap() {
        setHomeAtLargeFont()
        // A four-digit count: the longest title a large library gives, so a title that is not weighted squeezes ⋮.
        composeRule.runOnIdle { (1..1000).forEach { selection.toggle("$it") } }

        val root = composeRule.onRoot().getBoundsInRoot()
        val close = composeRule.onNodeWithContentDescription("Close selection").getBoundsInRoot()
        val title = composeRule.onNodeWithText("1000 selected").getBoundsInRoot()
        val editTags = composeRule.onNodeWithText("Edit tags").getBoundsInRoot()
        val more = composeRule.onNodeWithContentDescription("More options").getBoundsInRoot()
        val order = listOf("✕" to close, "title" to title, "Edit tags" to editTags, "⋮" to more)
        order.zipWithNext().forEach { (left, right) ->
            assertTrue(
                "${left.first} (right ${left.second.right}) ends before ${right.first} (left ${right.second.left})",
                left.second.right <= right.second.left + 0.5.dp,
            )
        }
        assertTrue("✕ starts on screen (${close.left})", close.left >= root.left)
        assertTrue("⋮ ends on screen (${more.right} <= ${root.right})", more.right <= root.right + 0.5.dp)
        assertTrue("⋮ keeps its size (${more.right - more.left})", more.right - more.left >= RollaDimens.iconSize)
        val editLabel = textLayout(composeRule.onNodeWithText("Edit tags", useUnmergedTree = true))
        assertEquals("\"Edit tags\" stays on one line", 1, editLabel.lineCount)
        // didOverflowWidth reads true at needs == width (84.0 vs 84 px), so the width is compared with a 1 px margin.
        val needs = editLabel.multiParagraph.intrinsics.maxIntrinsicWidth
        assertTrue(
            "\"Edit tags\" gets its full width (needs $needs px, has ${editLabel.size.width} px)",
            needs <= editLabel.size.width + 1f,
        )
    }

    // Ruling P24: "N selected" keeps screenTitle when it fits and steps down to chipLabel only when it would not, never
    // ellipsized or on two lines. Read from the title node's own TextLayoutResult (its laid-out width, not the bar's).
    // Control: at 1x "120 selected" keeps screenTitle. Trivial stub: no step-down → "120 selected" is ellipsized at
    // 1.5x (156 dp needed, 134 dp given) → RED. Mutation: FittingDetailTitle always uses the default style → RED.
    @Test
    fun selectionBar_titleStepsDownOnlyWhenItWouldNotFit() {
        fontScale = 1f
        setHomeAtLargeFont()
        composeRule.runOnIdle { (1..120).forEach { selection.toggle("$it") } }
        val atOne = textLayout(composeRule.onNodeWithText("120 selected"))
        assertEquals("control: at 1x the title keeps screenTitle", screenTitleSize, atOne.layoutInput.style.fontSize)
        assertFalse("control: at 1x the title is whole", atOne.isLineEllipsized(0))

        composeRule.runOnIdle { fontScale = LARGE_FONT }
        val atLarge = textLayout(composeRule.onNodeWithText("120 selected"))
        assertFalse("at 1.5x \"120 selected\" is not ellipsized", atLarge.isLineEllipsized(0))
        assertEquals("at 1.5x the title is one line", 1, atLarge.lineCount)
        assertEquals("at 1.5x the title steps down to chipLabel", chipLabelSize, atLarge.layoutInput.style.fontSize)
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first()
    }

    /** Home at [fontScale] (default [LARGE_FONT]); its bar draws the real SelectionArtworkActions on a stub. */
    private fun setHomeAtLargeFont() {
        val viewModel: SelectionArtworkViewModel = mockk(relaxed = true) {
            every { uiState } returns MutableStateFlow(SelectionArtworkUiState())
        }
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                screenTitleSize = MaterialTheme.typography.screenTitle.fontSize
                chipLabelSize = MaterialTheme.typography.chipLabel.fontSize
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                    selection = rememberSongSelectionState()
                    HomeScreen(
                        pagerState = rememberHomePagerState(),
                        selection = selection,
                        onSearchClick = {},
                        onSettingsClick = {},
                        onCreatePlaylistClick = {},
                        onEditTagsForSelection = {},
                        selectionActions = { ids, onResult ->
                            SelectionArtworkActions(selectedIds = ids, onResult = onResult, viewModel = viewModel)
                        },
                    ) { tab -> Text("page-${tab.name}") }
                }
            }
        }
    }

    /**
     * Home with a fake artwork-actions slot that records the ids and the result callback HomeScreen hands it, a
     * [busy] flag fed to HomeScreen the way HomeRoute feeds SelectionArtworkActions' working state, and a snackbar
     * host the test can show messages on. The pill footprint is [PILL], as MiniPlayerHost would provide it.
     */
    private fun setHomeWithActions(page: @Composable (HomeTab) -> Unit = { tab -> TaggedPage(tab) }) {
        composeRule.setContent {
            view = LocalView.current
            snackbarScope = rememberCoroutineScope()
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides PILL) {
                    pagerState = rememberHomePagerState()
                    selection = rememberSongSelectionState()
                    HomeScreen(
                        pagerState = pagerState,
                        selection = selection,
                        onSearchClick = {},
                        onSettingsClick = {},
                        onCreatePlaylistClick = {},
                        onEditTagsForSelection = {},
                        selectionActions = { ids, onResult ->
                            actionIds = ids
                            actionResult = onResult
                            Text("actions")
                        },
                        selectionBusy = busy,
                        snackbarHostState = snackbarHostState,
                        pageContent = page,
                    )
                }
            }
        }
    }

    /**
     * Tracks page = a list of full-width combinedClickable rows (like SongListItem) that count taps. A long press also
     * toggles the row into the selection, as SongListItem's long press does.
     */
    private fun setHomeWithTappableRows() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                pagerState = rememberHomePagerState()
                selection = rememberSongSelectionState()
                listState = rememberLazyListState()
                HomeScreen(
                    pagerState = pagerState,
                    selection = selection,
                    onSearchClick = {},
                    onSettingsClick = {},
                    onCreatePlaylistClick = {},
                    onEditTagsForSelection = {},
                    accessGranted = accessGranted,
                ) { tab ->
                    if (tab == HomeTab.TRACKS) {
                        TappableRows()
                    } else {
                        Text("page-${tab.name}", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    /**
     * Home holding a create-dialog flag the way HomeRoute does: + sets it, onAccessLost clears it, the Playlists page
     * draws [CREATE_DIALOG] while it is set, and the container shows "prompt" instead of the pages while access is
     * denied, as the permission gate does.
     */
    private fun setHomeWithCreateDialog() {
        var showCreateDialog by mutableStateOf(false)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                pagerState = rememberHomePagerState()
                selection = rememberSongSelectionState()
                HomeScreen(
                    pagerState = pagerState,
                    selection = selection,
                    onSearchClick = {},
                    onSettingsClick = {},
                    onCreatePlaylistClick = { showCreateDialog = true },
                    onEditTagsForSelection = {},
                    accessGranted = accessGranted,
                    onAccessLost = { showCreateDialog = false },
                    pagerContainer = { pages -> if (accessGranted) pages() else Text("prompt") },
                ) { tab ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("page-${tab.name}", modifier = Modifier.fillMaxWidth())
                        if (tab == HomeTab.PLAYLISTS && showCreateDialog) Text(CREATE_DIALOG)
                    }
                }
            }
        }
    }

    // A composable member: detekt's FunctionNaming wants camelCase, Compose wants PascalCase.
    @Suppress("FunctionNaming")
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun TappableRows() {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(LIST_TAG)) {
            items((0 until ROW_COUNT).toList()) { index ->
                Text(
                    text = "row $index",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag(rowTag(index))
                        .combinedClickable(
                            onClick = { rowTaps++ },
                            onLongClick = {
                                rowLongClicks++
                                selection.toggle(rowTag(index))
                            },
                        ),
                )
            }
        }
    }

    /** Rows that select the way TracksTab's do: a tap toggles while selecting, a long press always toggles. */
    // A composable member: detekt's FunctionNaming wants camelCase, Compose wants PascalCase.
    @Suppress("FunctionNaming")
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SelectableRows() {
        Column(modifier = Modifier.fillMaxSize()) {
            (0 until 3).forEach { index ->
                Text(
                    text = "row $index",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag(rowTag(index))
                        .combinedClickable(
                            onClick = { if (selection.isActive) selection.toggle(rowTag(index)) },
                            onLongClick = { selection.toggle(rowTag(index)) },
                        ),
                )
            }
        }
    }

    private fun rowTag(index: Int) = "row-$index"

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun descriptionCount(description: String): Int =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    // LongMethod: the production HomeScreen's full parameter list plus the captured theme and padding values.
    @Suppress("LongMethod")
    /** Home with access denied: the container draws a full-size [PROMPT_TAG] box and reports its list padding. */
    private fun setHomeWithPrompt(onPromptListBottomPadding: (Dp) -> Unit) {
        composeRule.setContent {
            view = LocalView.current
            RollaMusicPlayerTheme(darkTheme = true) {
                HomeScreen(
                    pagerState = rememberHomePagerState(),
                    selection = rememberSongSelectionState(),
                    onSearchClick = {},
                    onSettingsClick = {},
                    onCreatePlaylistClick = {},
                    onEditTagsForSelection = {},
                    accessGranted = false,
                    pagerContainer = {
                        onPromptListBottomPadding(homeListBottomPadding())
                        Box(Modifier.fillMaxSize().testTag(PROMPT_TAG))
                    },
                ) { }
            }
        }
    }

    /** Sends a bottom navigation bar of [height] to the composition's view, where Compose's insets listener sees it. */
    private fun sendNavigationBar(height: Dp) {
        composeRule.runOnIdle {
            val px = with(composeRule.density) { height.roundToPx() }
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px))
                    .build(),
            )
        }
    }

    private fun setHome() {
        composeRule.setContent {
            view = LocalView.current
            RollaMusicPlayerTheme(darkTheme = true) {
                with(LocalDensity.current) {
                    songTitleLineHeight = MaterialTheme.typography.songTitle.lineHeight.toDp()
                    menuDefaultLineHeight = MaterialTheme.typography.labelLarge.lineHeight.toDp()
                }
                pagerState = rememberHomePagerState()
                selection = rememberSongSelectionState()
                HomeScreen(
                    pagerState = pagerState,
                    selection = selection,
                    onSearchClick = { searchClicks++ },
                    onSettingsClick = { settingsClicks++ },
                    onCreatePlaylistClick = { createPlaylistClicks++ },
                    onEditTagsForSelection = { editTagsClicks++ },
                    accessGranted = accessGranted,
                ) { tab -> TaggedPage(tab) }
            }
        }
    }

    /** A page tagged with its tab; Tracks reports its list padding and has a button that starts a selection. */
    @Composable
    private fun TaggedPage(tab: HomeTab) {
        if (tab == HomeTab.TRACKS) pageListBottomPadding = homeListBottomPadding()
        Column(modifier = Modifier.fillMaxSize().testTag("$PAGE_TAG_PREFIX${tab.name}")) {
            Text("page-${tab.name}", modifier = Modifier.fillMaxWidth())
            if (tab == HomeTab.TRACKS) {
                Button(onClick = { selection.toggle("x") }) { Text("select") }
            }
        }
    }

    private companion object {
        const val PROMPT_TAG = "permission_prompt"
        const val PAGE_TAG_PREFIX = "panel-content-"
        const val LIST_TAG = "rows"
        const val CREATE_DIALOG = "create-dialog"
        const val ROW_COUNT = 40
        const val TEXT_HEIGHT_TOLERANCE_DP = 0.75f
        const val SNACKBAR_TEXT = "Artwork set on 2 tracks"
        const val LARGE_FONT = 1.5f
        val PILL = 64.dp
    }
}
