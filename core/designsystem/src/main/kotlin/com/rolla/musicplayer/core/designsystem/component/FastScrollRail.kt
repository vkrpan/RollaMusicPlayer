package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.fastScrollIndex
import com.rolla.musicplayer.core.designsystem.theme.fastScrollLetter
import com.rolla.musicplayer.core.designsystem.theme.fastScrollTrack
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** UI test and baseline-profile handle for the A–Z rail. */
const val FAST_SCROLL_RAIL_TEST_TAG = "fast_scroll_rail"

private const val FAST_SCROLL_RAIL_DESCRIPTION = "Fast scroll, letter index"

/**
 * The One UI A–Z rail (spec §10): a [fastScrollTrack] pill with "#" and A–Z in equal slots, pinned to the end of a
 * list. A tap or a vertical drag calls [onSectionSelected] once each time the label under the finger changes, with a
 * `TextHandleMove` tick; a drag selects the label under its down point first. When the rail is too short for 27
 * slots it shows every k-th label (spec §10, ruling 10), and a drag still reaches the dropped ones; the first and last
 * shown labels are inset to stay inside the pill. For TalkBack it is one node with a "Jump to X" action per populated
 * section, traversed after the list. It renders nothing for no [sections]. The touch width is
 * `fastScrollWidth + fastScrollEnd` (30 dp), the documented exception to the 48 dp rule (spec §5.4).
 */
// One gesture-and-semantics modifier chain over a flat label column; splitting it would only add indirection.
@Suppress("LongMethod")
@Composable
fun FastScrollRail(
    sections: List<FastScrollSection>,
    onSectionSelected: (FastScrollSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val currentSections by rememberUpdatedState(sections)
    val currentOnSectionSelected by rememberUpdatedState(onSectionSelected)
    val tracker = remember { LabelTracker() }
    fun select(y: Float, height: Int) {
        if (height <= 0) return
        val section = fastScrollSectionAt(currentSections, y / height)
        if (section.label == tracker.lastLabel) return
        tracker.lastLabel = section.label
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSectionSelected(section)
    }
    val letterStyle = MaterialTheme.typography.fastScrollLetter
    val letterSlot = with(LocalDensity.current) {
        maxOf(RollaDimens.fastScrollLetterSlot, letterStyle.lineHeight.toDp())
    }
    Box(
        modifier = modifier
            .width(RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd)
            // Ruling 11: separate detectors, so a horizontal swipe from the rail still reaches the Home pager.
            .pointerInput(haptics) {
                detectTapGestures(
                    onTap = { offset ->
                        tracker.lastLabel = null
                        select(offset.y, size.height)
                    },
                )
            }
            // The drag detector reports its start where the move crossed the touch slop, a slot or more below the
            // finger on a short rail. This records the down point first (Initial pass, nothing consumed), so a drag
            // selects the letter it started on.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    tracker.downY = down.position.y
                }
            }
            .pointerInput(haptics) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        tracker.lastLabel = null
                        select(tracker.downY ?: offset.y, size.height)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        select(change.position.y, size.height)
                    },
                )
            }
            .clearAndSetSemantics {
                testTag = FAST_SCROLL_RAIL_TEST_TAG
                contentDescription = FAST_SCROLL_RAIL_DESCRIPTION
                traversalIndex = 1f
                customActions = sections.filter { it.populated }.map { section ->
                    CustomAccessibilityAction("Jump to ${section.label}") {
                        currentOnSectionSelected(section)
                        true
                    }
                }
            },
    ) {
        val letterColor = MaterialTheme.colorScheme.fastScrollIndex
        Layout(
            content = {
                sections.forEach { section ->
                    Text(text = section.label, style = letterStyle, color = letterColor, maxLines = 1)
                }
            },
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = RollaDimens.fastScrollEnd)
                .width(RollaDimens.fastScrollWidth)
                .background(MaterialTheme.colorScheme.fastScrollTrack, CircleShape),
        ) { labels, constraints ->
            val height = constraints.maxHeight
            val step = fastScrollLabelStep((height / letterSlot.toPx()).toInt(), labels.size)
            val slot = height.toFloat() / labels.size
            // A slot can be shorter than the line (a short rail, a large font). A shown label keeps its natural
            // height, so it is never clipped, and is centred on its own slot. Shown labels sit at least letterSlot
            // apart, so they cannot overlap. Only the first and the last can reach past the pill's ends; they are
            // inset to stay inside it (spec §10, Phase 7). Dropped labels are neither measured nor placed.
            val shown = labels.withIndex().filter { (index, _) -> index % step == 0 }.map { (index, label) ->
                index to label.measure(Constraints(maxWidth = constraints.maxWidth))
            }
            layout(constraints.maxWidth, height) {
                shown.forEach { (index, label) ->
                    val centre = index * slot + slot / 2f
                    val top = (centre - label.height / 2f).roundToInt()
                        .coerceAtMost(height - label.height)
                        .coerceAtLeast(0)
                    label.place((constraints.maxWidth - label.width) / 2, top)
                }
            }
        }
    }
}

/**
 * Overlays a [FastScrollRail] on a list (spec §10, ruling 7). [content] is the `LazyColumn` driven by [listState]. The
 * rail shows only while the list can scroll, and only with sections. It runs from [railTopPadding] below the top of the
 * container (a fixed inset past a header item the rail must not cover, such as Playlists' feature-card row) to
 * [railBottomPadding] above its bottom (the floating pill on Home, ruling 8). The top inset is capped so the rail stays
 * at least [RollaDimens.fastScrollMinRailHeight] tall while the container leaves that much room above the bottom
 * padding; a short container (landscape) lets the rail overlap the header's end instead (Playlists hides its rail
 * there, Phase 7). A selection calls
 * `listState.scrollToItem(firstIndex + itemOffset)`, where [itemOffset] counts the list's items before the sectioned
 * rows (Playlists' feature cards). The container is a traversal group, so TalkBack visits the rail after the list.
 */
// The kit contract the five Home tabs share (ruling 7): list state, sections, header offset and the inset.
@Suppress("LongParameterList")
@Composable
fun FastScrollContainer(
    listState: LazyListState,
    sections: List<FastScrollSection>,
    modifier: Modifier = Modifier,
    itemOffset: Int = 0,
    railBottomPadding: Dp = homeListBottomPadding(),
    railTopPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollable by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    // Remembered, so the rail's modifier stays equal across recompositions and FastScrollRail can skip.
    val railInsets = remember(railTopPadding, railBottomPadding) {
        Modifier.railInsets(top = railTopPadding, bottom = railBottomPadding)
    }
    // A plain Box: the list composes in the caller's composition, not in a subcomposition (Phase 5 T5 M10). The rail's
    // insets need the container's height, which only the measure pass knows, so railInsets reads it there.
    Box(modifier = modifier.semantics { isTraversalGroup = true }) {
        content()
        if (scrollable && sections.isNotEmpty()) {
            FastScrollRail(
                sections = sections,
                onSectionSelected = { section ->
                    scope.launch { listState.scrollToItem(section.firstIndex + itemOffset) }
                },
                modifier = Modifier.align(Alignment.TopEnd).then(railInsets),
            )
        }
    }
}

/**
 * Sizes the rail to the container's height less [bottom] and the top inset, and reports the container's full height,
 * so the rail sits [top] below the container's top. A top inset never squeezes the rail below
 * [RollaDimens.fastScrollMinRailHeight] (a landscape Playlists tab), so a drag still reaches every letter and the rail
 * keeps an area for its TalkBack actions; the cap's floor of 0 keeps a container shorter than that from a negative
 * inset.
 */
private fun Modifier.railInsets(top: Dp, bottom: Dp): Modifier = layout { measurable, constraints ->
    val containerHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    val bottomPx = bottom.roundToPx()
    val railRoom = (containerHeight - bottomPx - RollaDimens.fastScrollMinRailHeight.roundToPx()).coerceAtLeast(0)
    val topPx = top.roundToPx().coerceAtMost(railRoom)
    val railHeight = (containerHeight - topPx - bottomPx).coerceAtLeast(0)
    val rail = measurable.measure(constraints.copy(minHeight = railHeight, maxHeight = railHeight))
    layout(rail.width, containerHeight) { rail.place(0, topPx) }
}

/**
 * The label the current gesture last selected, reset at each tap and drag start so each one ticks, and the y of the
 * current gesture's down point, where a drag selects first.
 */
private class LabelTracker {
    var lastLabel: String? = null
    var downY: Float? = null
}
