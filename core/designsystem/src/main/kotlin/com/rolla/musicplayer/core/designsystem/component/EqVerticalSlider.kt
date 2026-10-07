package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack

private const val VERTICAL_ROTATION_DEGREES = 270f

/**
 * One vertical EQ band slider (spec §8.5). It has a [RollaDimens.eqSliderTrack] track (primary below the thumb,
 * sliderInactiveTrack above) and a [RollaDimens.eqThumbDiameter] hollow ring thumb. It is a rotated M3 Slider, the
 * same proven technique as the v1.0 VerticalGainSlider, so SetProgress and keyboard semantics are kept.
 * [contentDescription] must name the band ("40 Hz gain"); without it, TalkBack announces all nine bands identically.
 * When disabled (the EQ is off) it renders at [DISABLED_CONTENT_ALPHA] (spec §8.5) and ignores input.
 *
 * The caller must bound the height (the band length, [RollaDimens.eqSliderLength]); under unbounded height, for
 * example inside verticalScroll, the slider collapses. The width is the column the caller gives it
 * ([RollaDimens.eqColumnPitch]). Direct hits land only on M3's content height, a band centered in that column, and
 * Compose's minimum-touch-target extension fills the column to its width. That is an accepted exception below
 * [RollaDimens.minTouchTarget] (spec §5.4).
 *
 * The layout is pinned to LTR so RTL mirroring can't flip the band upside down. The pin also applies to [modifier],
 * so pass only size or weight modifiers, never start/end-relative ones.
 */
// M3 Slider's parameters plus band label and thumb scale, in one flat rotated layout; splitting adds nothing.
@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumbScale: () -> Float = { 1f },
) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA)
                .verticalSlider()
                .semantics { this.contentDescription = contentDescription },
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            thumb = {
                RingThumb(
                    diameter = RollaDimens.eqThumbDiameter,
                    ringWidth = RollaDimens.sliderThumbRing,
                    ringColor = colors.primary,
                    fillColor = colors.surfaceContainer,
                    modifier = Modifier.graphicsLayer {
                        val scale = thumbScale()
                        scaleX = scale
                        scaleY = scale
                    },
                )
            },
            track = { state ->
                SliderTrackLine(
                    state = state,
                    thickness = RollaDimens.eqSliderTrack,
                    activeColor = colors.primary,
                    inactiveColor = colors.sliderInactiveTrack,
                )
            },
            valueRange = valueRange,
        )
    }
}

/** Rotates a horizontal slider so its start sits at the bottom, swapping the measured width and height. */
private fun Modifier.verticalSlider(): Modifier = graphicsLayer {
    rotationZ = VERTICAL_ROTATION_DEGREES
    transformOrigin = TransformOrigin(0f, 0f)
}.layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        placeable.place(-placeable.width, 0)
    }
}
