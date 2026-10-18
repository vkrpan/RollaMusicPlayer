package com.rolla.musicplayer.core.designsystem.component

import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.fastScrollIndex
import com.rolla.musicplayer.core.designsystem.theme.fastScrollLetter
import com.rolla.musicplayer.core.designsystem.theme.fastScrollTrack
import com.rolla.musicplayer.core.testing.drawRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FastScrollRailTest {

    // An activity rule (what createComposeRule() builds internally), so the render test can use drawRoot().
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val haptics = object : HapticFeedback {
        var ticks = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            if (hapticFeedbackType == HapticFeedbackType.TextHandleMove) ticks++
        }
    }

    // 50 rows: "Alpha 0".."Alpha 24" (index 0–24), "Mango 0".."Mango 24" (25–49). Only A and M are populated.
    private val names = List(25) { "Alpha $it" } + List(25) { "Mango $it" }
    private val sections = buildFastScrollSections(names)

    private fun rail() = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG)

    private fun jumpActions() = rail().fetchSemanticsNode().config[SemanticsActions.CustomActions]

    private fun jumpAction(label: String) = jumpActions().single { it.label == "Jump to $label" }

    private fun setRail(
        height: Dp = 432.dp,
        sections: () -> List<FastScrollSection> = { this.sections },
        onSelected: (FastScrollSection) -> Unit,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    FastScrollRail(
                        sections = sections(),
                        onSectionSelected = onSelected,
                        modifier = Modifier.height(height),
                    )
                }
            }
        }
    }

    // Spec §10: one node with actions only for populated sections. Mutation: build actions for every section →
    // "Jump to B" exists → RED. Control: the node and "Jump to A"/"Jump to M" exist.
    @Test
    fun railIsOneNodeWithAJumpActionPerPopulatedSection() {
        setRail {}
        rail().assertContentDescriptionEquals("Fast scroll, letter index")
        val labels = jumpActions().map { it.label }
        assertEquals(listOf("Jump to A", "Jump to M"), labels)
    }

    // Mutation: the action calls onSectionSelected with sections.first() → "#" → RED.
    @Test
    fun jumpActionSelectsItsSection() {
        val selected = mutableListOf<String>()
        setRail { selected += it.label }
        composeRule.runOnIdle { jumpAction("M").action() }
        assertEquals(listOf("M"), selected)
    }

    // Slot 20 of 27 is "T", which is empty and targets M's row (index 25). Mutation: map the fraction over 26 slots →
    // 19 ("S") → RED. Control: one haptic tick.
    @Test
    fun tapSelectsTheLabelUnderTheFingerAndTicksOnce() {
        val selected = mutableListOf<FastScrollSection>()
        setRail { selected += it }
        rail().performTouchInput { click(Offset(centerX, height * 20.5f / 27f)) }
        assertEquals(listOf(FastScrollSection("T", 25, populated = false)), selected)
        assertEquals(1, haptics.ticks)
    }

    // Fix round 1, I3. Each tap is a new gesture, so a second tap on the same letter selects it again; otherwise it
    // would be a dead control. Mutation: delete the tap path's `tracker.lastLabel = null` → the second tap matches the
    // last label and does nothing → RED. Control: the first tap selects T.
    @Test
    fun tappingTheSameLetterTwiceSelectsItTwice() {
        val selected = mutableListOf<String>()
        setRail { selected += it.label }
        rail().performTouchInput { click(Offset(centerX, height * 20.5f / 27f)) }
        assertEquals("control: the first tap selects T", listOf("T"), selected)
        rail().performTouchInput { click(Offset(centerX, height * 20.5f / 27f)) }
        assertEquals(listOf("T", "T"), selected)
        assertEquals(2, haptics.ticks)
    }

    // Final fix wave I2. A drag is a new gesture too, so a drag that starts on the letter the previous gesture ended on
    // selects it again and ticks; otherwise it would neither jump nor tick, a dead control. The swipe starts at T's
    // slot centre and moves the touch slop plus 4 dp down, so it is a drag (a move under the slop would end as a tap,
    // which resets on its own path), yet it stays inside T's slot. Robolectric's slop is 16 dp, so the slot must be
    // taller than 2 × 20 dp: a 1 620 dp rail (on a 1 700 dp screen) has 60 dp slots. Mutation: delete the drag path's
    // `tracker.lastLabel = null` → the drag matches the tap's T and does nothing → [T] → RED. Controls: the first tap
    // selects T; the swipe passes the slop and stays inside the slot.
    @Config(qualifiers = "w360dp-h1700dp")
    @Test
    fun aDragStartingOnTheLastSelectedLetterSelectsItAgain() {
        val selected = mutableListOf<String>()
        setRail(height = 1_620.dp) { selected += it.label }
        val touchSlop = ViewConfiguration.get(composeRule.activity).scaledTouchSlop.toFloat()
        val slotCentre = 20.5f / 27f
        rail().performTouchInput { click(Offset(centerX, height * slotCentre)) }
        assertEquals("control: the first tap selects T", listOf("T"), selected)
        rail().performTouchInput {
            val distance = touchSlop + 4.dp.toPx()
            assertTrue("control: $distance px stays inside T's slot", distance < height / sections.size / 2f)
            val start = Offset(centerX, height * slotCentre)
            swipe(start, start + Offset(0f, distance), durationMillis = 300)
        }
        assertEquals(listOf("T", "T"), selected)
        assertEquals(2, haptics.ticks)
    }

    // Fix round 1, I1. At fontScale 2 the letter line (fastScrollLetter's 13 sp line height, doubled) is taller than a
    // 200 dp rail's slot (200 dp / 27 ≈ 7.4 dp). letterSlot = max(fastScrollLetterSlot, line height) = 26 dp, so the
    // rail fits 200 / 26 → 7 slots, and fastScrollLabelStep(7) = 4 shows "#", D, H, L, P, T and X. Label 2 × step = 8
    // ("H") is shown and neither first nor last. Its ink must be taller than its slot. The weighted slots are each the
    // floor or the ceiling of railHeight / 27 px, and a clipped label inks no more than its slot, so the bound is that
    // ceiling. Mutation: drop wrapContentHeight(unbounded = true) → the Text is measured to its slot and clipped → RED.
    // Control: H has ink at all.
    @Config(sdk = [30], qualifiers = "+xxhdpi") // view.draw renders reliably at SDK 30 (OneUiTabRowTest precedent)
    @Test
    fun aShownLabelIsNotClippedWhenItsSlotIsShorterThanItsLine() {
        var track = Color.Unspecified
        var ink = Color.Unspecified
        var letterSlot = 0.dp
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                track = MaterialTheme.colorScheme.fastScrollTrack
                ink = MaterialTheme.colorScheme.fastScrollIndex
                val largeFont = Density(LocalDensity.current.density, fontScale = 2f)
                CompositionLocalProvider(LocalDensity provides largeFont) {
                    val lineHeight = with(largeFont) { MaterialTheme.typography.fastScrollLetter.lineHeight.toDp() }
                    letterSlot = maxOf(RollaDimens.fastScrollLetterSlot, lineHeight)
                    FastScrollRail(sections = sections, onSectionSelected = {}, modifier = Modifier.height(200.dp))
                }
            }
        }
        val step = fastScrollLabelStep(slots = (200.dp / letterSlot).toInt(), labelCount = sections.size)
        val index = 2 * step
        assertEquals("the derivation picks H", "H", sections[index].label)

        val slotPx = rail().fetchSemanticsNode().boundsInRoot.height / sections.size
        val inkRows = inkRowsAroundLabel(index, ink, track)
        assertTrue("control: H has ink", inkRows.isNotEmpty())
        val extent = inkRows.last() - inkRows.first() + 1
        val maxSlot = ceil(slotPx).toInt()
        assertTrue("H's ink spans $extent px, no more than one slot ($maxSlot px): clipped", extent > maxSlot)
    }

    /**
     * The bitmap rows holding [ink] pixels in the rail's letter column, within two slots either side of label [index]'s
     * slot centre: wide enough for the label's natural ink, narrow enough to exclude neighbours four slots away.
     */
    private fun inkRowsAroundLabel(index: Int, ink: Color, track: Color): List<Int> {
        val bitmap = composeRule.drawRoot()
        val bounds = rail().fetchSemanticsNode().boundsInRoot
        val slotPx = bounds.height / sections.size
        val centre = bounds.top + (index + 0.5f) * slotPx
        val columnRight = bounds.left + with(composeRule.density) { RollaDimens.fastScrollWidth.toPx() }
        return ((centre - 2 * slotPx).toInt()..(centre + 2 * slotPx).toInt()).filter { y ->
            (bounds.left.toInt() until columnRight.toInt()).any { x -> isInk(bitmap.getPixel(x, y), ink, track) }
        }
    }

    /** An ink pixel is nearer the letter colour than the track colour (anti-aliased edges fall either side). */
    private fun isInk(pixel: Int, ink: Color, track: Color): Boolean {
        fun distance(color: Color): Int {
            val argb = color.toArgb()
            return listOf(16, 8, 0).sumOf { shift -> abs((argb shr shift and 0xFF) - (pixel shr shift and 0xFF)) }
        }
        return distance(ink) < distance(track)
    }

    // Mutations: (a) select on every move without the label check → repeats → RED; (b) tick on every move → ticks >
    // selections → RED. Control: the drag ends on Z.
    @Test
    fun dragSelectsEachLabelOnceAndTicksPerChange() {
        val selected = mutableListOf<String>()
        setRail { selected += it.label }
        rail().performTouchInput {
            swipe(Offset(centerX, 1f), Offset(centerX, height - 1f), durationMillis = 1_000)
        }
        assertEquals("Z", selected.last())
        assertTrue("consecutive repeats in $selected", selected.zipWithNext().none { (a, b) -> a == b })
        assertEquals(selected.size, haptics.ticks)
    }

    // Review Focus 4. A 200 dp rail fits 12 slots, so it shows every 3rd label, but a drag still reaches Z and TalkBack
    // still lists every populated section. Mutation: map the fraction over the visible labels only → never Z → RED.
    @Test
    fun aShortRailStillReachesEveryLetter() {
        val selected = mutableListOf<String>()
        setRail(height = 200.dp) { selected += it.label }
        rail().performTouchInput { swipe(Offset(centerX, 1f), Offset(centerX, height - 1f), durationMillis = 1_000) }
        assertEquals("Z", selected.last())
        assertEquals(listOf("Jump to A", "Jump to M"), jumpActions().map { it.label })
    }

    // Ruling P3: a state flip. Control: with sections the rail exists, which catches "never render". Mutation: drop the
    // early return (render the rail with no sections) → the rail still exists after the flip → RED.
    @Test
    fun noSectionsRendersNothing() {
        var shown by mutableStateOf(sections)
        setRail(sections = { shown }) {}
        rail().assertExists()
        composeRule.runOnIdle { shown = emptyList() }
        rail().assertDoesNotExist()
    }

    // ── FastScrollContainer, over a real LazyColumn and LazyListState (the framework entry point) ──

    private lateinit var listState: LazyListState

    private fun setList(
        rows: List<String>,
        itemOffset: Int = 0,
        railBottomPadding: Dp = 0.dp,
        railTopPadding: Dp = 0.dp,
        containerHeight: Dp? = null,
    ) = setList({ rows }, itemOffset, railBottomPadding, railTopPadding, containerHeight)

    // containerHeight: null fills the 640 dp screen; a value gives the container that height (a landscape list).
    private fun setList(
        rowsProvider: () -> List<String>,
        itemOffset: Int = 0,
        railBottomPadding: Dp = 0.dp,
        railTopPadding: Dp = 0.dp,
        containerHeight: Dp? = null,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val rows = rowsProvider()
                listState = rememberLazyListState()
                FastScrollContainer(
                    listState = listState,
                    sections = buildFastScrollSections(rows),
                    itemOffset = itemOffset,
                    railBottomPadding = railBottomPadding,
                    railTopPadding = railTopPadding,
                    modifier = if (containerHeight != null) {
                        Modifier.fillMaxWidth().height(containerHeight).testTag("container")
                    } else {
                        Modifier.fillMaxSize().testTag("container")
                    },
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (itemOffset == 1) item(key = "header") { Box(Modifier.fillMaxWidth().height(143.dp)) }
                        items(rows, key = { it }) { Text(it, Modifier.fillMaxWidth().height(70.dp)) }
                    }
                }
            }
        }
    }

    // Spec §10: hidden when the list fits. Five 70 dp rows fit in 640 dp. Mutation: drop the canScroll condition → the
    // rail shows → RED. Control: the rows render. Ruling P3: then the list grows to 50 rows and the rail must appear;
    // mutation "never show the rail" → RED at that final control.
    @Test
    fun railIsHiddenWhenTheListFits() {
        var rows by mutableStateOf(List(5) { "Alpha $it" })
        setList(rowsProvider = { rows })
        composeRule.onNodeWithText("Alpha 4").assertExists()
        rail().assertDoesNotExist()
        rows = names
        composeRule.waitForIdle()
        rail().assertExists()
    }

    // The jump goes through the real LazyListState, offset past a header item (the Playlists feature cards). Mutation:
    // drop itemOffset from scrollToItem → firstVisibleItemIndex 25 → RED. Control: the rail exists.
    @Test
    fun aJumpScrollsTheListPastItsHeader() {
        setList(names, itemOffset = 1)
        rail().assertExists()
        composeRule.runOnIdle { jumpAction("M").action() }
        composeRule.runOnIdle { assertEquals(26, listState.firstVisibleItemIndex) }
    }

    // Ruling 8. The rail stops railBottomPadding above the container's bottom, at the pill. Mutation: drop the bottom
    // padding → the rail's bottom equals the container's → RED.
    @Test
    fun railEndsAboveTheBottomPadding() {
        setList(names, railBottomPadding = 60.dp)
        val container = composeRule.onNodeWithTag("container").getUnclippedBoundsInRoot()
        val railBounds = rail().getUnclippedBoundsInRoot()
        assertEquals((container.bottom - 60.dp).value, railBounds.bottom.value, 0.5f)
        assertEquals(container.top.value, railBounds.top.value, 0.5f)
    }

    // Task 5 fix round 1, I1. The rail starts railTopPadding below the container's top, past a header item it must not
    // cover (Playlists' feature cards; 143 dp is this file's header height). Mutation: ignore railTopPadding → the
    // rail's top equals the container's → RED. Control: the bottom still honours railBottomPadding.
    @Test
    fun railStartsBelowTheTopPadding() {
        setList(names, itemOffset = 1, railBottomPadding = 60.dp, railTopPadding = 143.dp)
        val container = composeRule.onNodeWithTag("container").getUnclippedBoundsInRoot()
        val railBounds = rail().getUnclippedBoundsInRoot()
        assertEquals((container.top + 143.dp).value, railBounds.top.value, 0.5f)
        assertEquals((container.bottom - 60.dp).value, railBounds.bottom.value, 0.5f)
    }

    // Task 5 fix round 2, R1 (Review Focus 4). A top inset never squeezes the rail below fastScrollMinRailHeight: a
    // 300 dp container with a 250 dp inset would leave 50 dp, so the inset is capped at 300 − 96 = 204 dp. Mutation:
    // use railTopPadding uncapped → the rail is 50 dp tall, its top at 250 dp → RED. Control: the rail's bottom is the
    // container's (railBottomPadding = 0).
    @Test
    fun aTopInsetNeverSqueezesTheRailBelowItsMinimumHeight() {
        setList(names, itemOffset = 1, railTopPadding = 250.dp, containerHeight = 300.dp)
        val container = composeRule.onNodeWithTag("container").getUnclippedBoundsInRoot()
        val railBounds = rail().getUnclippedBoundsInRoot()
        val railHeight = railBounds.bottom - railBounds.top
        assertEquals(300f, (container.bottom - container.top).value, 0.5f)
        assertTrue(
            "The rail must stay at least 96 dp tall, was $railHeight",
            railHeight.value >= RollaDimens.fastScrollMinRailHeight.value - 0.5f,
        )
        assertEquals((container.top + 204.dp).value, railBounds.top.value, 0.5f)
        assertEquals(container.bottom.value, railBounds.bottom.value, 0.5f)
    }

    // Final fix wave I1: the crash guard. A 150 dp container with a 100 dp bottom padding leaves 50 dp, less than
    // fastScrollMinRailHeight (landscape with the pill showing), so the top-inset cap would be 150 − 100 − 96 = −46 dp,
    // and a negative padding throws. The cap's floor of 0 dp keeps the rail at the container's top. Mutation: drop
    // `.coerceAtLeast(0.dp)` from railRoom → Modifier.padding throws IllegalArgumentException ("Padding must be
    // non-negative") → RED. Here the exception is the defect itself (the live crash), not a side effect of the
    // mutation. Control: the rail exists, and its bottom honours railBottomPadding.
    @Test
    fun aContainerShorterThanTheMinimumRailDoesNotCrash() {
        setList(names, railBottomPadding = 100.dp, railTopPadding = 0.dp, containerHeight = 150.dp)
        rail().assertExists()
        val container = composeRule.onNodeWithTag("container").getUnclippedBoundsInRoot()
        val railBounds = rail().getUnclippedBoundsInRoot()
        assertEquals(150f, (container.bottom - container.top).value, 0.5f)
        assertEquals(container.top.value, railBounds.top.value, 0.5f)
        assertEquals((container.bottom - 100.dp).value, railBounds.bottom.value, 0.5f)
    }

    // Spec §10: TalkBack reaches the rail after the list. Mutation: drop traversalIndex → the property is absent → RED.
    // Fix round 1, I2: the index only ranks inside the container's own traversal group (otherwise it ranks in Home's
    // outer group). Mutation: drop the container's isTraversalGroup → null → RED.
    @Test
    fun railIsTraversedAfterTheList() {
        setList(names)
        assertEquals(1f, rail().fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
        val container = composeRule.onNodeWithTag("container").fetchSemanticsNode().config
        assertEquals(true, container.getOrNull(SemanticsProperties.IsTraversalGroup))
    }

    // Review Focus 2 / spec §18. A horizontal swipe that starts on the rail pages the pager and jumps nothing.
    // Mutation: one awaitEachGesture handler that selects on down and consumes every move → the pager stays on 0 (and
    // selected is non-empty) → RED. Control: the rail is on screen before the swipe; that vertical gestures on it do
    // select is pinned by dragSelectsEachLabelOnceAndTicksPerChange.
    @Test
    fun aHorizontalSwipeOnTheRailPagesInsteadOfJumping() {
        val selected = mutableListOf<String>()
        lateinit var pagerState: PagerState
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                pagerState = rememberPagerState { 2 }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        FastScrollRail(
                            sections = sections,
                            onSectionSelected = { selected += it.label },
                            modifier = Modifier.height(432.dp),
                        )
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        rail().assertExists()
        rail().performTouchInput {
            swipe(Offset(centerX, centerY), Offset(centerX - 600f, centerY), durationMillis = 300)
        }
        composeRule.runOnIdle { assertEquals(1, pagerState.currentPage) }
        assertTrue("swipe selected $selected", selected.isEmpty())
    }
}
