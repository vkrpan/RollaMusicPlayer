package com.rolla.musicplayer.feature.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget's shape drawables round every API (below 31 Glance's cornerRadius is ignored), so their XML radii must
 * equal the tokens Glance's cornerRadius reads. The art box's radius was calibrated to 15 dp in Phase 7.
 */
@RunWith(AndroidJUnit4::class)
class WidgetDrawableRadiusTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Trivial stub (a drawable with no corners) → 0 dp → RED. Control: the card drawable reads widgetCardRadius
    // (24 dp), a different value, through the same probe. Mutation: widget_art_placeholder.xml keeps 12dp corners
    // → RED.
    @Test
    fun shapeDrawablesRoundAtTheirTokens() {
        assertEquals(
            "control: widget_card_background radius (dp)",
            RollaDimens.widgetCardRadius.value,
            radiusDp(R.drawable.widget_card_background),
            0.01f,
        )
        assertEquals(
            "widget_art_placeholder radius (dp)",
            RollaDimens.widgetArtRadius.value,
            radiusDp(R.drawable.widget_art_placeholder),
            0.01f,
        )
    }

    private fun radiusDp(@DrawableRes id: Int): Float {
        val drawable = ContextCompat.getDrawable(context, id) as GradientDrawable
        return drawable.cornerRadius / context.resources.displayMetrics.density
    }
}
