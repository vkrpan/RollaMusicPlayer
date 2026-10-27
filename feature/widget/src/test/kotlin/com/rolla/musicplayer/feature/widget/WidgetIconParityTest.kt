package com.rolla.musicplayer.feature.widget

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.testing.drawRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Each glyph is drawn into a 96 × 96 px box: 4 px per unit of the 24-unit viewport. */
private const val ICON_PX = 96
private const val PX_PER_UNIT = ICON_PX / 24

private const val PARITY_MIN_IOU = 0.95
private const val PLAY_PAUSE_MAX_IOU = 0.6
private const val RING_SELF_MIRROR_MAX_IOU = 0.9
private const val MIRRORED_NUMERALS_MAX_IOU = 0.8

/** The numeral box of the ±15 glyphs (ruling 5): viewport x 8–16, y 9–16. */
private val NUMERAL_BOX_X = 8 * PX_PER_UNIT until 16 * PX_PER_UNIT
private val NUMERAL_BOX_Y = 9 * PX_PER_UNIT until 16 * PX_PER_UNIT

/**
 * The widget's glyph drawables mirror `RollaIcons` (Phase 6c ruling 5). A widget drawable is drawn by the platform
 * renderer the launcher uses ([drawableBitmap]); a `RollaIcons` vector is drawn by Compose, captured with drawRoot().
 * Both renders are 96 × 96 px and are reduced to alpha masks (alpha >= 128); two glyphs match when the masks'
 * intersection over union (IoU) is high. One composition per test: the RollaIcons vector shown is a mutable state.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetIconParityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var rollaIcon by mutableStateOf(RollaIcons.Play)

    // Trivial stub (an empty drawable) → that pair's IoU is 0 → RED. Control: two different RollaIcons score < 0.6, so
    // the metric can fail. Mutation: widget_ic_play keeps the old Material path "M8,5v14l11,-7z" → the play pair → RED.
    @Test
    fun transportGlyphsMatchRollaIcons() {
        showRollaIcons()
        val control = iou(rollaMask(RollaIcons.Play), rollaMask(RollaIcons.Pause))
        val pairs = listOf(
            Triple("widget_ic_previous", R.drawable.widget_ic_previous, RollaIcons.SkipPrevious),
            Triple("widget_ic_next", R.drawable.widget_ic_next, RollaIcons.SkipNext),
            Triple("widget_ic_play", R.drawable.widget_ic_play, RollaIcons.Play),
            Triple("widget_ic_pause", R.drawable.widget_ic_pause, RollaIcons.Pause),
            Triple("widget_ic_music_note", R.drawable.widget_ic_music_note, RollaIcons.MusicNote),
        ).map { (drawable, id, icon) ->
            "$drawable vs ${icon.name}" to iou(widgetMask(id), rollaMask(icon))
        }
        val all = pairs.joinToString { (name, value) -> "$name = ${value.fmt()}" }

        assertTrue(
            "control: IoU(RollaIcons.Play, RollaIcons.Pause) = ${control.fmt()}, must be < $PLAY_PAUSE_MAX_IOU",
            control < PLAY_PAUSE_MAX_IOU,
        )
        pairs.forEach { (name, value) ->
            assertTrue(
                "$name: IoU ${value.fmt()}, must be >= $PARITY_MIN_IOU (all pairs: $all)",
                value >= PARITY_MIN_IOU,
            )
        }
    }

    // Trivial stub (forward = back) → the forward ring equals the unmirrored back ring, which the control shows scores
    // < 0.9 → RED. Mutation: the forward ring is not mirrored → its arrowhead sits on the other side → RED.
    @Test
    fun forwardRingMirrorsBackRing() {
        val back = widgetMask(R.drawable.widget_ic_skip_back_15)
        val forward = widgetMask(R.drawable.widget_ic_skip_forward_15)
        val outsideNumerals = { x: Int, y: Int -> !inNumeralBox(x, y) }
        val selfMirror = iou(back, back.mirrored(), outsideNumerals)
        val mirrored = iou(forward, back.mirrored(), outsideNumerals)

        assertTrue(
            "control: back ring vs its own mirror outside the numeral box, IoU ${selfMirror.fmt()}, " +
                "must be < $RING_SELF_MIRROR_MAX_IOU (the ring is not symmetric)",
            selfMirror < RING_SELF_MIRROR_MAX_IOU,
        )
        assertTrue(
            "forward ring vs mirrored back ring outside the numeral box: IoU ${mirrored.fmt()}, " +
                "must be >= $PARITY_MIN_IOU",
            mirrored >= PARITY_MIN_IOU,
        )
    }

    // Trivial stub (no numerals) → the inside-box IoU is 0 → RED. Mutation: mirror the whole forward icon → its
    // numerals read "51" → IoU(forward, mirrored back) inside the box is high → RED.
    @Test
    fun fifteenStaysUpright() {
        val back = widgetMask(R.drawable.widget_ic_skip_back_15)
        val forward = widgetMask(R.drawable.widget_ic_skip_forward_15)
        val same = iou(forward, back, ::inNumeralBox)
        val mirrored = iou(forward, back.mirrored(), ::inNumeralBox)
        val both = "IoU(forward, back) = ${same.fmt()}, IoU(forward, mirrored back) = ${mirrored.fmt()}"

        assertTrue(
            "numerals: forward vs mirrored back inside the numeral box must be < $MIRRORED_NUMERALS_MAX_IOU " +
                "(\"15\" must not read \"51\"); $both",
            mirrored < MIRRORED_NUMERALS_MAX_IOU,
        )
        assertTrue(
            "numerals: forward vs back inside the numeral box must be >= $PARITY_MIN_IOU; $both",
            same >= PARITY_MIN_IOU,
        )
    }

    private fun showRollaIcons() {
        composeRule.setContent {
            val size = with(LocalDensity.current) { ICON_PX.toDp() }
            Image(painter = rememberVectorPainter(rollaIcon), contentDescription = null, modifier = Modifier.size(size))
        }
    }

    /** Shows [icon] through Compose and returns its alpha mask: the 96 × 96 px box at the root's top-left corner. */
    private fun rollaMask(icon: ImageVector): GlyphMask {
        composeRule.runOnIdle { rollaIcon = icon }
        return composeRule.drawRoot().alphaMask(ICON_PX)
    }

    /** The widget drawable [id] drawn by the platform renderer at 96 × 96 px, as an alpha mask. */
    private fun widgetMask(@DrawableRes id: Int): GlyphMask =
        context.drawableBitmap(id, ICON_PX, ICON_PX).alphaMask(ICON_PX)
}

private fun inNumeralBox(x: Int, y: Int): Boolean = x in NUMERAL_BOX_X && y in NUMERAL_BOX_Y
