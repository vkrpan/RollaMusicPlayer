package com.rolla.musicplayer.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private val TITLES = listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders")

// A partial swipe step: three of them stay well inside one page of the 320dp-wide pager.
private const val SWIPE_STEP_PX = 30f

// Long enough for a pressed ripple to finish spreading and hold.
private const val RIPPLE_SETTLE_MS = 1_000L

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiTabRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentPageIsSelectedAndTappingANeighbourReportsItsIndex() {
        var clicked = -1
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiTabRow(
                    titles = TITLES,
                    pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                    onTabClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Tracks").assertIsSelected()
        composeRule.onNodeWithText("Albums").assertIsNotSelected().performClick()
        assertEquals(3, clicked)
    }

    @Test
    fun disabledRowReportsTabsDisabledAndIgnoresTaps() {
        var clicked = -1
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiTabRow(
                    titles = TITLES,
                    pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                    onTabClick = { clicked = it },
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithText("Albums").assertIsNotEnabled()
        // Disabled locks paging; it must not drop the current tab's selected state.
        composeRule.onNodeWithText("Tracks").assertIsNotEnabled().assertIsSelected()
        composeRule.onNodeWithText("Albums").performTouchInput { click() }
        assertEquals("a disabled tab must not report a click", -1, clicked)
    }

    @Test
    fun largeFontScaleDoesNotClipLabelsVertically() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    OneUiTabRow(
                        titles = TITLES,
                        pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                        onTabClick = {},
                    )
                }
            }
        }
        val row = tabRow().getUnclippedBoundsInRoot()
        val rowHeight = row.bottom - row.top
        // The label box is the laid-out text plus its vertical padding. Its semantics bounds are the full-height touch
        // slot, so the height comes from the text layout; the row centers the label vertically.
        val labelHeight = textLayout("Tracks").size.height.pxToDp() + RollaDimens.tabLabelVerticalPadding * 2
        // Precondition: at 2x the label outgrows the minimum row height, so the check below is not vacuous.
        assertTrue("label $labelHeight fits ${RollaDimens.tabRowHeight}", labelHeight > RollaDimens.tabRowHeight)
        assertTrue("label $labelHeight taller than row $rowHeight", labelHeight <= rowHeight)
    }

    @Test
    fun rtlMirrorsTabOrder() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    OneUiTabRow(
                        titles = TITLES,
                        pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                        onTabClick = {},
                    )
                }
            }
        }
        val tracks = composeRule.onNodeWithText("Tracks").getUnclippedBoundsInRoot()
        val albums = composeRule.onNodeWithText("Albums").getUnclippedBoundsInRoot()
        // Centers, not edges: a scaled tab reports its unscaled size from a scaled origin, so its edges overshoot.
        val tracksCenter = (tracks.left + tracks.right) / 2
        val albumsCenter = (albums.left + albums.right) / 2
        assertTrue("in RTL the next tab sits to the left", albumsCenter < tracksCenter)
    }

    @Test
    fun tappingAboveANeighbourLabelSelectsIt() {
        var clicked = -1
        setTabRow(LayoutDirection.Ltr) { clicked = it }
        tapNearRowTopAtCenterOf("Albums")
        assertEquals(3, clicked)
    }

    @Test
    fun tappingAboveANeighbourLabelSelectsItInRtl() {
        var clicked = -1
        setTabRow(LayoutDirection.Rtl) { clicked = it }
        tapNearRowTopAtCenterOf("Albums")
        assertEquals(3, clicked)
    }

    @Test
    fun tappingTheGapBetweenTabsSelectsANeighbour() {
        var clicked = -1
        setTabRow(LayoutDirection.Ltr) { clicked = it }
        val row = tabRow().getUnclippedBoundsInRoot()
        // Tracks is selected (scale 1): its visual right edge is its center plus half its text width, and the visual
        // gap from there to Albums is tabSpacing wide.
        val tracks = composeRule.onNodeWithText("Tracks").getUnclippedBoundsInRoot()
        val tracksVisualRight = (tracks.left + tracks.right) / 2 + textLayout("Tracks").size.width.pxToDp() / 2
        val gapMiddle = tracksVisualRight + RollaDimens.tabSpacing / 2 - row.left
        tabRow().performTouchInput { click(Offset(gapMiddle.toPx(), height / 2f)) }
        assertTrue("a tap in the gap resolved to $clicked", clicked == 2 || clicked == 3)
    }

    @Test
    fun swipingThePagerDoesNotRecomposeLabels() {
        lateinit var pagerState: PagerState
        val counter = RecompositionCounter()
        composeRule.setContent {
            ObserveRecompositions(counter)
            RollaMusicPlayerTheme(darkTheme = true) {
                pagerState = rememberPagerState(initialPage = 2) { TITLES.size }
                Column {
                    OneUiTabRow(titles = TITLES, pagerState = pagerState, onTabClick = {})
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue("composition observer attached", counter.isAttached)
        val before = counter.recompositions
        repeat(3) {
            composeRule.runOnIdle { pagerState.dispatchRawDelta(SWIPE_STEP_PX) }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertEquals("the swipe stays on one page", 2, pagerState.currentPage)
            assertTrue("the swipe moved the pager", pagerState.currentPageOffsetFraction > 0f)
        }
        assertEquals("recompositions during a partial swipe", before, counter.recompositions)
    }

    // Kills: a CircleShape clip that wraps the slot's selectable (`clip(CircleShape).selectable(...)`). The clip layer
    // limits hit testing to its outline, and a slot-high pill loses only the corners, which the taps above never reach.
    @Test
    fun tappingEachCornerOfASlotSelectsIt() {
        var clicked = -1
        setTabRow(LayoutDirection.Ltr) { clicked = it }
        val row = tabRow().getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithText("Albums").getUnclippedBoundsInRoot()
        val inset = 2.dp
        val corners = listOf(
            slot.left + inset to slot.top + inset,
            slot.right - inset to slot.top + inset,
            slot.left + inset to slot.bottom - inset,
            slot.right - inset to slot.bottom - inset,
        )
        for ((x, y) in corners) {
            clicked = -1
            tabRow().performTouchInput { click(Offset((x - row.left).toPx(), (y - row.top).toPx())) }
            assertEquals("tap at ($x, $y) in the Albums slot", 3, clicked)
        }
    }

    // Kills: a full-height pill (pill height = slot height) and a label-width pill (pill width = label width).
    @Test
    fun pressPillSpansTheSlotAtTheLabelHeightCenteredInTheRow() {
        setTabRow(LayoutDirection.Ltr) {}
        val row = tabRow().getUnclippedBoundsInRoot()
        val labelHeight = textLayout("Tracks").size.height.pxToDp() + RollaDimens.tabLabelVerticalPadding * 2
        // Precondition: the label is shorter than the row, so a full-height pill would show.
        assertTrue("label $labelHeight fills the row", labelHeight < row.bottom - row.top - 2.dp)
        // The current tab and a scaled neighbour: the pill follows each slot, scaled or not.
        for (title in listOf("Tracks", "Albums")) {
            val slot = composeRule.onNodeWithText(title).getUnclippedBoundsInRoot()
            val pill = pill(title).getUnclippedBoundsInRoot()
            assertDpEquals("$title pill width", slot.right - slot.left, pill.right - pill.left)
            assertDpEquals("$title pill height", labelHeight, pill.bottom - pill.top)
            assertDpEquals("$title pill vertical center", (row.top + row.bottom) / 2, (pill.top + pill.bottom) / 2)
        }
    }

    // Kills: dropping the pill's clip (the ripple fills the pill's rectangular box), and the old slot-wide ripple
    // (the theme's default indication on the slot).
    // SDK 30: API 31+'s patterned RippleDrawable draws nothing in a Robolectric software draw, so on SDK 34 a press
    // leaves every pixel unchanged and this test would pass vacuously.
    @Test
    @Config(sdk = [30])
    fun pressRippleIsClippedToThePill() {
        composeRule.mainClock.autoAdvance = false
        val row = setTabRowOnBackground()
        val probes = rippleProbes("Tracks")
        assertPixel("pill interior, before the press", row.background, drawToBitmap(row.view), probes.inside)
        composeRule.onNodeWithText("Tracks").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(RIPPLE_SETTLE_MS)
        val pressed = drawToBitmap(row.view)
        assertTrue("the ripple lights the pill interior", pressed[probes.inside] != row.background)
        assertPixel("slot corner", row.background, pressed, probes.slotCorner)
        assertPixel("pill box corner, outside the rounded end", row.background, pressed, probes.pillBoxCorner)
    }

    // Kills: a tab that stays pressable while the row is disabled (selectable's enabled forced to true), and, through
    // its own positive control, any break in ripple rendering (e.g. a transparent ripple color), which would otherwise
    // let "no ripple" pass vacuously.
    // SDK 30 for the same reason as above: on SDK 34 no ripple ever renders.
    @Test
    @Config(sdk = [30])
    fun disabledRowShowsNoRipple() {
        composeRule.mainClock.autoAdvance = false
        val row = setTabRowOnBackground()
        val probes = rippleProbes("Tracks")
        val tracks = composeRule.onNodeWithText("Tracks")
        // Positive control: while the row is enabled, the same press lights the same probe.
        tracks.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(RIPPLE_SETTLE_MS)
        assertTrue("control: an enabled press lights the pill", drawToBitmap(row.view)[probes.inside] != row.background)
        tracks.performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(RIPPLE_SETTLE_MS)
        assertPixel("control: the ripple faded after release", row.background, drawToBitmap(row.view), probes.inside)

        row.enabled.value = false
        // Without an explicit apply, the paused clock never recomposed the row. A row left enabled would light the
        // pill and fail below, so no semantics precondition is needed (and none masks the pixel check).
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(RIPPLE_SETTLE_MS)
        tracks.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(RIPPLE_SETTLE_MS)
        assertPixel("pill interior of a disabled tab", row.background, drawToBitmap(row.view), probes.inside)
    }

    /**
     * Sets an enabled row on the dark theme's background and returns its hosting view, the background as ARGB (read
     * from the theme, so the pixel checks follow the token) and a switch for the row's enabled state.
     */
    private fun setTabRowOnBackground(): PixelRow {
        lateinit var view: View
        var background = 0
        val enabled = mutableStateOf(true)
        composeRule.setContent {
            view = LocalView.current
            RollaMusicPlayerTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background.toArgb()
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    OneUiTabRow(
                        titles = TITLES,
                        pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                        onTabClick = {},
                        enabled = enabled.value,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return PixelRow(view = view, background = background, enabled = enabled)
    }

    /**
     * Draws [view] into a software bitmap. captureToImage() times out under Robolectric (it waits for a frame commit
     * that never comes), but a direct software draw runs Compose's draw phase under NATIVE graphics.
     */
    private fun drawToBitmap(view: View): Bitmap {
        composeRule.waitForIdle()
        lateinit var bitmap: Bitmap
        composeRule.runOnUiThread {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun assertPixel(message: String, expected: Int, bitmap: Bitmap, at: IntOffset) =
        assertEquals("$message at $at", Integer.toHexString(expected), Integer.toHexString(bitmap[at]))

    /** Pixel probes (root px) around [title]'s pill, away from its glyphs. */
    private fun rippleProbes(title: String): RippleProbes {
        val slot = composeRule.onNodeWithText(title).getUnclippedBoundsInRoot()
        val pill = pill(title).getUnclippedBoundsInRoot()
        val inset = 2.dp
        fun at(x: Dp, y: Dp) = with(composeRule.density) { IntOffset(x.roundToPx(), y.roundToPx()) }
        return RippleProbes(
            // Half the side padding in from the slot start, on the pill's center line: inside the rounded end.
            inside = at(slot.left + RollaDimens.tabSpacing / 4, (pill.top + pill.bottom) / 2),
            slotCorner = at(slot.left + inset, slot.top + inset),
            pillBoxCorner = at(pill.left + inset, pill.top + inset),
        )
    }

    private fun pill(title: String): SemanticsNodeInteraction = composeRule.onNode(
        hasTestTag(TAB_PILL_TEST_TAG) and hasAnySibling(hasText(title)),
        useUnmergedTree = true,
    )

    private fun assertDpEquals(message: String, expected: Dp, actual: Dp) =
        assertTrue("$message: expected $expected, was $actual", abs((expected - actual).value) <= 1f)

    private fun setTabRow(layoutDirection: LayoutDirection, onTabClick: (Int) -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    OneUiTabRow(
                        titles = TITLES,
                        pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                        onTabClick = onTabClick,
                    )
                }
            }
        }
    }

    private fun tabRow(): SemanticsNodeInteraction =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))

    private fun textLayout(title: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun Int.pxToDp(): Dp = with(composeRule.density) { toDp() }

    /** Taps 2dp inside the row's top edge, at the horizontal center of [title]'s tab. */
    private fun tapNearRowTopAtCenterOf(title: String) {
        val row = tabRow().getUnclippedBoundsInRoot()
        val tab = composeRule.onNodeWithText(title).getUnclippedBoundsInRoot()
        val x = (tab.left + tab.right) / 2 - row.left
        tabRow().performTouchInput { click(Offset(x.toPx(), 2.dp.toPx())) }
    }
}

private class RippleProbes(val inside: IntOffset, val slotCorner: IntOffset, val pillBoxCorner: IntOffset)

/** A row under a pixel test: its hosting view, the theme background as ARGB, and its enabled switch. */
private class PixelRow(val view: View, val background: Int, val enabled: MutableState<Boolean>)

private operator fun Bitmap.get(offset: IntOffset): Int = getPixel(offset.x, offset.y)

/** Counts recompositions (not initial compositions) of the composition it observes, including its subcompositions. */
@OptIn(ExperimentalComposeRuntimeApi::class)
private class RecompositionCounter : CompositionObserver {
    var isAttached = false
    var recompositions = 0
        private set

    override fun onBeginComposition(composition: Composition, invalidationMap: Map<RecomposeScope, Set<Any>?>) {
        // An empty map is an initial composition (a pager page coming into view), not a recomposition.
        if (invalidationMap.isNotEmpty()) recompositions++
    }

    override fun onEndComposition(composition: Composition) = Unit
}

@OptIn(ExperimentalComposeRuntimeApi::class)
@Composable
private fun ObserveRecompositions(counter: RecompositionCounter) {
    val composition = currentComposer.composition
    DisposableEffect(composition) {
        val handle = composition.observe(counter)
        counter.isAttached = handle != null
        onDispose { handle?.dispose() }
    }
}
