package com.rolla.musicplayer.feature.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.ThemeMode
import com.rolla.musicplayer.core.testing.drawLatestDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [ThemeModeDialog] against spec §8.7: the `large` shape. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeModeDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The production theme gives `large` and `extraLarge` the same 26 dp radius, so dropping `shape` (AlertDialog's
    // default is extraLarge) changes no pixel there: an equivalent mutant under RollaShapes. This theme separates the
    // two (large square, extraLarge round), so the dialog's corner pixel shows which one it reads.
    // Mutation: drop `shape = MaterialTheme.shapes.large` → the extraLarge round corner leaves the pixel clear → RED.
    @Test
    fun dialogUsesTheLargeShape() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes.copy(
                        large = RoundedCornerShape(0.dp),
                        extraLarge = RoundedCornerShape(SEPARATING_RADIUS),
                    ),
                ) {
                    ThemeModeDialog(selected = ThemeMode.SYSTEM, onSelect = {}, onDismiss = {})
                }
            }
        }
        assertEquals("alpha of the dialog's top-left corner pixel", OPAQUE, dialogCornerAlpha())
    }

    /** Draws the dialog window's Compose view and reads the alpha just inside the dialog pane's top-left corner. */
    private fun dialogCornerAlpha(): Int {
        composeRule.waitForIdle()
        val pane = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .getBoundsInRoot()
        val bitmap = composeRule.drawLatestDialog()
        val (x, y) = with(composeRule.density) { (pane.left.roundToPx() + 1) to (pane.top.roundToPx() + 1) }
        return bitmap.getPixel(x, y) ushr ALPHA_SHIFT
    }

    private companion object {
        val SEPARATING_RADIUS = 40.dp
        const val OPAQUE = 0xFF
        const val ALPHA_SHIFT = 24
    }
}
