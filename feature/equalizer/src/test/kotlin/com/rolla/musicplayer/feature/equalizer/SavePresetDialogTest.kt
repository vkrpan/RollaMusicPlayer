package com.rolla.musicplayer.feature.equalizer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.testing.drawLatestDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** [SavePresetDialog] against spec §8.7: `large` shape and accentText for the field's blue text. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavePresetDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    // A dialog hosting a text field never idles under the PAUSED looper (known Robolectric hang).
    // Mutation: drop focusedLabelColor from OutlinedTextFieldDefaults.colors → the focused label is primary → RED.
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun focusedLabelIsAccentTextNotPrimary() {
        var accent = Color.Unspecified
        var primary = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                accent = MaterialTheme.colorScheme.accentText
                primary = MaterialTheme.colorScheme.primary
                SavePresetDialog(onConfirm = {}, onDismiss = {})
            }
        }
        assertNotEquals("fixture: accentText and primary must differ", accent, primary)
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(accent, labelColor())
    }

    // The production theme gives `large` and `extraLarge` the same 26 dp radius, so dropping `shape` (AlertDialog's
    // default is extraLarge) changes no pixel there: an equivalent mutant under RollaShapes. This theme separates the
    // two (large square, extraLarge round), so the dialog's corner pixel shows which one it reads.
    // Mutation: drop `shape = MaterialTheme.shapes.large` → the extraLarge round corner leaves the pixel clear → RED.
    @LooperMode(LooperMode.Mode.LEGACY)
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
                    SavePresetDialog(onConfirm = {}, onDismiss = {})
                }
            }
        }
        assertEquals("alpha of the dialog's top-left corner pixel", OPAQUE, dialogCornerAlpha())
    }

    private fun labelColor(): Color {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("Preset name", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.color
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
