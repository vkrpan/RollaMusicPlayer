package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

private val TITLES = listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders")

// A partial swipe step: three of them stay well inside one page of the 320dp-wide pager.
private const val SWIPE_STEP_PX = 30f

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
