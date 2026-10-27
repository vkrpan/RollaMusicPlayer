package com.rolla.musicplayer.feature.widget

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

private const val DP_TOLERANCE = 0.01f

// Task 0 ruling T0, Motorola edge 70: a one-row widget is drawn 97.7 dp tall inside the launcher's padding (read in
// Task 0 from another app's one-row widget on the same grid, and confirmed with this widget at one row in Task 5), so
// the default is one row and minHeight is 97 dp.
private const val RULED_MIN_HEIGHT_DP = 97f
private const val RULED_TARGET_CELL_HEIGHT = 1

// Ruling T0c, observed on that launcher: with minResizeHeight 97 dp the user could not resize the widget to one row;
// with 70 dp they could. (Its DeviceProfile reports a 90 dp cellHeight while it draws a 113.7 dp row.) 70 dp = the
// 69 dp artwork box + 1 dp; below the nominal content the text gives way (ruling 3).
private const val RULED_MIN_RESIZE_HEIGHT_DP = 70f

// AppWidgetProviderInfo.RESIZE_HORIZONTAL | RESIZE_VERTICAL.
private const val RESIZE_HORIZONTAL_AND_VERTICAL = 3

/**
 * The widget's provider metadata (`music_widget_info.xml`, Phase 6c ruling 2 and Task 0) and the receiver's label
 * (ruling 7).
 */
@RunWith(AndroidJUnit4::class)
class MusicWidgetInfoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Mutation: keep 180 dp minResizeWidth → 180 ≠ 260 → RED.
    @Test
    fun minimumWidthFitsFiveTouchTargets() {
        val info = readProviderInfo()
        assertEquals("minWidth (dp)", widgetMinWidth().value, info.minWidthDp, DP_TOLERANCE)
        assertEquals("minResizeWidth (dp)", widgetMinWidth().value, info.minResizeWidthDp, DP_TOLERANCE)
    }

    // Mutation: minResizeHeight back to 97 dp (the pre-T0c value) → 97 ≠ 70 → RED.
    @Test
    fun heightsFollowTheDeviceProbe() {
        val info = readProviderInfo()
        assertEquals("minHeight (dp)", RULED_MIN_HEIGHT_DP, info.minHeightDp, DP_TOLERANCE)
        assertEquals("minResizeHeight (dp)", RULED_MIN_RESIZE_HEIGHT_DP, info.minResizeHeightDp, DP_TOLERANCE)
        assertEquals("targetCellHeight", RULED_TARGET_CELL_HEIGHT, info.targetCellHeight)
        assertEquals("updatePeriodMillis (push-driven, never polled)", 0, info.updatePeriodMillis)
        assertEquals("resizeMode is horizontal|vertical", RESIZE_HORIZONTAL_AND_VERTICAL, info.resizeMode)
    }

    // Mutation: the literal android:label="RollaMusicPlayer" → RED.
    @Test
    fun labelIsRollaMusic() {
        val pm = context.packageManager
        val receiver = pm.getReceiverInfo(ComponentName(context, MusicWidgetReceiver::class.java), 0)
        assertEquals("receiver label", "Rolla Music", receiver.loadLabel(pm).toString())
    }

    private data class ProviderInfo(
        val minWidthDp: Float,
        val minHeightDp: Float,
        val minResizeWidthDp: Float,
        val minResizeHeightDp: Float,
        val targetCellHeight: Int,
        val updatePeriodMillis: Int,
        val resizeMode: Int,
    )

    private fun readProviderInfo(): ProviderInfo {
        val parser = context.resources.getXml(R.xml.music_widget_info)
        var event = parser.next()
        // Skip to the <appwidget-provider> element; a document with no element ends the loop instead of spinning.
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
        assertEquals("the provider XML has a start tag", XmlPullParser.START_TAG, event)
        assertEquals("root element", "appwidget-provider", parser.name)
        val values = context.obtainStyledAttributes(Xml.asAttributeSet(parser), PROVIDER_ATTRS)
        val density = context.resources.displayMetrics.density
        try {
            fun index(attr: Int) = PROVIDER_ATTRS.indexOf(attr)
            fun dp(attr: Int) = values.getDimension(index(attr), -1f) / density
            fun int(attr: Int) = values.getInt(index(attr), -1)
            return ProviderInfo(
                minWidthDp = dp(android.R.attr.minWidth),
                minHeightDp = dp(android.R.attr.minHeight),
                minResizeWidthDp = dp(android.R.attr.minResizeWidth),
                minResizeHeightDp = dp(android.R.attr.minResizeHeight),
                targetCellHeight = int(android.R.attr.targetCellHeight),
                updatePeriodMillis = int(android.R.attr.updatePeriodMillis),
                resizeMode = int(android.R.attr.resizeMode),
            )
        } finally {
            values.recycle()
            parser.close()
        }
    }

    private companion object {
        // The provider attributes read from music_widget_info.xml, sorted: obtainStyledAttributes needs ascending
        // ids. targetCellHeight is an API 31 attribute; it is only read on Robolectric's SDK 34.
        @SuppressLint("InlinedApi")
        val PROVIDER_ATTRS: IntArray = intArrayOf(
            android.R.attr.minWidth,
            android.R.attr.minHeight,
            android.R.attr.minResizeWidth,
            android.R.attr.minResizeHeight,
            android.R.attr.targetCellHeight,
            android.R.attr.updatePeriodMillis,
            android.R.attr.resizeMode,
        ).sortedArray()
    }
}
