package com.rolla.musicplayer.feature.equalizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.eqGridLine
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

// A ring pixel is within this of primary on every channel; the 35 % curve and the gray track are far outside it.
private const val RING_TOLERANCE = 24

// The grid line and the card differ by only ~6 levels per channel, so the grid check is tight.
private const val GRID_TOLERANCE = 2

// The curve's 35 % primary stroke lifts the card's blue channel by ~80; anything above +30 is the curve.
private const val CURVE_BLUE_LIFT = 30

// Rows searched around the expected curve point, either side.
private const val CURVE_SEARCH_ROWS = 4

/**
 * Pixel checks of the slider card's geometry at font scale 2 (where the label rows make a whole-column frame visibly
 * wrong): the curve passes through the thumbs mid-drag, and the 0 dB grid line runs through the sliders' center. The
 * thumbs are located from the pixels themselves (the first primary ring row in a band's column), never from the code
 * under test. The screen is the production [EqualizerScreen]; the host only feeds reported gains back into its state,
 * as the ViewModel does. Rendering: view.draw into a bitmap (captureToImage times out under Robolectric).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqualizerScreenPixelTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var gains by mutableStateOf(BandGains(BassBoostPreset.gainsMillibel))
    private var primary = Color.Unspecified
    private var card = Color.Unspecified
    private var gridLine = Color.Unspecified

    private fun setHost(initial: List<Short>) {
        gains = BandGains(initial)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    primary = MaterialTheme.colorScheme.primary
                    card = MaterialTheme.colorScheme.surfaceContainer
                    gridLine = MaterialTheme.colorScheme.eqGridLine
                    EqualizerScreen(
                        uiState = EqualizerUiState(
                            enabled = true,
                            gainsMillibel = gains,
                            presets = BUILT_IN_EQUALIZER_PRESETS,
                            selectedPresetId = null,
                        ),
                        onNavigateUp = {},
                        onSetEnabled = {},
                        onBandGainChange = { band, gain ->
                            gains = BandGains(gains.values.toMutableList().also { it[band] = gain })
                        },
                        onSelectPreset = {},
                        onDeletePreset = {},
                        onSaveAsPresetClick = {},
                    )
                }
            }
        }
    }

    // Mutations: (a) the curve springs while dragging (drop isDragging from its snap) → the 40 Hz point lags far
    // behind its thumb two frames after a big move → RED. (b) the old 0.85 amplitude fraction instead of the thumb's
    // travel → the point misses the thumb by ~20 px → RED. (c) the curve framed on the whole band column → RED.
    @Test
    fun curveRunsThroughTheThumbsMidDrag() {
        setHost(BassBoostPreset.gainsMillibel)
        val slider = rule.onNodeWithContentDescription("40Hz gain")
        slider.performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f)) // pull 40 Hz well below its +6 dB start
        }
        rule.waitForIdle() // the clock auto-advances: the curve fades in and settles
        rule.mainClock.autoAdvance = false
        slider.performTouchInput { moveBy(Offset(0f, -900f)) } // then to the top, still dragging
        // Two frames: the first recomposes with the new gain (the curve, read at draw time, snaps in it); the second
        // lets the slider, which reads its value in composition, draw the thumb there too.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val bitmap = drawRoot()

        val x0 = columnCenterPx("40")
        val x1 = columnCenterPx("80")
        val xMid = (x0 + x1) / 2
        val yMid = ((thumbCenterPx(bitmap, x0) + thumbCenterPx(bitmap, x1)) / 2).roundToInt()
        val curveRow = (yMid - CURVE_SEARCH_ROWS..yMid + CURVE_SEARCH_ROWS).firstOrNull { y ->
            Color(bitmap.getPixel(xMid, y)).blueLevel() > card.blueLevel() + CURVE_BLUE_LIFT
        }
        assertTrue("no curve pixel at x=$xMid within $CURVE_SEARCH_ROWS px of y=$yMid", curveRow != null)

        slider.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
    }

    // Mutation: draw the grid behind the whole band column (labels included) instead of the slider band → at font
    // scale 2 its 0 dB line sits 2 dp above the sliders' center → RED.
    @Test
    fun zeroDbGridLineRunsThroughTheSlidersCenter() {
        setHost(List(BUILT_IN_EQUALIZER_PRESETS.first().gainsMillibel.size) { 0 }) // every thumb at 0 dB
        val bitmap = drawRoot()
        val zeroDbRow = thumbCenterPx(bitmap, columnCenterPx("40")).roundToInt()
        val nearCardStart = (RollaDimens.settingsCardInset.value * rule.density.density).roundToInt()
        val pixel = bitmap.getPixel(nearCardStart, zeroDbRow)
        assertTrue(
            "pixel at the sliders' 0 dB row is ${Integer.toHexString(pixel)}, not the grid line " +
                Integer.toHexString(gridLine.toArgb()),
            isNear(gridLine.toArgb(), pixel, GRID_TOLERANCE) && !isNear(card.toArgb(), pixel, GRID_TOLERANCE),
        )
    }

    private fun drawRoot(): Bitmap {
        rule.waitForIdle()
        lateinit var bitmap: Bitmap
        rule.runOnUiThread {
            val view = rule.activity.findViewById<View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    /** The x (px) of a band's column center, from its frequency label (which is centered on the column). */
    private fun columnCenterPx(frequencyLabel: String): Int {
        val bounds = rule.onNodeWithText(frequencyLabel).getBoundsInRoot()
        return ((bounds.left + bounds.right).value / 2 * rule.density.density).roundToInt()
    }

    /**
     * The y (px) of a band's thumb center, read from the pixels. Scanning the column top-down: the ring's top arc is
     * the first primary run, then the hollow center (card colored), then the bottom arc and the active track (primary
     * again). The hollow is symmetric about the center, so its midpoint is the center, free of the edges' anti-aliasing
     * bias.
     */
    private fun thumbCenterPx(bitmap: Bitmap, x: Int): Float {
        fun isRing(y: Int) = isNear(primary.toArgb(), bitmap.getPixel(x, y), RING_TOLERANCE)
        val ringTop = (0 until bitmap.height).first { isRing(it) }
        val hollowStart = (ringTop until bitmap.height).first { !isRing(it) }
        val hollowEnd = (hollowStart until bitmap.height).first { isRing(it) } // exclusive
        return (hollowStart + hollowEnd) / 2f
    }

    private fun Color.blueLevel(): Int = (toArgb() and 0xFF)

    private fun isNear(expected: Int, actual: Int, tolerance: Int): Boolean =
        listOf(16, 8, 0).all { shift -> abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) <= tolerance }
}
