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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.equalizer.TARGET_FREQUENCIES_HZ
import kotlin.math.abs
import kotlin.math.max

private const val MIN_GAIN_SPAN_MILLIBEL = 1f

// Spec §8.5: the curve's stroke is primary at 35 % alpha. It is a faint stroke behind the sliders, not text, so
// primary (not accentText) is the sanctioned color here.
private const val CURVE_STROKE_ALPHA = 0.35f

/**
 * Decorative, gains-driven frequency-response curve: a smooth line through the current
 * [gainsMillibel], purely a rendering of the gain settings the sliders already show, and NOT a
 * live spectrum or audio visualization. This intentionally has no dependency on
 * android.media.audiofx.Visualizer, AudioRecord, MediaRecorder, or any PCM/audio-buffer API: real
 * audio capture was rejected in code review because it unconditionally requires RECORD_AUDIO,
 * which conflicts with the offline/privacy-first architecture described in CLAUDE.md. Every input
 * here is a value the equalizer screen UI state already holds.
 *
 * Spec §8.5: it is drawn inside the slider card, behind the sliders, as a single [CURVE_STROKE_ALPHA] primary stroke
 * with no fill (the grid and the sliders stay legible through it), and the screen composes it only while a slider is
 * being dragged. The caller sizes it to the slider band (the row of nine sliders, eqSliderLength tall): one point
 * sits on each of the nine equal-width band columns' centers, 0 gain at the vertical center, and the range's extremes
 * at the ends of the thumb's travel (the band length minus one [RollaDimens.eqThumbDiameter]), so every point lies on
 * its slider's thumb at any font scale.
 *
 * Motion is driven only by changes to [gainsMillibel]; there is no idle pulsing or
 * oscillation and no time-based or random input, so a static EQ setting always renders as a
 * perfectly static curve. Each of the nine points animates independently with the same
 * GainValueSpring the vertical sliders use, so a preset selection sweeps this curve in sync with
 * the sliders it mirrors. While [isDragging] (and always under reduced motion) the points snap,
 * exactly as the sliders do, so the curve tracks the dragged thumb with no lag.
 *
 * Excluded from the accessibility tree via Modifier.clearAndSetSemantics because it visualizes the
 * same nine gain values TalkBack already announces via the sliders in EqualizerBandsRow, so
 * exposing it too would only be a duplicate, noisier announcement with no new information.
 */
@Composable
internal fun EqualizerResponseCurve(
    gainsMillibel: BandGains,
    minGainMillibel: Short,
    maxGainMillibel: Short,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedGains = rememberAnimatedGains(gainsMillibel, minGainMillibel, maxGainMillibel, isDragging)
    val curveColor = MaterialTheme.colorScheme.primary.copy(alpha = CURVE_STROKE_ALPHA)

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        drawResponseCurve(animatedGains = animatedGains, curveColor = curveColor)
    }
}

/**
 * Builds and keeps in sync one [Animatable] per band, normalized against [minGainMillibel]/
 * [maxGainMillibel] with 0 gain at the vertical center (see [normalizedGain]). One LaunchedEffect
 * per band, keyed by that band own target, mirrors the per-slider pattern BandGainSlider
 * already uses. The band count is fixed (TARGET_FREQUENCIES_HZ.size, always 9) for the composable
 * whole lifetime, so calling LaunchedEffect from inside this forEachIndexed is stable across
 * recompositions, the same way EqualizerBandsRow already calls BandGainSlider in a loop.
 */
@Composable
private fun rememberAnimatedGains(
    gainsMillibel: BandGains,
    minGainMillibel: Short,
    maxGainMillibel: Short,
    isDragging: Boolean,
): List<Animatable<Float, AnimationVector1D>> {
    val reducedMotion = rememberReducedMotion()

    // Symmetric normalization around the min/max range's larger magnitude (rather than a plain
    // min..max lerp) so 0 gain always maps to the vertical center even if the device reported
    // range happens to be asymmetric -- see the visual spec 0-gain-vertical-center rule.
    val maxAbsGainMillibel = remember(minGainMillibel, maxGainMillibel) {
        max(abs(minGainMillibel.toFloat()), abs(maxGainMillibel.toFloat())).coerceAtLeast(MIN_GAIN_SPAN_MILLIBEL)
    }

    val animatedGains = remember {
        List(TARGET_FREQUENCIES_HZ.size) { index ->
            Animatable(normalizedGain(gainsMillibel.values.getOrElse(index) { 0 }, maxAbsGainMillibel))
        }
    }

    gainsMillibel.values.forEachIndexed { index, gainMillibel ->
        val target = normalizedGain(gainMillibel, maxAbsGainMillibel)
        LaunchedEffect(target, reducedMotion, isDragging) {
            if (isDragging || reducedMotion) {
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
 *
 * The stroke width reuses [RollaDimens.sliderThumbRing], the EQ's own 2 dp stroke weight (there is no curve token).
 */
private fun DrawScope.drawResponseCurve(animatedGains: List<Animatable<Float, AnimationVector1D>>, curveColor: Color) {
    drawPath(
        path = smoothPath(curvePoints(animatedGains, size, RollaDimens.eqThumbDiameter.toPx() / 2f)),
        color = curveColor,
        style = Stroke(width = RollaDimens.sliderThumbRing.toPx(), cap = StrokeCap.Round),
    )
}

private fun normalizedGain(gainMillibel: Short, maxAbsGainMillibel: Float): Float =
    (gainMillibel / maxAbsGainMillibel).coerceIn(-1f, 1f)

/**
 * One point per band, at the center of each of the [size] width's equal band columns (so each point sits over its
 * slider), y normalized against [size] height with 0 gain at vertical center and +/-1 at [thumbInset] from the top and
 * bottom edges, where M3's slider stops the thumb's center. Reads each Animatable value directly; call this only from
 * inside a draw scope (once per drawn frame), never from composition.
 */
private fun curvePoints(
    animatedGains: List<Animatable<Float, AnimationVector1D>>,
    size: Size,
    thumbInset: Float,
): List<Offset> {
    val bandCount = animatedGains.size
    val columnWidth = if (bandCount > 0) size.width / bandCount else 0f
    val centerY = size.height / 2f
    val amplitude = (centerY - thumbInset).coerceAtLeast(0f)
    return animatedGains.mapIndexed { index, animatable ->
        Offset(x = (index + 0.5f) * columnWidth, y = centerY - animatable.value * amplitude)
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

private val PREVIEW_GAINS_MILLIBEL: List<Short> = listOf(300, 150, 0, -100, -150, -50, 100, 250, 400)
private const val PREVIEW_MIN_GAIN_MILLIBEL: Short = -1500
private const val PREVIEW_MAX_GAIN_MILLIBEL: Short = 1500
private val PreviewCurveHeight = 80.dp

@Suppress("UnusedPrivateMember")
@Preview(name = "Response Curve - Light")
@Preview(name = "Response Curve - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEqualizerResponseCurve() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            EqualizerResponseCurve(
                gainsMillibel = BandGains(PREVIEW_GAINS_MILLIBEL),
                minGainMillibel = PREVIEW_MIN_GAIN_MILLIBEL,
                maxGainMillibel = PREVIEW_MAX_GAIN_MILLIBEL,
                isDragging = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PreviewCurveHeight),
            )
        }
    }
}
