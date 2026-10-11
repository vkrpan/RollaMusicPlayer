package com.rolla.musicplayer.feature.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.QueueItem
import com.rolla.musicplayer.core.media.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QueueSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun queue(n: Int, current: Int, dupOf: Int? = null) = QueueState(
        items = (0 until n).map {
            QueueItem(it, if (dupOf != null && it == n - 1) "id$dupOf" else "id$it", "T$it", "A$it", "")
        },
        currentIndex = current,
    )

    /**
     * A shuffled queue: [windowIndices] in playback order, each titled "T<window index>". [current] is a list position
     * (ruling 2), so the fixture's window indices never equal their positions where it matters.
     */
    private fun shuffledQueue(windowIndices: List<Int>, current: Int) = QueueState(
        items = windowIndices.map { QueueItem(it, "id$it", "T$it", "A$it", "") },
        currentIndex = current,
    )

    private fun show(state: QueueState, onItem: (QueueItem) -> Unit = {}, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                QueueSheet(state, onItemClick = onItem, onDismissRequest = onDismiss)
            }
        }
    }

    // Mutation: mark index 0 as current regardless of currentIndex → T1 not selected → RED.
    @Test
    fun onlyTheCurrentRowIsSelected() {
        show(queue(3, current = 1))
        composeRule.onNodeWithText("Queue").assertIsDisplayed()
        composeRule.onNodeWithText("T1").assertIsSelected()
        composeRule.onNodeWithText("T0").assertIsNotSelected()
    }

    // Mutation: call onDismissRequest after onItemClick → dismissals == 1 → RED (spec §11: keeps the sheet open).
    @Test
    fun tappingARowReportsItAndKeepsTheSheetOpen() {
        var tapped: QueueItem? = null
        var dismissals = 0
        show(queue(3, current = 0), onItem = { tapped = it }, onDismiss = { dismissals++ })
        composeRule.onNodeWithText("T2").performClick()
        assertEquals(2, tapped?.index)
        assertEquals(0, dismissals)
    }

    // Mutation: delete the scroll-to-current effect → T45 is never composed → RED.
    @Test
    fun opensScrolledToTheCurrentItem() {
        show(queue(60, current = 45))
        composeRule.onNodeWithText("T45").assertIsDisplayed()
    }

    // Review Focus 1. Mutation: drop the empty branch (empty LazyColumn) → the text is missing → RED.
    @Test
    fun emptyQueueShowsTheEmptyState() {
        show(QueueState.EMPTY)
        composeRule.onNodeWithText("Queue is empty").assertIsDisplayed()
    }

    // Review Focus 2. Mutation: key rows by mediaId → duplicate key crash → RED; or select by mediaId → two selected.
    @Test
    fun duplicateSongsAreSeparateRowsWithOneCurrent() {
        show(queue(3, current = 2, dupOf = 0))
        composeRule.onNodeWithText("T2").assertIsSelected()
        composeRule.onNodeWithText("T0").assertIsNotSelected()
    }

    // Ruling 2: currentIndex is a list position, not a window index. Position 0 holds window index 2 ("T2").
    // Mutation: isCurrent = item.index == queue.currentIndex → window index 0 ("T0") selected instead → RED.
    @Test
    fun shuffledQueueSelectsByListPositionNotWindowIndex() {
        show(shuffledQueue(windowIndices = listOf(2, 0, 1), current = 0))
        composeRule.onNodeWithText("T2").assertIsSelected()
        composeRule.onNodeWithText("T0").assertIsNotSelected()
        composeRule.onNodeWithText("T1").assertIsNotSelected()
    }

    // D3-1. The sheet's dialog window is edge to edge, so the fully expanded sheet must stop below the status bar while
    // the scrim stays full screen and the surface still runs behind the navigation bar, with the last row clearing it.
    // Robolectric dispatches no system-bar insets, so the test hands the dialog's ComposeView a status-bar and a
    // navigation-bar inset itself, then expands the sheet through the drag handle's Expand action. Only the dialog gets
    // insets; the activity's stay zero, so the test also tells the sheet's window apart from the host's.
    // Mutation: drop statusBarsPadding() from the sheet modifier → the sheet's top is 0 → RED.
    // Mutation: windowInsetsPadding(WindowInsets.statusBars) instead (reads the activity's window) → top is 0 → RED.
    // Mutation: systemBarsPadding() instead → the sheet stops above the nav bar → RED.
    @Test
    fun fullyExpandedSheetStopsBelowTheStatusBar() {
        show(queue(60, current = 0))
        composeRule.runOnIdle { dispatchSystemBarInsets(top = STATUS_BAR_PX, bottom = NAV_BAR_PX) }
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand)
        composeRule.waitForIdle()

        val window = composeRule.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val scrim = composeRule.onNodeWithContentDescription("Close sheet").fetchSemanticsNode().boundsInRoot
        val sheet = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .fetchSemanticsNode().boundsInRoot
        assertEquals("scrim is full screen", window, scrim)
        assertEquals("sheet top sits just under the status bar", STATUS_BAR_PX.toFloat(), sheet.top, PX_TOLERANCE)
        assertEquals("sheet runs behind the nav bar", window.bottom, sheet.bottom, PX_TOLERANCE)

        composeRule.onNodeWithTag(QUEUE_LIST_TAG).performScrollToIndex(59)
        val lastRow = composeRule.onNodeWithText("T59").fetchSemanticsNode().boundsInRoot
        assertTrue("last row clears the nav bar", lastRow.bottom <= window.bottom - NAV_BAR_PX + PX_TOLERANCE)
    }

    // Ruling 2: scroll by list position. Reversed window indices: position 45 is "T14"; window index 45 ("T45") sits at
    // position 14, 31 rows away, so it is never composed. Node counts, not isDisplayed: Compose 1.7 reports an absent
    // node as "not displayed" too.
    // Mutation: scrollToItem(queue.items[queue.currentIndex].index) → lands on position 14 → "T14" absent → RED.
    @Test
    fun shuffledQueueOpensScrolledToTheCurrentListPosition() {
        show(shuffledQueue(windowIndices = (59 downTo 0).toList(), current = 45))
        composeRule.onAllNodesWithText("T14").assertCountEquals(1)
        composeRule.onNodeWithText("T14").assertIsDisplayed().assertIsSelected()
        composeRule.onAllNodesWithText("T45").assertCountEquals(0)
    }

    /** Hands the sheet dialog's ComposeView system-bar insets, as the platform would on an edge-to-edge device. */
    private fun dispatchSystemBarInsets(top: Int, bottom: Int) {
        val dialogRoot = ShadowDialog.getLatestDialog().window!!.decorView
        val composeView = requireNotNull(dialogRoot.findComposeView()) { "no ComposeView in the sheet dialog" }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
            .build()
        ViewCompat.dispatchApplyWindowInsets(composeView, insets)
    }

    private fun View.findComposeView(): AbstractComposeView? = when (this) {
        is AbstractComposeView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findComposeView() }
        else -> null
    }

    private companion object {
        const val STATUS_BAR_PX = 80
        const val NAV_BAR_PX = 120
        const val PX_TOLERANCE = 1f
    }
}
