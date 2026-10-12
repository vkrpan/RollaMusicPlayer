package com.rolla.musicplayer.core.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertTrue
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.math.abs

// Robolectric pixel-test helpers shared by every module's JVM tests (moved from the private copies in
// :core:designsystem and :feature:player). Use drawRoot() for plain trees, drawLatestDialog() for a dialog window, and
// captureWindow() (with HardwarePixelCopyRule) for trees that contain a sharedElement.

/** Anti-aliased edges and gradient sampling put a pixel within a few levels of its token, never exactly on it. */
const val PIXEL_CHANNEL_TOLERANCE = 6

private const val PIXEL_COPY_RENDER_MODE = "robolectric.pixelCopyRenderMode"

/**
 * Draws the activity content view into a software bitmap. captureToImage() times out under Robolectric (it waits for a
 * frame commit that never comes), but a direct software draw runs the Compose draw phase under NATIVE graphics. It is
 * shared by every module's Robolectric pixel tests.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.drawRoot(): Bitmap {
    waitForIdle()
    lateinit var bitmap: Bitmap
    runOnUiThread {
        val view = activity.findViewById<View>(android.R.id.content)
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }
    return bitmap
}

/**
 * Draws the Compose view of the most recently shown dialog into a software bitmap, as [drawRoot] does for the activity
 * content (which cannot see a dialog: it is a separate window). getBoundsInRoot() of a node inside the dialog indexes
 * the result directly.
 */
fun ComposeTestRule.drawLatestDialog(): Bitmap {
    waitForIdle()
    lateinit var bitmap: Bitmap
    runOnUiThread {
        val window = checkNotNull(ShadowDialog.getLatestDialog().window) { "the latest dialog has no window" }
        val view = checkNotNull(window.decorView.composeViewOrNull()) { "no Compose view in the dialog" }
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }
    return bitmap
}

private fun View.composeViewOrNull(): View? = when (this) {
    is AbstractComposeView -> this
    is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).composeViewOrNull() }
    else -> null
}

/**
 * Captures the activity window with PixelCopy, rendered by Robolectric's HardwareRenderer (the test applies
 * [HardwarePixelCopyRule]). The kit's software view.draw cannot be used here: a sharedElement always records into a
 * RenderNode-backed GraphicsLayer, and a software Canvas rejects drawRenderNode (SDK 29+) or fails the
 * DisplayListCanvas cast (SDK 23–28). captureToImage() times out under Robolectric.
 *
 * Requires API 26 (PixelCopy.request with a Window); its callers are Robolectric tests running at SDK 26 or above.
 */
@RequiresApi(Build.VERSION_CODES.O)
fun AndroidComposeTestRule<*, out ComponentActivity>.captureWindow(): Bitmap {
    waitForIdle()
    lateinit var bitmap: Bitmap
    var result = -1
    runOnUiThread {
        val view = activity.window.decorView
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
    }
    waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()
    check(result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
    // Crop to the content view, whose origin is the Compose root's, so getBoundsInRoot() indexes the result directly.
    lateinit var content: Bitmap
    runOnUiThread {
        val view = activity.findViewById<View>(android.R.id.content)
        val origin = IntArray(2).also { view.getLocationInWindow(it) }
        content = Bitmap.createBitmap(bitmap, origin[0], origin[1], view.width, view.height)
    }
    return content
}

/** True when the ARGB [actual] is within [PIXEL_CHANNEL_TOLERANCE] of [expected] on every channel, alpha included. */
fun isColorNear(expected: Color, actual: Int): Boolean {
    val want = expected.toArgb()
    return ARGB_CHANNEL_SHIFTS.all { shift ->
        abs((want shr shift and CHANNEL_MASK) - (actual shr shift and CHANNEL_MASK)) <= PIXEL_CHANNEL_TOLERANCE
    }
}

/**
 * Asserts that the ARGB [actual] is within [PIXEL_CHANNEL_TOLERANCE] of [expected] on every channel, alpha included.
 */
fun assertColorNear(expected: Color, actual: Int) {
    val want = expected.toArgb()
    assertTrue(
        "expected ${Integer.toHexString(want)} +/-$PIXEL_CHANNEL_TOLERANCE, was ${Integer.toHexString(actual)}",
        isColorNear(expected, actual),
    )
}

// Bit offsets of alpha, red, green and blue in an ARGB_8888 int, and one channel's mask.
private val ARGB_CHANNEL_SHIFTS = listOf(24, 16, 8, 0)
private const val CHANNEL_MASK = 0xFF

/** Every pixel of [bitmap] inside [bounds] (root coordinates, dp). */
fun AndroidComposeTestRule<*, out ComponentActivity>.pixelsIn(bitmap: Bitmap, bounds: DpRect): List<Int> =
    with(density) {
        (bounds.left.roundToPx() until bounds.right.roundToPx()).flatMap { x ->
            (bounds.top.roundToPx() until bounds.bottom.roundToPx()).map { y -> bitmap.getPixel(x, y) }
        }
    }

/**
 * Renders PixelCopy through Robolectric's HardwareRenderer for the duration of each test, as [captureWindow] needs.
 * Declare it with `@get:Rule(order = 0)` and the compose rule with `order = 1`, so the property is set before the
 * compose rule launches its activity and cleared after the activity is torn down.
 */
class HardwarePixelCopyRule : TestWatcher() {

    override fun starting(description: Description) {
        System.setProperty(PIXEL_COPY_RENDER_MODE, "hardware")
    }

    override fun finished(description: Description) {
        System.clearProperty(PIXEL_COPY_RENDER_MODE)
    }
}
