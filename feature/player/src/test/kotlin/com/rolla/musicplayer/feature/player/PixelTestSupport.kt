package com.rolla.musicplayer.feature.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.DpRect
import org.robolectric.Shadows.shadowOf
import kotlin.math.abs

// Adapted from :core:designsystem's test PixelTestSupport (test source sets are not shared across modules).
// Anti-aliased edges and gradient sampling put a pixel within a few levels of its token, never exactly on it.
private const val CHANNEL_TOLERANCE = 6

/**
 * Captures the activity window with PixelCopy, rendered by Robolectric's HardwareRenderer (the test sets
 * robolectric.pixelCopyRenderMode=hardware). The kit's software view.draw cannot be used here: a sharedElement always
 * records into a RenderNode-backed GraphicsLayer, and a software Canvas rejects drawRenderNode (SDK 29+) or fails the
 * DisplayListCanvas cast (SDK 23–28). captureToImage() times out under Robolectric.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.captureWindow(): Bitmap {
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

/** True when the ARGB [actual] is within [CHANNEL_TOLERANCE] of [expected] on every channel, alpha included. */
internal fun isColorNear(expected: Color, actual: Int): Boolean {
    val want = expected.toArgb()
    return listOf(24, 16, 8, 0).all { shift ->
        abs((want shr shift and 0xFF) - (actual shr shift and 0xFF)) <= CHANNEL_TOLERANCE
    }
}

/** Every pixel of [bitmap] inside [bounds] (root coordinates, dp). */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.pixelsIn(bitmap: Bitmap, bounds: DpRect): List<Int> =
    with(density) {
        (bounds.left.roundToPx() until bounds.right.roundToPx()).flatMap { x ->
            (bounds.top.roundToPx() until bounds.bottom.roundToPx()).map { y -> bitmap.getPixel(x, y) }
        }
    }
