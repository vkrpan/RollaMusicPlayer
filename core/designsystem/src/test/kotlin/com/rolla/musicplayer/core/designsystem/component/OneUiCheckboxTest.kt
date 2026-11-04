package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [OneUiCheckbox] (Phase 7, 6b ruling P4): round, primary when checked, an onSurfaceVariant ring when unchecked. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiCheckboxTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Probes on a surfaceContainer panel (dark), each a 1 dp square inside the 20 dp box:
    // - checked, 3 dp below the top centre: the primary disc (the check mark sits in the middle);
    // - checked, 1 dp in from the top-left corner: outside a circle, so the panel shows (a square would be primary);
    // - unchecked, 1 dp below the top centre: the 2 dp ring, onSurfaceVariant (not outline, 2.60:1 on the panel);
    // - unchecked, the centre: the panel (the ring is hollow).
    // Control: the checked disc probe is primary (fails under a stub that draws nothing). Mutations: a square shape →
    // the corner probe is primary → RED; the ring in outline → RED; a filled unchecked circle → the centre probe → RED.
    @Test
    fun checkedIsAPrimaryDiscAndUncheckedAnOnSurfaceVariantRing() {
        var panel = Color.Unspecified
        var primary = Color.Unspecified
        var ring = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                panel = MaterialTheme.colorScheme.surfaceContainer
                primary = MaterialTheme.colorScheme.primary
                ring = MaterialTheme.colorScheme.onSurfaceVariant
                Row(Modifier.background(panel).padding(16.dp)) {
                    OneUiCheckbox(checked = true, onCheckedChange = null, modifier = Modifier.testTag("on"))
                    val off = Modifier.padding(start = 16.dp).testTag("off")
                    OneUiCheckbox(checked = false, onCheckedChange = null, modifier = off)
                }
            }
        }
        val bitmap = composeRule.drawRoot()
        val on = composeRule.onNodeWithTag("on").getUnclippedBoundsInRoot()
        val off = composeRule.onNodeWithTag("off").getUnclippedBoundsInRoot()
        assertEquals20dp(on)
        fun probe(x: Dp, y: Dp): Int = composeRule.pixelsIn(bitmap, DpRect(x, y, x + 1.dp, y + 1.dp)).first()
        val onCentre = (on.left + on.right) / 2
        val offCentre = (off.left + off.right) / 2
        assertColorNear(primary, probe(onCentre - 0.5.dp, on.top + 3.dp))
        assertColorNear(panel, probe(on.left + 1.dp, on.top + 1.dp))
        assertColorNear(ring, probe(offCentre - 0.5.dp, off.top + 0.5.dp))
        assertColorNear(panel, probe(offCentre - 0.5.dp, (off.top + off.bottom) / 2 - 0.5.dp))
    }

    // With a handler it is a standalone control: Role.Checkbox, its toggle state, a 48 dp target, and a click flips it.
    // Without one (the row owns the click, SongListItem) it is purely visual: no role and the bare 20 dp box.
    // Control: the click flips the handled box from off to on (fails under an inert stub). Mutation: drop the
    // toggleable → no role and no flip → RED; always apply the minimum size → the visual box is 48 dp → RED.
    @Test
    fun aHandledCheckboxIsAToggleableCheckboxAndAnUnhandledOneIsVisualOnly() {
        var checked by mutableStateOf(false)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Row {
                    // The node's own bounds are only its drawn box (the toggleable sits inside the minimum size), so
                    // the footprint is read from a wrap-content host, as SwitchAndChipTest does for the switch.
                    Box(Modifier.testTag("handled-host")) {
                        OneUiCheckbox(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = Modifier.testTag("handled"),
                        )
                    }
                    OneUiCheckbox(checked = true, onCheckedChange = null, modifier = Modifier.testTag("visual"))
                }
            }
        }
        val handled = composeRule.onNodeWithTag("handled")
        handled.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOff()
        val bounds = composeRule.onNodeWithTag("handled-host").getUnclippedBoundsInRoot()
        assertTrue("handled target ≥ 48 dp", bounds.right - bounds.left >= RollaDimens.minTouchTarget)
        handled.performClick()
        handled.assertIsOn()
        composeRule.onNodeWithTag("visual")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        assertEquals20dp(composeRule.onNodeWithTag("visual").getUnclippedBoundsInRoot())
    }

    private fun assertEquals20dp(bounds: DpRect) {
        val width = bounds.right - bounds.left
        assertTrue("box width $width is the 20 dp checkbox", (width - RollaDimens.checkboxSize).value in -0.5f..0.5f)
    }
}
