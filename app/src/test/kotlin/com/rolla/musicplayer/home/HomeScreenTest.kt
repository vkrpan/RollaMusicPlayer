package com.rolla.musicplayer.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.ui.SongSelectionState
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
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

    // Captured inside the theme by setHome: songTitle's line height, and labelLarge's, which is M3's default menu-item
    // text style (MenuTokens.ListItemLabelTextFont). The Home menu test tells the two apart by rendered text height.
    private var songTitleLineHeight = Dp.Unspecified
    private var menuDefaultLineHeight = Dp.Unspecified

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

    private fun rowTag(index: Int) = "row-$index"

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun descriptionCount(description: String): Int =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun setHome() {
        composeRule.setContent {
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
                ) { tab ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("page-${tab.name}", modifier = Modifier.fillMaxWidth())
                        if (tab == HomeTab.TRACKS) {
                            Button(onClick = { selection.toggle("x") }) { Text("select") }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val LIST_TAG = "rows"
        const val CREATE_DIALOG = "create-dialog"
        const val ROW_COUNT = 40
        const val TEXT_HEIGHT_TOLERANCE_DP = 0.75f
    }
}
