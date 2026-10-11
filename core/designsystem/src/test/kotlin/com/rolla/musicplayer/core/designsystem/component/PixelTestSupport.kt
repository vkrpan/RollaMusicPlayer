package com.rolla.musicplayer.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.junit.Assert.assertTrue
import kotlin.math.abs

// Anti-aliased edges and gradient sampling put a pixel within a few levels of its token, never exactly on it.
private const val CHANNEL_TOLERANCE = 6

/**
 * Draws the activity content view into a software bitmap. captureToImage() times out under Robolectric (it waits for a
 * frame commit that never comes), but a direct software draw runs the Compose draw phase under NATIVE graphics. This
 * is the OneUiTabRowTest precedent, shared by the kit pixel tests.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.drawRoot(): Bitmap {
    waitForIdle()
    lateinit var bitmap: Bitmap
    runOnUiThread {
        val view = activity.findViewById<View>(android.R.id.content)
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }
    return bitmap
}

/** Asserts that the ARGB [actual] is within [CHANNEL_TOLERANCE] of [expected] on every channel, alpha included. */
internal fun assertColorNear(expected: Color, actual: Int) {
    val want = expected.toArgb()
    val shifts = listOf(24, 16, 8, 0)
    val close = shifts.all { shift ->
        abs((want shr shift and 0xFF) - (actual shr shift and 0xFF)) <= CHANNEL_TOLERANCE
    }
    assertTrue("expected ${Integer.toHexString(want)} +/-$CHANNEL_TOLERANCE, was ${Integer.toHexString(actual)}", close)
}
