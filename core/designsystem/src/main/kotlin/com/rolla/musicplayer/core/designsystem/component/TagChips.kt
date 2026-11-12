package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.chipLabel
import com.rolla.musicplayer.core.designsystem.theme.subTagChipBorder
import com.rolla.musicplayer.core.designsystem.theme.subTagChipLabel
import com.rolla.musicplayer.core.designsystem.theme.tagChipContainer
import com.rolla.musicplayer.core.designsystem.theme.tagChipLabel

/** How a custom tag chip is drawn: [Tonal] (filled) for a tag, [Outline] (outlined, no fill) for a subtag. */
enum class TagChipStyle { Tonal, Outline }

/** One chip of a [TagChipLine]: its [label] and its [style]. */
@Immutable
data class TagChipData(val label: String, val style: TagChipStyle)

/**
 * A small display-only custom tag chip (custom tags phase 4): a [RollaDimens.tagChipHeight] pill with a one-line
 * tagChipLabel that ellipsizes. Tonal fills with tagChipContainer and labels in tagChipLabel; Outline has no fill, a
 * [RollaDimens.tagChipBorderWidth] subTagChipBorder outline and a subTagChipLabel label. It has no click and no role:
 * the row or screen that shows it owns the description. At larger font sizes it grows taller than tagChipHeight.
 */
@Composable
fun TagChip(label: String, style: TagChipStyle, modifier: Modifier = Modifier) {
    val colors = tagChipColors(style)
    Box(
        modifier = modifier
            .heightIn(min = RollaDimens.tagChipHeight)
            .tagChipSurface(style, colors)
            .padding(horizontal = RollaDimens.tagChipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tagChipLabel,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [chips] as [TagChip]s, start-aligned (or centred when [centred]) on at most [maxLines] lines, with
 * [RollaDimens.tagChipGap] between chips and between lines. Chips that do not fit are counted in a Tonal "+N" chip at
 * the end of the last line, for which chips are dropped from the end of that line until it fits. A line's first chip
 * is placed even when it is wider than the line, or than the room left beside "+N": it is narrowed and ellipsizes,
 * unless that room is below the narrowest useful chip (its two paddings and an ellipsis), in which case it is dropped
 * and counted in "+N" as well.
 *
 * Callers omit the line when there are no chips (it would still cost a node; it then takes no space). Each chip is
 * composed in its own slot only when the plan reaches it and is measured by its intrinsic width, so its text is shaped
 * once. Some composed chips are not placed: the chip that ends a line, and chips dropped for "+N". Each pass marks
 * every composed chip shown or hidden, and a hidden chip clears its own semantics, so its label reaches neither the
 * merged description of a clickable row around the line nor the accessibility tree; shown chips keep their text. (The
 * test API's unmerged view still lists a hidden label, unplaced, under its clearing node: Compose keeps cleared
 * descendants there by design.) Only the
 * "+N" label and the ellipsis are measured with a TextMeasurer. Being a SubcomposeLayout, the line has no intrinsic
 * measurements: never put it under an `IntrinsicSize` modifier.
 */
@Composable
fun TagChipLine(chips: List<TagChipData>, modifier: Modifier = Modifier, maxLines: Int = 1, centred: Boolean = false) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.tagChipLabel
    // One slot per chip, with a stable content lambda: subcomposing it again on a later pass does not recompose it.
    val slots = remember(chips) { chips.map(::TagChipSlot) }
    SubcomposeLayout(modifier) { constraints ->
        if (chips.isEmpty()) return@SubcomposeLayout layout(constraints.minWidth, constraints.minHeight) {}
        val chipPadding = 2 * RollaDimens.tagChipHorizontalPadding.roundToPx()
        val gap = RollaDimens.tagChipGap.roundToPx()
        fun textWidth(text: String): Int = textMeasurer.oneLineWidth(text, labelStyle, this)
        val chipMeasurables = HashMap<Int, Measurable>()
        fun chip(index: Int): Measurable =
            chipMeasurables.getOrPut(index) { subcompose(index, slots[index].content).single() }

        val plan = planTagChipLine(
            chipCount = chips.size,
            maxLines = maxLines.coerceAtLeast(1),
            metrics = TagChipLineMetrics(
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity,
                gap = gap,
                chipWidth = { chip(it).maxIntrinsicWidth(Constraints.Infinity) },
                plusWidth = { textWidth(plusLabel(it)) + chipPadding },
                minChipWidth = { textWidth(ELLIPSIS) + chipPadding },
            ),
        )
        val shown = plan.lines.flatten()
        markShownChips(slots, composed = chipMeasurables.keys, shown = shown)
        val placeables = shown.map { chip(it.index).measure(Constraints(maxWidth = it.width)) }
        layoutTagChipRows(plan.rowsOf(placeables + measurePlusChip(plan)), gap, centred, constraints)
    }
}

/** Marks each chip composed this pass shown when the plan places it, hidden otherwise (see [TagChipSlot]). */
private fun markShownChips(slots: List<TagChipSlot>, composed: Set<Int>, shown: List<PlacedTagChip>) {
    val shownIndices = shown.mapTo(HashSet()) { it.index }
    composed.forEach { index -> slots[index].setShown(index in shownIndices) }
}

/**
 * One chip's slot in a [TagChipLine]: its stable content and whether the current layout pass places it. A chip is
 * composed before the plan knows whether it fits, so it starts hidden; [HiddenChipSemanticsNode] clears the chip's
 * semantics while it is hidden and is told to re-read them when that changes.
 */
private class TagChipSlot(chip: TagChipData) {
    var isShown = false
        private set
    var semanticsNode: HiddenChipSemanticsNode? = null
    val content: @Composable () -> Unit = {
        TagChip(label = chip.label, style = chip.style, modifier = HiddenChipSemanticsElement(this))
    }

    fun setShown(shown: Boolean) {
        if (isShown == shown) return
        isShown = shown
        semanticsNode?.invalidateSemantics()
    }
}

/** Attaches a [HiddenChipSemanticsNode] for [slot]. Equal per slot, so recomposition keeps the node. */
private class HiddenChipSemanticsElement(val slot: TagChipSlot) : ModifierNodeElement<HiddenChipSemanticsNode>() {
    override fun create(): HiddenChipSemanticsNode = HiddenChipSemanticsNode(slot)

    override fun update(node: HiddenChipSemanticsNode) {
        node.rebind(slot)
    }

    override fun equals(other: Any?): Boolean = other is HiddenChipSemanticsElement && other.slot === slot

    override fun hashCode(): Int = System.identityHashCode(slot)
}

/**
 * Clears a chip's semantics (its label included) while its slot is hidden, so an unplaced chip is neither its own node
 * nor part of a merging ancestor's description; adds nothing while it is shown.
 */
private class HiddenChipSemanticsNode(private var slot: TagChipSlot) : Modifier.Node(), SemanticsModifierNode {
    override val shouldClearDescendantSemantics: Boolean
        get() = !slot.isShown

    override fun SemanticsPropertyReceiver.applySemantics() = Unit

    override fun onAttach() {
        slot.semanticsNode = this
    }

    override fun onDetach() {
        if (slot.semanticsNode === this) slot.semanticsNode = null
    }

    fun rebind(newSlot: TagChipSlot) {
        if (newSlot === slot) return
        if (slot.semanticsNode === this) slot.semanticsNode = null
        slot = newSlot
        slot.semanticsNode = this
        invalidateSemantics()
    }
}

/** Composes and measures the "+N" chip at its planned width, or nothing when no chip is hidden. */
private fun SubcomposeMeasureScope.measurePlusChip(plan: TagChipLinePlan): List<Placeable> {
    if (plan.hidden == 0) return emptyList()
    return subcompose(PlusSlot) { TagChip(label = plusLabel(plan.hidden), style = TagChipStyle.Tonal) }
        .map { it.measure(Constraints(maxWidth = plan.plusWidth)) }
}

/** [text]'s width as the chip's Text lays it out: one line, ellipsis, unconstrained, in [scope]'s density. */
private fun TextMeasurer.oneLineWidth(text: String, style: TextStyle, scope: MeasureScope): Int = measure(
    text = text,
    style = style,
    overflow = TextOverflow.Ellipsis,
    maxLines = 1,
    layoutDirection = scope.layoutDirection,
    density = scope,
).size.width

/** The slot id of the "+N" chip; chips use their index. */
private object PlusSlot

/** What TextOverflow.Ellipsis draws: the narrowest useful chip is its two paddings around this. */
private const val ELLIPSIS = "…"

/** Splits [items], laid out in plan order (the shown chips, then "+N"), into one row per line; "+N" ends the last. */
private fun <T> TagChipLinePlan.rowsOf(items: List<T>): List<List<T>> {
    var next = 0
    return lines.mapIndexed { line, slots ->
        val row = items.subList(next, next + slots.size).toMutableList()
        next += slots.size
        if (line == lines.lastIndex && hidden > 0) row += items.last()
        row
    }
}

/** Stacks [rows] [gap] apart, each start-aligned or centred, chips [gap] apart and centred on their row's height. */
private fun MeasureScope.layoutTagChipRows(
    rows: List<List<Placeable>>,
    gap: Int,
    centred: Boolean,
    constraints: Constraints,
): MeasureResult {
    val rowWidths = rows.map { row -> row.sumOf { it.width } + gap * (row.size - 1) }
    val rowHeights = rows.map { row -> row.maxOf { it.height } }
    val width = rowWidths.max().coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (rowHeights.sum() + gap * (rows.size - 1)).coerceIn(constraints.minHeight, constraints.maxHeight)
    return layout(width, height) {
        var y = 0
        rows.forEachIndexed { line, row ->
            var x = if (centred) (width - rowWidths[line]) / 2 else 0
            row.forEach { placeable ->
                placeable.placeRelative(x, y + (rowHeights[line] - placeable.height) / 2)
                x += placeable.width + gap
            }
            y += rowHeights[line] + gap
        }
    }
}

/**
 * A removable filter chip (custom tags phase 4, the Find tracks filter strip): the LabelChip look (chipLabel on a
 * [RollaDimens.chipHeight] pill) with a trailing Close glyph. The whole chip is one button (Role.Button) that reads
 * [removeLabel] and calls [onRemove]; it occupies at least [RollaDimens.minTouchTarget]. Tonal fills with
 * tagChipContainer; Outline draws the subtag outline instead. A label too long for the space ellipsizes, and the
 * Close glyph stays.
 */
@Composable
fun RemovableChip(
    label: String,
    removeLabel: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    style: TagChipStyle = TagChipStyle.Tonal,
) {
    val colors = tagChipColors(style)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = RollaDimens.chipHeight)
            .tagChipSurface(style, colors)
            .clickable(role = Role.Button, onClick = onRemove)
            .semantics { contentDescription = removeLabel }
            .padding(horizontal = RollaDimens.chipHorizontalPadding, vertical = RollaDimens.labelChipVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(RollaDimens.tagChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.chipLabel,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = RollaIcons.Close,
            contentDescription = null,
            tint = colors.label,
            modifier = Modifier.size(RollaDimens.circleButtonGlyph),
        )
    }
}

/** One placed chip of a [TagChipLinePlan]: the index into the chip list and the width it is measured at. */
internal data class PlacedTagChip(val index: Int, val width: Int)

/** Which chips a [TagChipLine] shows on each line, how many it hides behind "+N", and the "+N" chip's width. */
internal class TagChipLinePlan(val lines: List<List<PlacedTagChip>>, val hidden: Int, val plusWidth: Int)

/**
 * The pixel geometry a [TagChipLine] is planned in: the line's [maxWidth] and [gap]; [chipWidth] of a chip index (its
 * full one-line width); [plusWidth] of the hidden count; [minChipWidth], the narrowest a chip may be narrowed to. The
 * three are asked lazily, only for what the plan reaches.
 */
internal class TagChipLineMetrics(
    val maxWidth: Int,
    val gap: Int,
    val chipWidth: (index: Int) -> Int,
    val plusWidth: (hidden: Int) -> Int,
    val minChipWidth: () -> Int,
)

/**
 * Plans a [TagChipLine]: fills up to [maxLines] lines left to right; when chips remain, drops chips from the end of the
 * last line until a "+N" chip (N = the chips not placed) fits after them. A chip wider than the room it has is narrowed
 * to it, or dropped (and counted) when that room is below [TagChipLineMetrics.minChipWidth].
 */
internal fun planTagChipLine(chipCount: Int, maxLines: Int, metrics: TagChipLineMetrics): TagChipLinePlan {
    val lines = mutableListOf<MutableList<PlacedTagChip>>()
    var next = 0
    while (next < chipCount && lines.size < maxLines) {
        val line = fillTagChipLine(start = next, chipCount = chipCount, metrics = metrics)
        lines += line
        next += line.size
        if (line.isEmpty()) break // too narrow for even one chip: the rest go into "+N"
    }
    if (next == chipCount) return TagChipLinePlan(lines, hidden = 0, plusWidth = 0)
    val hidden = fitPlusChip(lines.last(), hiddenBefore = chipCount - next, metrics = metrics)
    return TagChipLinePlan(lines, hidden, plusWidth = minOf(metrics.plusWidth(hidden), metrics.maxWidth))
}

/**
 * One line from chip [start]: the first chip (narrowed to the line, or none when the line is narrower than the
 * narrowest useful chip), then each next chip that still fits.
 */
private fun fillTagChipLine(start: Int, chipCount: Int, metrics: TagChipLineMetrics): MutableList<PlacedTagChip> {
    val first = metrics.chipWidth(start)
    if (first > metrics.maxWidth && metrics.maxWidth < metrics.minChipWidth()) return mutableListOf()
    val line = mutableListOf(PlacedTagChip(start, minOf(first, metrics.maxWidth)))
    var used = line.single().width.toLong()
    var next = start + 1
    while (next < chipCount) {
        val width = metrics.chipWidth(next)
        if (used + metrics.gap + width > metrics.maxWidth) break
        line += PlacedTagChip(next, width)
        used += metrics.gap + width
        next++
    }
    return line
}

/**
 * Makes room for the "+N" chip at the end of [last]: drops chips from its end, each one more hidden, until "+N" fits;
 * a lone first chip is narrowed to the room left instead, or dropped too when that room is below the narrowest useful
 * chip. Returns the final hidden count.
 */
private fun fitPlusChip(last: MutableList<PlacedTagChip>, hiddenBefore: Int, metrics: TagChipLineMetrics): Int {
    var hidden = hiddenBefore
    fun room(): Long = metrics.maxWidth.toLong() - metrics.gap - minOf(metrics.plusWidth(hidden), metrics.maxWidth)
    while (last.isNotEmpty() && last.sumOf { it.width.toLong() } + metrics.gap.toLong() * (last.size - 1) > room()) {
        if (last.size == 1 && room() >= metrics.minChipWidth()) {
            last[0] = last[0].copy(width = room().toInt())
        } else {
            last.removeAt(last.lastIndex)
            hidden++
        }
    }
    return hidden
}

private fun plusLabel(hidden: Int): String = "+$hidden"

/** A tag chip's label colour and, for Tonal, its fill; Outline's outline colour. */
private class TagChipColors(val label: Color, val fill: Color, val outline: Color)

@Composable
private fun tagChipColors(style: TagChipStyle): TagChipColors = when (style) {
    TagChipStyle.Tonal -> TagChipColors(
        label = MaterialTheme.colorScheme.tagChipLabel,
        fill = MaterialTheme.colorScheme.tagChipContainer,
        outline = Color.Unspecified,
    )
    TagChipStyle.Outline -> TagChipColors(
        label = MaterialTheme.colorScheme.subTagChipLabel,
        fill = Color.Unspecified,
        outline = MaterialTheme.colorScheme.subTagChipBorder,
    )
}

/** The pill both chip kinds draw: clipped to CircleShape, then Tonal's fill or Outline's outline. */
private fun Modifier.tagChipSurface(style: TagChipStyle, colors: TagChipColors): Modifier {
    val pill = clip(CircleShape)
    return when (style) {
        TagChipStyle.Tonal -> pill.background(colors.fill)
        TagChipStyle.Outline -> pill.border(RollaDimens.tagChipBorderWidth, colors.outline, CircleShape)
    }
}
