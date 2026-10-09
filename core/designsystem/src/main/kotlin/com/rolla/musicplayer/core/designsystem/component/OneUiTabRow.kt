package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.tabSelected
import com.rolla.musicplayer.core.designsystem.theme.tabUnselected
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One UI's center-weighted tab row (spec §6/§7.2). The current tab is centered, large and onSurface; its neighbors
 * shrink toward tabUnselected and gray out, and clip at the screen edges. There is no indicator. All motion follows
 * the pager's position (current page + offset fraction): layout handles placement and slot widths, and draw handles
 * scale and color, so a swipe animates continuously without recomposing.
 *
 * Each tab's touch target is its whole slot: the scaled label plus half the spacing on each side, at the full row
 * height (at least [RollaDimens.tabRowHeight]), so the row has no dead zones between or around labels.
 *
 * Caller contract:
 * - [titles] has exactly [PagerState.pageCount] entries, one per page.
 * - [onTabClick] should call `pagerState.animateScrollToPage(index)` (spec §6). The caller owns that animation,
 *   including honoring reduced motion.
 * - The row needs a bounded width (it centers the current tab in it).
 * - [enabled] false disables every tab (no click, reported as disabled), for example while a list is in selection
 *   mode and paging is locked.
 */
@Composable
fun OneUiTabRow(
    titles: List<String>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectedStyle = MaterialTheme.typography.tabSelected
    val minScale = MaterialTheme.typography.tabUnselected.fontSize.value / selectedStyle.fontSize.value
    val colors = tabLabelColors()
    val position = remember(pagerState) { { pagerState.currentPage + pagerState.currentPageOffsetFraction } }
    val measurePolicy = remember(minScale, position) { tabRowMeasurePolicy(minScale, position) }
    Layout(
        content = {
            titles.forEachIndexed { index, title ->
                TabLabel(
                    title = title,
                    selected = pagerState.currentPage == index,
                    emphasis = { tabEmphasis(position(), index) },
                    minScale = minScale,
                    style = selectedStyle,
                    colors = colors,
                    enabled = enabled,
                    onClick = { onTabClick(index) },
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.tabRowHeight)
            .clipToBounds()
            .selectableGroup(),
        measurePolicy = measurePolicy,
    )
}

/**
 * Measures each tab as its full-height slot (see [tabSlotEdges]) and places the slots side by side. The pager position
 * is read here, in the layout phase, never in composition.
 */
private fun tabRowMeasurePolicy(minScale: Float, position: () -> Float) = MeasurePolicy { measurables, constraints ->
    require(constraints.hasBoundedWidth) { "OneUiTabRow needs a bounded width" }
    // A tab's intrinsic size is its unscaled label (TabLabel's slot layout reports it when unconstrained).
    val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
    val labelHeight = measurables.indices.maxOfOrNull { measurables[it].maxIntrinsicHeight(widths[it]) } ?: 0
    val height = maxOf(constraints.minHeight, labelHeight)
    val edges = tabSlotEdges(widths, RollaDimens.tabSpacing.toPx(), minScale, position(), constraints.maxWidth)
    val slots = measurables.mapIndexed { index, measurable ->
        measurable.measure(Constraints.fixed(width = edges[index + 1] - edges[index], height = height))
    }
    layout(constraints.maxWidth, height) {
        slots.forEachIndexed { index, slot -> slot.placeRelative(x = edges[index], y = 0) }
    }
}

private data class TabLabelColors(val selected: Color, val unselected: Color)

@Composable
@ReadOnlyComposable
private fun tabLabelColors(): TabLabelColors =
    MaterialTheme.colorScheme.let { TabLabelColors(selected = it.onSurface, unselected = it.tabUnselected) }

// Eight distinct inputs of one private label; a holder type for them would only add indirection.
@Suppress("LongParameterList")
@Composable
private fun TabLabel(
    title: String,
    selected: Boolean,
    emphasis: () -> Float,
    minScale: Float,
    style: TextStyle,
    colors: TabLabelColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        text = title,
        modifier = Modifier
            // Outside the scaling layer, so the touch target is the unscaled, full-height slot.
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .tabSlot()
            .graphicsLayer {
                val scale = tabScale(minScale, emphasis())
                scaleX = scale
                scaleY = scale
            }
            .padding(vertical = RollaDimens.tabLabelVerticalPadding),
        style = style,
        maxLines = 1,
        color = { lerp(colors.unselected, colors.selected, emphasis()) },
    )
}

/**
 * Fills the size the row measures the tab at (its slot) and centers the unscaled label in it. Unconstrained (the
 * row's intrinsic queries), it reports the label's own size.
 */
private fun Modifier.tabSlot(): Modifier = layout { measurable, constraints ->
    val label = measurable.measure(Constraints())
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else label.width
    val height = maxOf(constraints.minHeight, label.height)
    layout(width, height) {
        // Centering is direction-neutral, so plain place() avoids a rounding flip in RTL.
        label.place(x = labelInset(width, label.width), y = (height - label.height) / 2)
    }
}

/** 1 on the current page, falling linearly to 0 one page away. */
internal fun tabEmphasis(position: Float, index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

/** Label scale for an emphasis: [minScale] when unselected, 1 when selected. */
internal fun tabScale(minScale: Float, emphasis: Float): Float = minScale + (1f - minScale) * emphasis

/** The x the row centers on: interpolated between the two tab centers around [position], clamped to the ends. */
internal fun interpolateAnchor(centers: List<Float>, position: Float): Float {
    if (centers.isEmpty()) return 0f
    val clamped = position.coerceIn(0f, (centers.size - 1).toFloat())
    val lower = floor(clamped).toInt()
    val upper = min(lower + 1, centers.size - 1)
    return centers[lower] + (centers[upper] - centers[lower]) * (clamped - lower)
}

/**
 * Edges (px, LTR) of each tab's touch slot: tab i spans `[edges[i], edges[i + 1])`, so slots are contiguous. A slot is
 * the label's scaled width plus half of [spacing] on each side, and the row is shifted so the interpolated current
 * label sits at the row center. Returns `widths.size + 1` edges, or none for an empty row.
 */
internal fun tabSlotEdges(
    widths: List<Int>,
    spacing: Float,
    minScale: Float,
    position: Float,
    rowWidth: Int,
): List<Int> {
    if (widths.isEmpty()) return emptyList()
    val starts = ArrayList<Float>(widths.size + 1)
    val centers = ArrayList<Float>(widths.size)
    var cursor = 0f
    widths.forEachIndexed { index, width ->
        val scaled = width * tabScale(minScale, tabEmphasis(position, index))
        starts += cursor
        centers += cursor + scaled / 2f
        cursor += scaled + spacing
    }
    starts += cursor
    val shift = rowWidth / 2f - interpolateAnchor(centers, position) - spacing / 2f
    return starts.map { (shift + it).roundToInt() }
}

/** Left of an unscaled label centered in a slot (negative when the unscaled label is wider than its slot). */
internal fun labelInset(slotWidth: Int, labelWidth: Int): Int = ((slotWidth - labelWidth) / 2f).roundToInt()
