package com.rolla.musicplayer.feature.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import kotlin.math.sqrt

/** The five transport controls: previous · −15 s · play/pause · +15 s · next. */
private const val CONTROL_COUNT = 5

// Estimated platform default line height for 13–16 sp text, as a multiple of the font size (ruling 3).
private const val TEXT_LINE_HEIGHT_FACTOR = 1.33f

/**
 * How the widget card lays out at one launcher size (Phase 6c rulings 2, 3, P12 and P14).
 *
 * There are two vertical modes because the launcher can report a smaller height through `LocalSize` than it draws (on
 * the Motorola edge 70 about 167 dp for a 211 dp two-row card, and at most 89.6 dp for the 97.7 dp one-row card).
 * Centring the content needs height the launcher is known to draw, so:
 * - [centreContent] true: the reported height holds the nominal content plus [widgetBarCornerClearance] above and below
 *   it. The content wraps its height and is centred in the card; the padding and the bar inset are 0.
 * - [centreContent] false: the row fills the card the launcher draws, and [verticalPadding] is computed from the
 *   reported height. The text block takes what the controls and the bar leave, so it gives way first, and
 *   [barBottomInset] lifts the bar clear of the card's corner. The controls and the bar stay whole while the height is
 *   at least 48 + 3 + 4.51 ≈ 55.5 dp; the provider's `minResizeHeight` is 70 dp.
 *
 * @property showArtwork whether the artwork box is drawn; below [widgetArtworkMinWidth] the text column takes the
 *   full width so every control cell keeps a [RollaDimens.minTouchTarget] box.
 * @property controlCellWidth the width of each of the five equal control cells.
 * @property centreContent whether the content wraps its height and is centred in the card (ruling P14).
 * @property verticalPadding 0 when [centreContent] is true; otherwise the top and bottom card padding,
 *   `max(0, (height − nominal) / 2)`, which is under [widgetBarCornerClearance] in this mode.
 * @property barBottomInset 0 when [centreContent] is true; otherwise extra bottom padding under the text column
 *   (title, controls, progress bar) so that `verticalPadding + barBottomInset` is at least [widgetBarCornerClearance]
 *   and the bar's outer end clears the card's corner arc (ruling P12). Applied to the text column only, so the artwork
 *   stays centred.
 */
internal data class WidgetLayout(
    val showArtwork: Boolean,
    val controlCellWidth: Dp,
    val centreContent: Boolean,
    val verticalPadding: Dp,
    val barBottomInset: Dp,
)

/**
 * How far above the card's bottom edge the progress bar's bottom must sit to clear the card's rounded corner (ruling
 * P12): `r − √(r² − (r − p)²)` with `r = widgetCardRadius` and `p` the smaller of the start and end card paddings,
 * i.e. 24 − √(576 − 196) ≈ 4.51 dp.
 *
 * The bar's outer end sits [RollaDimens.widgetCardPaddingEnd] in from the card's side, and when the artwork hides its
 * start end sits [RollaDimens.widgetCardPaddingStart] in; the corner arc rises this far above the bottom edge at the
 * nearer of the two. The clearance cannot rely on [WidgetLayout.verticalPadding] alone: the launcher can report a
 * smaller height through `LocalSize` than it draws (on the Motorola edge 70 about 167 dp for a 211 dp two-row card,
 * and ≤ 89.6 dp at one row), so the computed padding is 0 while the drawn card still has its 24 dp corners.
 */
internal fun widgetBarCornerClearance(): Dp {
    val radius = RollaDimens.widgetCardRadius.value
    val inset = radius - minOf(RollaDimens.widgetCardPaddingStart, RollaDimens.widgetCardPaddingEnd).value
    return (radius - sqrt(radius * radius - inset * inset)).dp
}

/** The narrowest card that shows the artwork and still gives five [RollaDimens.minTouchTarget] cells: 339 dp. */
internal fun widgetArtworkMinWidth(): Dp = RollaDimens.widgetCardPaddingStart + RollaDimens.widgetArt +
    RollaDimens.widgetArtGap + RollaDimens.minTouchTarget * CONTROL_COUNT + RollaDimens.widgetCardPaddingEnd

/** The narrowest card that fits five [RollaDimens.minTouchTarget] cells without the artwork: 260 dp. */
internal fun widgetMinWidth(): Dp =
    RollaDimens.widgetCardPaddingStart + RollaDimens.minTouchTarget * CONTROL_COUNT + RollaDimens.widgetCardPaddingEnd

/**
 * The layout for a card of [size] (the `LocalSize` the launcher reports under `SizeMode.Exact`, which can be smaller
 * than the card it draws) at the system [fontScale].
 */
internal fun widgetLayoutFor(size: DpSize, fontScale: Float): WidgetLayout {
    val showArtwork = size.width >= widgetArtworkMinWidth()
    val artworkColumn = if (showArtwork) RollaDimens.widgetArt + RollaDimens.widgetArtGap else 0.dp
    val textColumn = size.width - RollaDimens.widgetCardPaddingStart - RollaDimens.widgetCardPaddingEnd - artworkColumn
    val textSizes = RollaDimens.widgetTitleSize.value + RollaDimens.widgetArtistSize.value
    val textNominal = (textSizes * TEXT_LINE_HEIGHT_FACTOR * fontScale).dp
    val contentNominal =
        maxOf(RollaDimens.widgetArt, textNominal + RollaDimens.minTouchTarget + RollaDimens.widgetProgressHeight)
    // Ruling P14: centre only when the reported height holds the content and a corner clearance above and below it.
    // The Motorola edge 70 draws the card taller than it reports, so there the centred content keeps that clearance;
    // a launcher that drew less than it reports could clip a centred card.
    val centreContent = size.height >= contentNominal + widgetBarCornerClearance() * 2
    val verticalPadding =
        if (centreContent) 0.dp else ((size.height - contentNominal) / 2).coerceAtLeast(0.dp)
    val barBottomInset =
        if (centreContent) 0.dp else (widgetBarCornerClearance() - verticalPadding).coerceAtLeast(0.dp)
    return WidgetLayout(
        showArtwork = showArtwork,
        controlCellWidth = textColumn / CONTROL_COUNT,
        centreContent = centreContent,
        verticalPadding = verticalPadding,
        barBottomInset = barBottomInset,
    )
}
