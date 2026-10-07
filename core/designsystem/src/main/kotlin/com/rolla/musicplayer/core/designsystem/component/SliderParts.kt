package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Position of [value] within [valueRange] as 0..1. Values are clamped, and an empty range yields 0 instead of NaN. */
internal fun sliderFraction(value: Float, valueRange: ClosedFloatingPointRange<Float>): Float {
    val span = valueRange.endInclusive - valueRange.start
    return if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
}

/**
 * Rounded slider track: inactive full width, active from the start edge to the thumb center. It mirrors in RTL.
 * The state is read in the draw phase only, so dragging redraws and never recomposes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderTrackLine(
    state: SliderState,
    thickness: Dp,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = inactiveColor, cornerRadius = radius)
        val activeWidth = size.width * sliderFraction(state.value, state.valueRange)
        if (activeWidth > 0f) {
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - activeWidth else 0f
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(left, 0f),
                size = Size(activeWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** A circular thumb: a [fillColor] disc with a [ringWidth] ring of [ringColor]. */
@Composable
internal fun RingThumb(
    diameter: Dp,
    ringWidth: Dp,
    ringColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .background(fillColor, CircleShape)
            .border(ringWidth, ringColor, CircleShape),
    )
}
