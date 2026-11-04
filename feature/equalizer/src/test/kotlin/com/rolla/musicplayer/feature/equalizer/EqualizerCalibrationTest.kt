package com.rolla.musicplayer.feature.equalizer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.eqCaption
import com.rolla.musicplayer.core.designsystem.theme.labelSecondary
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.model.EqualizerPreset
import com.rolla.musicplayer.core.testing.drawRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

// A track pixel is within this of its token on every channel; the card, the grid line and the ring's hollow are
// far outside it.
private const val TRACK_TOLERANCE = 24

/**
 * The Equaliser's Phase 7 calibration geometry and colours, on the production [EqualizerScreen]: the columns' edge
 * inset (ruling P12), the full-length track, the card's bottom padding, the caption's start and the label colours.
 * 3 px/dp, so the 3 dp track is several pixels wide. The root is 411 dp wide, so a host can lay the screen out at the
 * Motorola's 406.7 dp; [screenWidth] is read in composition, so a test can flip it without a second setContent.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqualizerCalibrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var screenWidth by mutableStateOf(MOTOROLA_WIDTH)
    private var colors: List<Color> = emptyList()

    // Ruling P12, measured on the 406.7 dp Motorola and the 360 dp reference: the first and last column centres sit
    // eqColumnEdgeInset (65 dp) from the edges; at 360 dp that is the minimum pitch, 28.7 dp. One composition: the
    // width flips. Column centres are read from the frequency labels, which are centred on their columns.
    // Trivial stub / mutation: the old fixed eqColumnPitch columns centred as a group → 88.5 dp at 406.7 → RED.
    @Test
    fun columnsSitAtTheEdgeInsetAndNeverBelowTheMinimumPitch() {
        setScreen()
        assertColumns(MOTOROLA_WIDTH, expectedPitch = (MOTOROLA_WIDTH - RollaDimens.eqColumnEdgeInset * 2) / 8)
        screenWidth = REFERENCE_WIDTH
        composeRule.waitForIdle()
        assertColumns(REFERENCE_WIDTH, expectedPitch = RollaDimens.eqColumnPitch)
    }

    // The slider draws the full eqSliderLength (it drew eqSliderLength − eqThumbDiameter = 224 dp). Every gain is 0 dB,
    // so the thumb sits mid-track: the top end of the 40 Hz column's track is inactive (sliderInactiveTrack), the
    // bottom end active (primary). Control: the column's track is found at both ends. Mutation: size the band and the
    // sliders eqSliderLength tall (the old layout) → 224 dp → RED.
    @Test
    fun sliderDrawsTheFullEqSliderLength() {
        setScreen(width = REFERENCE_WIDTH, preset = BUILT_IN_EQUALIZER_PRESETS.first()) // Balanced: all 0 dB
        val bitmap = composeRule.drawRoot()
        val (primary, inactive) = colors
        val x = centrePx(text = "40")
        // Only the band between the value labels ("0" for every band) and the frequency labels, so no glyph edge of a
        // label in this column can pass for a track pixel.
        val band = with(composeRule.density) {
            val valueBottom = composeRule.onAllNodesWithText("0").onFirst().getBoundsInRoot().bottom
            val frequencyTop = composeRule.onNodeWithText("40").getBoundsInRoot().top
            valueBottom.roundToPx() until frequencyTop.roundToPx()
        }
        val top = band.firstOrNull { isNear(inactive, bitmap.getPixel(x, it)) }
        val bottom = band.lastOrNull { isNear(primary, bitmap.getPixel(x, it)) }
        assertTrue("control: the track's ends were found ($top..$bottom px in $band)", top != null && bottom != null)
        val length = with(composeRule.density) { (checkNotNull(bottom) - checkNotNull(top) + 1).toDp() }
        assertEquals("track length (dp)", RollaDimens.eqSliderLength.value, length.value, 1f)
    }

    // Ruling P20: the track's top sits eqValueLabelTrackGap (14.7 dp, as measured) below the value labels' baseline,
    // where their digits end; the band's half-thumb inset counts inside the gap. The track top is read from the pixels
    // (the first sliderInactiveTrack pixel below the value label in the 40 Hz column; every gain is 0 dB, so the thumb
    // is mid-track). Control: the track top is found. Mutation: drop the value row's paddingFromBaseline (the old
    // spacing, ≈ 11 dp) → RED.
    @Test
    fun trackTopIsEqValueLabelTrackGapBelowTheValueBaseline() {
        setScreen(width = REFERENCE_WIDTH, preset = BUILT_IN_EQUALIZER_PRESETS.first()) // Balanced: all 0 dB
        val bitmap = composeRule.drawRoot()
        val inactive = colors[INACTIVE_TRACK]
        val x = centrePx(text = "40")
        val value = composeRule.onAllNodesWithText("0").onFirst()
        val valueBounds = value.getBoundsInRoot()
        val layouts = mutableListOf<TextLayoutResult>()
        value.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        with(composeRule.density) {
            val baseline = valueBounds.top + layouts.single().lastBaseline.toDp()
            val frequencyTop = composeRule.onNodeWithText("40").getBoundsInRoot().top
            // From the label's box bottom, so no anti-aliased edge of the digit itself can pass for the track.
            val trackTop = (valueBounds.bottom.roundToPx() until frequencyTop.roundToPx())
                .firstOrNull { isNear(inactive, bitmap.getPixel(x, it)) }
            assertTrue("control: the track top was found below the value baseline", trackTop != null)
            assertEquals(
                "value baseline → track top (dp)",
                RollaDimens.eqValueLabelTrackGap.value,
                (checkNotNull(trackTop).toDp() - baseline).value,
                1f,
            )
        }
    }

    // The card ends eqCardBottomPadding below the frequency labels' baseline (where their digits end, as measured).
    // Mutations: the old padding(vertical = settingsRowPadding) → 19 dp below the label box → RED; pad the label row's
    // bottom by eqCardBottomPadding (from the box, not the baseline) → 48 dp → RED.
    @Test
    fun cardEndsEqCardBottomPaddingBelowTheFrequencyBaseline() {
        setScreen(width = REFERENCE_WIDTH)
        val card = composeRule.onNodeWithTag(EQ_SLIDER_CARD_TAG).getBoundsInRoot()
        val label = composeRule.onNodeWithText("40").getBoundsInRoot()
        val baseline = with(composeRule.density) { textLayout("40").lastBaseline.toDp() }
        assertEquals(
            "frequency baseline → card bottom (dp)",
            RollaDimens.eqCardBottomPadding.value,
            (card.bottom - (label.top + baseline)).value,
            0.5f,
        )
    }

    // The caption starts eqCaptionStart from the screen edge, in eqCaption; the band labels are labelSecondary
    // (ruling P13). Control: eqCaption and labelSecondary differ from onSurface and onSurfaceVariant in the dark theme,
    // so each colour check can fail. Mutations: caption padded by chipGridStart (the old start) → RED; caption in
    // onSurface → RED; band labels in onSurfaceVariant → RED.
    @Test
    fun captionAndBandLabelsTakeTheirTokens() {
        setScreen(width = REFERENCE_WIDTH)
        val eqCaptionColor = colors[EQ_CAPTION]
        val labelSecondaryColor = colors[LABEL_SECONDARY]
        val onSurface = colors[ON_SURFACE]
        val onSurfaceVariant = colors[ON_SURFACE_VARIANT]
        assertNotEquals("control: eqCaption differs from onSurface", onSurface, eqCaptionColor)
        assertNotEquals("control: labelSecondary differs from onSurfaceVariant", onSurfaceVariant, labelSecondaryColor)
        val caption = presetCaption(BassBoostPreset.id)
        composeRule.onNodeWithText(caption).getBoundsInRoot().left
            .let { assertEquals("caption start (dp)", RollaDimens.eqCaptionStart.value, it.value, 0.5f) }
        assertEquals("caption colour", eqCaptionColor, textLayout(caption).layoutInput.style.color)
        assertEquals("frequency label colour", labelSecondaryColor, textLayout("40").layoutInput.style.color)
        assertEquals("value label colour", labelSecondaryColor, textLayout("+6").layoutInput.style.color)
    }

    private fun assertColumns(width: Dp, expectedPitch: Dp) {
        val first = centreX("40")
        val pitch = centreX("80") - first
        assertEquals("at $width: first column centre (dp)", RollaDimens.eqColumnEdgeInset.value, first.value, 1f)
        val lastFromEnd = width - centreX("10k")
        assertEquals(
            "at $width: last column centre from the end (dp)",
            RollaDimens.eqColumnEdgeInset.value,
            lastFromEnd.value,
            1f,
        )
        assertEquals("at $width: pitch (dp)", expectedPitch.value, pitch.value, 0.1f)
    }

    // LongMethod: the captured theme colours and the production screen's full parameter list, in one host.
    @Suppress("LongMethod")
    private fun setScreen(width: Dp = MOTOROLA_WIDTH, preset: EqualizerPreset = BassBoostPreset) {
        screenWidth = width
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val scheme = MaterialTheme.colorScheme
                colors = listOf(
                    scheme.primary,
                    scheme.sliderInactiveTrack,
                    scheme.eqCaption,
                    scheme.labelSecondary,
                    scheme.onSurface,
                    scheme.onSurfaceVariant,
                )
                Box(Modifier.fillMaxHeight().width(screenWidth)) {
                    EqualizerScreen(
                        uiState = EqualizerUiState(
                            enabled = true,
                            gainsMillibel = BandGains(preset.gainsMillibel),
                            presets = BUILT_IN_EQUALIZER_PRESETS,
                            selectedPresetId = preset.id,
                        ),
                        onNavigateUp = {},
                        onSetEnabled = {},
                        onBandGainChange = { _, _ -> },
                        onSelectPreset = {},
                        onDeletePreset = {},
                        onSaveAsPresetClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun centreX(text: String): Dp {
        val bounds = composeRule.onNodeWithText(text).getBoundsInRoot()
        return (bounds.left + bounds.right) / 2
    }

    private fun centrePx(text: String): Int = with(composeRule.density) { centreX(text).toPx().roundToInt() }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun isNear(expected: Color, actual: Int): Boolean {
        val want = expected.toArgb()
        return listOf(16, 8, 0).all { shift ->
            abs((want shr shift and 0xFF) - (actual shr shift and 0xFF)) <= TRACK_TOLERANCE
        }
    }

    private companion object {
        val MOTOROLA_WIDTH = 406.7.dp
        val REFERENCE_WIDTH = 360.dp

        // Indices into [colors], in the order setScreen captures them.
        const val INACTIVE_TRACK = 1
        const val EQ_CAPTION = 2
        const val LABEL_SECONDARY = 3
        const val ON_SURFACE = 4
        const val ON_SURFACE_VARIANT = 5
    }
}
