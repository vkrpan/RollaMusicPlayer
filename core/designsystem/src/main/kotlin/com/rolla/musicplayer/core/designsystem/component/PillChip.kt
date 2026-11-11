package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.chipLabel

/**
 * The EQ preset chip (spec §8.5): a [RollaDimens.chipHeight] pill with a bold label. Selected is primary/onPrimary;
 * unselected is surfaceContainerHigh/onSurface. The color change springs, or snaps under reduced motion. It is
 * single-select (RadioButton role); the caller wraps a grid of these in Modifier.selectableGroup().
 * [onLongClick], when set, adds a long-press action (e.g. rename/delete a saved preset); when null the chip exposes
 * no long-click action at all. [onLongClickLabel] names that action for TalkBack (e.g. "Delete preset"), which
 * otherwise announces only a generic "long press to activate"; it is ignored when [onLongClick] is null.
 *
 * Footprint: the pill is drawn at [RollaDimens.chipHeight] but occupies [RollaDimens.minTouchTarget] of layout height,
 * with the slack split evenly above and below. Callers must space chip rows with [RollaDimens.chipRowLayoutGap], not
 * [RollaDimens.chipGap].
 */
// Two animated colors plus one flat pill/label layout; splitting it would only add indirection.
// Seven parameters: the M3 chip shape (label, selected, onClick, modifier, enabled) plus the optional long press
// and its accessibility label.
@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalFoundationApi::class) // combinedClickable is still experimental in Compose foundation 1.7
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val container by animateColorAsState(
        targetValue = chipContainerColor(selected),
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = chipContentColor(selected),
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "chipContent",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA) // before background, so the whole pill fades
            .chipSurface(container = container, shape = CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.RadioButton,
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = RollaDimens.chipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        ChipLabelText(label = label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A display-only chip in the unselected [PillChip] look (custom tags phase 2, fix round 1): the same container,
 * label style, colours and horizontal padding, with no click target, no role and no 48 dp touch footprint, so the
 * row or card that owns the click reads [label] as its own text (the tag editor's Tags and Subtags rows).
 *
 * The label never ellipsizes: a name too long for one line wraps inside the chip. One line keeps the pill
 * (CircleShape, [RollaDimens.chipHeight] tall); a wrapped label switches to the medium shape, a rounded rectangle,
 * since a pill's ends would cut into the second line. [RollaDimens.labelChipVerticalPadding] keeps wrapped lines off
 * the edges and makes a one-line chip exactly chipHeight at the default font size.
 */
@Composable
fun LabelChip(label: String, modifier: Modifier = Modifier) {
    var wrapped by remember(label) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .chipSurface(
                container = chipContainerColor(selected = false),
                shape = if (wrapped) MaterialTheme.shapes.medium else CircleShape,
            )
            .padding(horizontal = RollaDimens.chipHorizontalPadding, vertical = RollaDimens.labelChipVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        ChipLabelText(
            label = label,
            color = chipContentColor(selected = false),
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
            onTextLayout = { wrapped = it.lineCount > 1 },
        )
    }
}

/** The chip container both chips draw: at least [RollaDimens.chipHeight] tall, clipped to [shape], [container]. */
private fun Modifier.chipSurface(container: Color, shape: Shape): Modifier =
    heightIn(min = RollaDimens.chipHeight).clip(shape).background(container)

/** Container: primary when selected, else surfaceContainerHigh. */
@Composable
@ReadOnlyComposable
private fun chipContainerColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh

/** Label: onPrimary when selected, else onSurface. */
@Composable
@ReadOnlyComposable
private fun chipContentColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

/** The chip label: chipLabel style, centred, in [color]. */
@Composable
private fun ChipLabelText(
    label: String,
    color: Color,
    maxLines: Int,
    overflow: TextOverflow,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = label,
        style = MaterialTheme.typography.chipLabel,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = TextAlign.Center,
        onTextLayout = onTextLayout,
    )
}
