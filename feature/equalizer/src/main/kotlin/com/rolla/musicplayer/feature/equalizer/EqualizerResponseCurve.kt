package com.rolla.musicplayer.feature.equalizer

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.media.equalizer.TARGET_FREQUENCIES_HZ
import kotlin.math.abs
import kotlin.math.max

private val ResponseCurveHeight = 60.dp
private val ResponseCurveStrokeWidth = 2.dp
private val ResponseCurveCenterLineWidth = 1.dp
private const val CURVE_AMPLITUDE_FRACTION = 0.85f
private const val CURVE_FILL_TOP_ALPHA = 0.18f
private const val MIN_GAIN_SPAN_MILLIBEL = 1f

/**
 * Decorative, gains-driven frequency-response curve: a smooth line through the current
 * [gainsMillibel], purely a rendering of the gain settings the sliders already show, and NOT a
 * live spectrum or audio visualization. This intentionally has no dependency on
 * android.media.audiofx.Visualizer, AudioRecord, MediaRecorder, or any PCM/audio-buffer API: real
 * audio capture was rejected in code review because it unconditionally requires RECORD_AUDIO,
 * which conflicts with the offline/privacy-first architecture described in CLAUDE.md. Every input
 * here is a value the equalizer screen UI state already holds.
 *
 * Motion is driven only by changes to [gainsMillibel] and [enabled]; there is no idle pulsing or
 * oscillation and no time-based or random input, so a static EQ setting always renders as a
 * perfectly static curve. Each of the nine points animates independently with the same
 * GainValueSpring the vertical sliders use (or snaps instantly when isReducedMotion is true), so a
 * preset selection sweeps this curve in sync with the sliders it mirrors.
 *
 * Excluded from the accessibility tree via Modifier.clearAndSetSemantics because it visualizes the
 * same nine gain values TalkBack already announces via the sliders in EqualizerBandsRow, so
 * exposing it too would only be a duplicate, noisier announcement with no new information.
 */
@Composable
internal fun EqualizerResponseCurve(
    gainsMillibel: List<Short>,
    minGainMillibel: Short,
    maxGainMillibel: Short,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedGains = rememberAnimatedGains(gainsMillibel, minGainMillibel, maxGainMillibel)

    // A bypassed EQ reads as a flat, muted line (sliderInactiveTrack) rather than the accent color.
    // A direct switch (no crossfade) keeps this file free of any dependency beyond animation-core,
    // which EqualizerScreen.kt already relies on for the sliders themselves.
    val curveColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.sliderInactiveTrack
    val centerLineColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ResponseCurveHeight)
            .clearAndSetSemantics { },
    ) {
        drawResponseCurve(
            animatedGains = animatedGains,
            curveColor = curveColor,
            centerLineColor = centerLineColor,
            fillColor = fillColor,
            enabled = enabled,
        )
    }
}

/**
 * Builds and keeps in sync one [Animatable] per band, normalized against [minGainMillibel]/
 * [maxGainMillibel] with 0 gain at the vertical center (see [normalizedGain]). One LaunchedEffect
 * per band, keyed by that band own target, mirrors the per-slider pattern VerticalGainSlider
 * already uses. The band count is fixed (TARGET_FREQUENCIES_HZ.size, always 9) for the composable
 * whole lifetime, so calling LaunchedEffect from inside this forEachIndexed is stable across
 * recompositions, the same way EqualizerBandsRow already calls VerticalGainSlider in a loop.
 */
@Composable
private fun rememberAnimatedGains(
    gainsMillibel: List<Short>,
    minGainMillibel: Short,
    maxGainMillibel: Short,
): List<Animatable<Float, AnimationVector1D>> {
    val reducedMotion = isReducedMotion()

    // Symmetric normalization around the min/max range's larger magnitude (rather than a plain
    // min..max lerp) so 0 gain always maps to the vertical center even if the device reported
    // range happens to be asymmetric -- see the visual spec 0-gain-vertical-center rule.
    val maxAbsGainMillibel = remember(minGainMillibel, maxGainMillibel) {
        max(abs(minGainMillibel.toFloat()), abs(maxGainMillibel.toFloat())).coerceAtLeast(MIN_GAIN_SPAN_MILLIBEL)
    }

    val animatedGains = remember {
        List(TARGET_FREQUENCIES_HZ.size) { index ->
            Animatable(normalizedGain(gainsMillibel.getOrElse(index) { 0 }, maxAbsGainMillibel))
        }
    }

    gainsMillibel.forEachIndexed { index, gainMillibel ->
        val target = normalizedGain(gainMillibel, maxAbsGainMillibel)
        LaunchedEffect(target, reducedMotion) {
            if (reducedMotion) {
                animatedGains[index].snapTo(target)
            } else {
                animatedGains[index].animateTo(target, GainValueSpring)
            }
        }
    }

    return animatedGains
}

/**
 * Both the point list and the Path are rebuilt on every drawn frame, inside this draw scope, never
 * hoisted into composition or remembered. Reading each Animatable value here (not before Canvas is
 * called) is what keeps the running spring animation from causing any recomposition: only this
 * draw phase re-runs per frame. A fresh small Offset list plus one Path per frame is the same kind
 * of per-frame draw-phase allocation as any Canvas curve or chart drawing code, not a
 * recomposition-time allocation.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawResponseCurve(
    animatedGains: List<Animatable<Float, AnimationVector1D>>,
    curveColor: Color,
    centerLineColor: Color,
    fillColor: Color,
    enabled: Boolean,
) {
    val centerY = size.height / 2f
    drawLine(
        color = centerLineColor,
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = ResponseCurveCenterLineWidth.toPx(),
    )

    val points = curvePoints(animatedGains, size)
    val curvePath = smoothPath(points)

    if (enabled) {
        drawPath(
            path = fillPathUnder(curvePath, size),
            brush = Brush.verticalGradient(
                colors = listOf(fillColor.copy(alpha = CURVE_FILL_TOP_ALPHA), Color.Transparent),
                endY = size.height,
            ),
        )
    }

    drawPath(
        path = curvePath,
        color = curveColor,
        style = Stroke(width = ResponseCurveStrokeWidth.toPx(), cap = StrokeCap.Round),
    )
}

/** Closes [curvePath] down to the bottom corners of [size] to make a fillable area-chart region. */
private fun fillPathUnder(curvePath: Path, size: Size): Path = Path().apply {
    addPath(curvePath)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private fun normalizedGain(gainMillibel: Short, maxAbsGainMillibel: Float): Float =
    (gainMillibel / maxAbsGainMillibel).coerceIn(-1f, 1f)

/**
 * One point per band, evenly spaced across the given [size] width, y normalized against [size]
 * height with 0 gain at vertical center. Reads each Animatable value directly; call this only from
 * inside a draw scope (once per drawn frame), never from composition.
 */
private fun curvePoints(animatedGains: List<Animatable<Float, AnimationVector1D>>, size: Size): List<Offset> {
    val bandCount = animatedGains.size
    val stepX = if (bandCount > 1) size.width / (bandCount - 1) else 0f
    val centerY = size.height / 2f
    val amplitude = centerY * CURVE_AMPLITUDE_FRACTION
    return animatedGains.mapIndexed { index, animatable ->
        Offset(x = index * stepX, y = centerY - animatable.value * amplitude)
    }
}

/**
 * A smoothed line through [points] using horizontal-midpoint cubic Bezier control points, a
 * standard, allocation-light chart-smoothing technique that needs no external charting library.
 */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    val first = points.firstOrNull() ?: return@apply
    moveTo(first.x, first.y)
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val midX = (previous.x + current.x) / 2f
        cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
    }
}

private val PREVIEW_ENABLED_GAINS_MILLIBEL: List<Short> = listOf(300, 150, 0, -100, -150, -50, 100, 250, 400)
private val PREVIEW_DISABLED_GAINS_MILLIBEL: List<Short> = listOf(0, 100, 200, 100, 0, -100, -200, -100, 0)
private const val PREVIEW_MIN_GAIN_MILLIBEL: Short = -1500
private const val PREVIEW_MAX_GAIN_MILLIBEL: Short = 1500
private val PreviewCurveHeight = 80.dp

@Suppress("UnusedPrivateMember")
@Preview(name = "Response Curve Enabled - Light")
@Preview(name = "Response Curve Enabled - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEqualizerResponseCurveEnabled() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            EqualizerResponseCurve(
                gainsMillibel = PREVIEW_ENABLED_GAINS_MILLIBEL,
                minGainMillibel = PREVIEW_MIN_GAIN_MILLIBEL,
                maxGainMillibel = PREVIEW_MAX_GAIN_MILLIBEL,
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PreviewCurveHeight),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Response Curve Disabled - Light")
@Preview(name = "Response Curve Disabled - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEqualizerResponseCurveDisabled() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            EqualizerResponseCurve(
                gainsMillibel = PREVIEW_DISABLED_GAINS_MILLIBEL,
                minGainMillibel = PREVIEW_MIN_GAIN_MILLIBEL,
                maxGainMillibel = PREVIEW_MAX_GAIN_MILLIBEL,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PreviewCurveHeight),
            )
        }
    }
}
