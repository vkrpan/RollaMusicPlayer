package com.rolla.musicplayer.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The preview's viewport is 339 × 110 units (the artwork threshold width); it is drawn at 4 px per unit. */
private const val PREVIEW_SCALE = 4
private const val PREVIEW_WIDTH_PX = 339 * PREVIEW_SCALE
private const val PREVIEW_HEIGHT_PX = 110 * PREVIEW_SCALE

/** Anti-aliasing and the vector's bitmap cache may move a sampled pixel a few levels off its palette value. */
private const val CHANNEL_TOLERANCE = 3

/** The five glyph cells: 28 units square, top at y 58, centred on x 113, 161, 209, 257, 305 (the 48-unit cells). */
private const val GLYPH_UNITS = 28
private const val GLYPH_PX = GLYPH_UNITS * PREVIEW_SCALE
private const val GLYPH_TOP_PX = 58 * PREVIEW_SCALE
private val GLYPH_CELLS = listOf(
    Triple("widget_ic_previous", R.drawable.widget_ic_previous, 113),
    Triple("widget_ic_skip_back_15", R.drawable.widget_ic_skip_back_15, 161),
    Triple("widget_ic_play", R.drawable.widget_ic_play, 209),
    Triple("widget_ic_skip_forward_15", R.drawable.widget_ic_skip_forward_15, 257),
    Triple("widget_ic_next", R.drawable.widget_ic_next, 305),
)

/** The note's 44-unit box, centred in the artwork box: x 22.5–66.5, y 33–77. */
private const val NOTE_PX = 44 * PREVIEW_SCALE
private const val NOTE_LEFT_PX = 90 // 22.5 units × 4
private const val NOTE_TOP_PX = 33 * PREVIEW_SCALE

private const val PREVIEW_GLYPH_MIN_IOU = 0.95

/** Palette values (ruling 4) behind and of the glyphs. */
private const val CARD = 0xFF000000.toInt()
private const val PLACEHOLDER = 0xFF363636.toInt()
private const val GLYPH_TINT = 0xFFFCFCFE.toInt()

/**
 * The widget picker's preview (`widget_preview.xml`) is drawn only in the widget palette (Phase 6c ruling 4) and
 * follows the plan's geometry: the card, the artwork placeholder, the five glyphs and the progress bar. Its glyph
 * copies match the widget drawables. Everything is drawn by the platform renderer ([drawableBitmap]).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Trivial stub (an empty drawable) → (5, 55) is transparent, not #000000 → RED. Control: the card stays black at
    // (200, 55), between the text and the glyph row, while the play glyph's centre (209, 72) is the glyph tint, so a
    // preview painted in one colour cannot pass. Mutation: the card keeps its v1.0 dark grey → (5, 55) is 28 levels
    // off → RED.
    @Test
    fun previewUsesThePalette() {
        val preview = renderPreview()

        assertPixel("card at (5, 55)", 0xFF000000, preview, 5f, 55f)
        assertPixel("artwork placeholder at (15, 25)", 0xFF363636, preview, 15f, 25f)
        assertPixel("progress fill at (100, 97.5)", 0xFF2F6FF0, preview, 100f, 97.5f)
        assertPixel("progress track at (300, 97.5)", 0xFF646466, preview, 300f, 97.5f)
        assertPixel("control: card between the text and the glyph row at (200, 55)", 0xFF000000, preview, 200f, 55f)
        assertPixel("control: play glyph centre at (209, 72)", 0xFFFCFCFE, preview, 209f, 72f)
    }

    // The preview holds copies of the glyph paths; this pins each copy to its widget drawable, so a redraw that is
    // not copied into the preview fails. Each glyph cell (28 units = 112 px) and the note box (44 units = 176 px) is
    // cropped from the 4× render and reduced to an ink mask (the crop is opaque, so ink coverage stands in for
    // alpha), then compared with the drawable's alpha mask at the same pixel size, both drawn by the platform renderer.
    // Trivial stub (an empty preview) → every crop's mask is empty, IoU 0 → RED. Control: the play cell against the
    // pause drawable scores < 0.95, so the metric can fail. Mutation: move the arrowhead in
    // widget_ic_skip_forward_15.xml only → the forward cell → RED.
    @Test
    fun previewGlyphsMatchTheWidgetDrawables() {
        val preview = renderPreview()
        val cells = GLYPH_CELLS.map { (name, id, centreX) ->
            val left = (centreX - GLYPH_UNITS / 2) * PREVIEW_SCALE
            Triple(name, id, preview.inkMask(GLYPH_PX, left, GLYPH_TOP_PX, CARD, GLYPH_TINT))
        }
        val note = preview.inkMask(NOTE_PX, NOTE_LEFT_PX, NOTE_TOP_PX, PLACEHOLDER, GLYPH_TINT)
        val crops = cells + Triple("widget_ic_music_note", R.drawable.widget_ic_music_note, note)
        val scores = crops.map { (name, id, crop) -> name to iou(crop, drawableMask(id, crop.size)) }
        val all = scores.joinToString { (name, value) -> "$name = ${value.fmt()}" }
        val playCell = cells.single { it.first == "widget_ic_play" }.third
        val control = iou(playCell, drawableMask(R.drawable.widget_ic_pause, GLYPH_PX))

        assertTrue(
            "control: the preview's play cell vs widget_ic_pause, IoU ${control.fmt()}, " +
                "must be < $PREVIEW_GLYPH_MIN_IOU",
            control < PREVIEW_GLYPH_MIN_IOU,
        )
        scores.forEach { (name, value) ->
            assertTrue(
                "preview copy of $name: IoU ${value.fmt()}, must be >= $PREVIEW_GLYPH_MIN_IOU (all: $all)",
                value >= PREVIEW_GLYPH_MIN_IOU,
            )
        }
    }

    private fun renderPreview(): Bitmap =
        context.drawableBitmap(R.drawable.widget_preview, PREVIEW_WIDTH_PX, PREVIEW_HEIGHT_PX)

    /** The widget drawable [id] drawn by the platform renderer at [size] × [size] px, as an alpha mask. */
    private fun drawableMask(@DrawableRes id: Int, size: Int): GlyphMask =
        context.drawableBitmap(id, size, size).alphaMask(size)

    /** Asserts the pixel at viewport point ([x], [y]) is within [CHANNEL_TOLERANCE] of [expected] on every channel. */
    private fun assertPixel(label: String, expected: Long, bitmap: Bitmap, x: Float, y: Float) {
        val want = expected.toInt()
        val actual = bitmap.getPixel((x * PREVIEW_SCALE).toInt(), (y * PREVIEW_SCALE).toInt())
        val near = listOf(24, 16, 8, 0).all { shift ->
            abs((want shr shift and 0xFF) - (actual shr shift and 0xFF)) <= CHANNEL_TOLERANCE
        }
        assertTrue(
            "$label: expected ${Integer.toHexString(want)} +/-$CHANNEL_TOLERANCE, was ${Integer.toHexString(actual)}",
            near,
        )
    }
}
