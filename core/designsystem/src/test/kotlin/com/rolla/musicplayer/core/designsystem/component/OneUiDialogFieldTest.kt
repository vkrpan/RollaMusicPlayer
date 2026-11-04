package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.dialogFieldContainer
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawLatestDialog
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** [OneUiDialogField]: the name field of the playlist and preset dialogs (Phase 7 F2 and F3). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiDialogFieldTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // F3: the field takes focus when the dialog shows, so the keyboard opens without a tap. Robolectric's dialog
    // window is not in touch mode, so Compose gives the FIRST focusable default focus there (a phone gives none); the
    // plain field above is that decoy, so the named field is focused only if it asks. Control: the node exists and is
    // the second field (fails under a stub that renders nothing). Mutation: drop the requestFocus effect → the decoy
    // keeps focus → RED.
    // A dialog hosting a text field never idles under the PAUSED looper (known Robolectric hang).
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun theFieldIsFocusedWhenTheDialogShows() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDialog(
                    onDismissRequest = {},
                    title = "New playlist",
                    confirmButton = { OneUiTextButton(text = "Create", onClick = {}) },
                    text = {
                        Column {
                            OneUiTextField(value = "", onValueChange = {}, label = "Plain")
                            OneUiDialogField(value = "", onValueChange = {}, label = "Playlist name")
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("Playlist name")).assertIsFocused()
    }

    // F2: in light the field is dialogFieldContainer (#FFFFFF, 1.22:1 on the #E8E8EA dialog) instead of
    // surfaceContainerHighest (#EBEBED, 1.02:1). The probe sits 4 dp inside the field's start edge, level with its
    // centre, left of the label. Control: in dark the same probe reads the dark token (#333333; it sits within the
    // pixel tolerance of the dark dialog, so only light checks the separation), and in light the probe is not the
    // dialog colour. Mutation: containerColor = surfaceContainerHighest → the light probe reads #EBEBED → RED.
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun theFieldUsesTheDialogFieldContainerInBothThemes() {
        var dark by mutableStateOf(true)
        var expected = Color.Unspecified
        var dialog = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = dark) {
                expected = MaterialTheme.colorScheme.dialogFieldContainer
                dialog = MaterialTheme.colorScheme.surfaceContainerHigh
                NameDialog()
            }
        }
        listOf(true, false).forEach { theme ->
            composeRule.runOnIdle { dark = theme }
            composeRule.waitForIdle()
            val field = composeRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
            val middle = (field.top + field.bottom) / 2
            val probe = composeRule.pixelsIn(
                composeRule.drawLatestDialog(),
                DpRect(field.left + 3.dp, middle - 1.dp, field.left + 5.dp, middle + 1.dp),
            )
            val pixel = probe[probe.size / 2]
            assertColorNear(expected, pixel)
            if (!theme) assertFalse("light: the field must not vanish into the dialog", isColorNear(dialog, pixel))
        }
    }

    // Ruling P37 (final review I1): a field that opens with a name (Rename) is not focused on show, because a focused
    // String field starts its cursor before the name and the first key would land in front of it; an empty field
    // (New playlist) still is (F3, the test above). Control: the rename field exists with its name (fails under a stub
    // that renders nothing). Mutation: focus on show regardless of the initial value → the rename field is focused
    // → RED.
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun aFieldThatOpensWithANameIsNotFocusedOnShow() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDialog(
                    onDismissRequest = {},
                    title = "Rename playlist",
                    confirmButton = { OneUiTextButton(text = "Rename", onClick = {}) },
                    text = {
                        // Robolectric's dialog gives the first focusable default focus (see the F3 test): the decoy
                        // takes it, so the name field is focused only if it asks.
                        Column {
                            OneUiTextField(value = "", onValueChange = {}, label = "Plain")
                            OneUiDialogField(value = "Workout Mix", onValueChange = {}, label = "Playlist name")
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasText("Workout Mix")).assertIsNotFocused()
    }

    @Composable
    private fun NameDialog() {
        OneUiDialog(
            onDismissRequest = {},
            title = "New playlist",
            confirmButton = { OneUiTextButton(text = "Create", onClick = {}) },
            text = { OneUiDialogField(value = "", onValueChange = {}, label = "Playlist name") },
        )
    }
}
