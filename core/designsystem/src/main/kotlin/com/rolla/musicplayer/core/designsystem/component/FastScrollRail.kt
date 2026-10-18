package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.fastScrollIndex
import com.rolla.musicplayer.core.designsystem.theme.fastScrollLetter
import com.rolla.musicplayer.core.designsystem.theme.fastScrollTrack
import kotlinx.coroutines.launch

/** UI test and baseline-profile handle for the A–Z rail. */
const val FAST_SCROLL_RAIL_TEST_TAG = "fast_scroll_rail"

private const val FAST_SCROLL_RAIL_DESCRIPTION = "Fast scroll, letter index"

/**
 * The One UI A–Z rail (spec §10): a [fastScrollTrack] pill with "#" and A–Z in equal slots, pinned to the end of a
 * list. A tap or a vertical drag calls [onSectionSelected] once each time the label under the finger changes, with a
 * `TextHandleMove` tick. When the rail is too short for 27 slots it shows every k-th label (spec §10, ruling 10), and a
 * drag still reaches the dropped ones. For TalkBack it is one node with a "Jump to X" action per populated section,
 * traversed after the list. It renders nothing for no [sections]. The touch width is `fastScrollWidth + fastScrollEnd`
 * (30 dp), the documented exception to the 48 dp rule (spec §5.4).
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
    BoxWithConstraints(
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
            .pointerInput(haptics) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        tracker.lastLabel = null
                        select(offset.y, size.height)
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
        val step = fastScrollLabelStep((maxHeight / letterSlot).toInt(), sections.size)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = RollaDimens.fastScrollEnd)
                .width(RollaDimens.fastScrollWidth)
                .background(MaterialTheme.colorScheme.fastScrollTrack, CircleShape),
        ) {
            sections.forEachIndexed { index, section ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (index % step == 0) {
                        // A slot can be shorter than the line (a short rail, a large font). The label keeps its
                        // natural height, centred on its own slot, so it is never clipped. Shown labels sit at least
                        // letterSlot apart, so they cannot overlap.
                        Text(
                            text = section.label,
                            style = letterStyle,
                            color = MaterialTheme.colorScheme.fastScrollIndex,
                            maxLines = 1,
                            modifier = Modifier.wrapContentHeight(unbounded = true),
                        )
                    }
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
 * padding; a short container (landscape) lets the rail overlap the header's end instead. A selection calls
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
    // BoxWithConstraints sizes itself like a Box (to its modifier, else its content); it adds only maxHeight, read by
    // the cap below.
    BoxWithConstraints(modifier = modifier.semantics { isTraversalGroup = true }) {
        content()
        if (scrollable && sections.isNotEmpty()) {
            // A top inset never squeezes the rail below fastScrollMinRailHeight (a landscape Playlists tab), so a drag
            // still reaches every letter and the rail keeps an area for its TalkBack actions.
            val railRoom = (maxHeight - railBottomPadding - RollaDimens.fastScrollMinRailHeight).coerceAtLeast(0.dp)
            FastScrollRail(
                sections = sections,
                onSectionSelected = { section ->
                    scope.launch { listState.scrollToItem(section.firstIndex + itemOffset) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = railTopPadding.coerceAtMost(railRoom), bottom = railBottomPadding)
                    .fillMaxHeight(),
            )
        }
    }
}

/** The label the current gesture last selected; reset at each tap and drag start, so each one ticks. */
private class LabelTracker {
    var lastLabel: String? = null
}
