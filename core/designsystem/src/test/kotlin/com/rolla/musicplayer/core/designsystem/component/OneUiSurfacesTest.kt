package com.rolla.musicplayer.core.designsystem.component

import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawLatestDialog
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiSurfacesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Spec §8.7 sheets: the title is a heading. Control: the content renders. Mutation: drop the title Text → RED.
    @Test
    fun bottomSheetShowsItsTitleAsAHeadingAboveItsContent() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiBottomSheet(onDismissRequest = {}, title = "Add to playlist") { Text("Row content") }
            }
        }
        composeRule.onNodeWithText("Row content").assertExists()
        composeRule.onNode(hasText("Add to playlist") and isHeading()).assertExists()
    }

    // Review Focus / 0017: the sheet's container is drawn in surfaceContainerHigh. The probe samples the sheet window
    // under the content. Control: the same probe in light theme gives the light token. Mutation: containerColor =
    // surfaceContainer → RED.
    @Test
    fun bottomSheetContainerIsSurfaceContainerHighInBothThemes() {
        // setContent may run once per test, so one composition switches theme between the two probes.
        var dark by mutableStateOf(true)
        var expected = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = dark) {
                expected = MaterialTheme.colorScheme.surfaceContainerHigh
                OneUiBottomSheet(onDismissRequest = {}) {
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("sheet-body"))
                }
            }
        }
        listOf(true, false).forEach { theme ->
            composeRule.runOnIdle { dark = theme }
            composeRule.waitForIdle()
            val bitmap = composeRule.drawLatestDialog()
            val body = composeRule.onNodeWithTag("sheet-body").getUnclippedBoundsInRoot()
            assertColorNear(expected, composeRule.pixelsIn(bitmap, body).let { it[it.size / 2] })
        }
    }

    // Spec §5.1: blue text is accentText, never primary. Destructive is destructiveText (ruling 3 as amended: never
    // error as text). Control: both buttons render their label. Mutation: drop the colours (stock TextButton →
    // primary) → the accent probe fails → RED.
    @Test
    fun textButtonsDrawAccentTextAndDestructiveError() {
        var accent = Color.Unspecified
        var destructive = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                accent = MaterialTheme.colorScheme.accentText
                destructive = MaterialTheme.colorScheme.destructiveText
                Column {
                    OneUiTextButton(text = "MMMMMM", onClick = {}, modifier = Modifier.testTag("accent"))
                    OneUiTextButton(
                        text = "WWWWWW",
                        onClick = {},
                        destructive = true,
                        modifier = Modifier.testTag("danger"),
                    )
                }
            }
        }
        val bitmap = composeRule.drawRoot()
        val accentInk = composeRule.pixelsIn(bitmap, composeRule.onNodeWithTag("accent").getUnclippedBoundsInRoot())
        val dangerInk = composeRule.pixelsIn(bitmap, composeRule.onNodeWithTag("danger").getUnclippedBoundsInRoot())
        assertTrue("accent glyphs drawn", accentInk.any { isColorNear(accent, it) })
        assertTrue("destructive glyphs drawn", dangerInk.any { isColorNear(destructive, it) })
        assertFalse("destructive must not use the accent", dangerInk.any { isColorNear(accent, it) })
    }

    // Ruling 3 as amended: destructive text and icons are destructiveText, never error. Dark error (#FF5A5A) sits 6
    // levels from dark destructiveText (#FF6060), exactly PIXEL_CHANNEL_TOLERANCE, and light uses one value for
    // both, so the real theme cannot separate them. This fixture repaints error far from every other colour on
    // screen. Control: the destructiveText ink is drawn (fails under "never render"). Mutation: destructiveText →
    // colorScheme.error in OneUiTextButton or SheetActionRow → the separator colour appears → RED.
    @Test
    fun destructiveControlsDrawDestructiveTextNotError() {
        var destructive = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(error = ERROR_SEPARATOR)) {
                    destructive = MaterialTheme.colorScheme.destructiveText
                    DestructiveButtonAndRow()
                }
            }
        }
        val bitmap = composeRule.drawRoot()
        listOf("button", "row").forEach { tag ->
            val ink = composeRule.pixelsIn(bitmap, composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot())
            assertTrue("$tag: destructiveText drawn", ink.any { isColorNear(destructive, it) })
            assertFalse("$tag: must not draw error", ink.any { isColorNear(ERROR_SEPARATOR, it) })
        }
    }

    // The dialog draws its container in surfaceContainerHigh and shows its title. Control: the confirm button exists.
    // Mutation: containerColor = surfaceContainer → RED.
    @Test
    fun dialogUsesSurfaceContainerHighAndShowsTitleAndButtons() {
        var expected = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                expected = MaterialTheme.colorScheme.surfaceContainerHigh
                OneUiDialog(
                    onDismissRequest = {},
                    title = "Delete playlist?",
                    confirmButton = { OneUiTextButton(text = "Delete", onClick = {}, destructive = true) },
                    dismissButton = { OneUiTextButton(text = "Cancel", onClick = {}) },
                )
            }
        }
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithText("Delete playlist?").assertExists()
        val bitmap = composeRule.drawLatestDialog()
        val title = composeRule.onNodeWithText("Delete playlist?").getUnclippedBoundsInRoot()
        // A pixel just left of the title, inside the dialog's padding, is container, not text.
        val probe = composeRule.pixelsIn(bitmap, DpRect(title.left - 8.dp, title.top, title.left - 4.dp, title.bottom))
        assertColorNear(expected, probe[probe.size / 2])
    }

    // The menu shows its items in songTitle and calls onClick. Control: the menu is closed before expanded = true.
    // Mutation: OneUiMenuItem ignores onClick → clicks stays 0 → RED.
    @Test
    fun dropdownMenuItemClicks() {
        var expanded by mutableStateOf(false)
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    OneUiMenuItem(text = "Settings", onClick = { clicks++ })
                }
            }
        }
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        composeRule.runOnIdle { expanded = true }
        composeRule.onNodeWithText("Settings").performClick()
        assertEquals(1, clicks)
    }

    // SheetActionRow: singleLineRowHeight tall, label starts at listTextStart, destructive tints destructiveText.
    // Control: the normal row's label is onSurface. Mutations: (a) height = listRowHeight → RED; (b) destructive
    // ignored → RED.
    @Test
    fun sheetActionRowGeometryAndDestructiveTint() {
        var onSurface = Color.Unspecified
        var destructive = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                onSurface = MaterialTheme.colorScheme.onSurface
                destructive = MaterialTheme.colorScheme.destructiveText
                Column {
                    SheetActionRow(RollaIcons.Edit, "MMMMMM", onClick = {}, modifier = Modifier.testTag("plain"))
                    SheetActionRow(
                        RollaIcons.Delete,
                        "WWWWWW",
                        onClick = {},
                        destructive = true,
                        modifier = Modifier.testTag("danger"),
                    )
                }
            }
        }
        val plain = composeRule.onNodeWithTag("plain").getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.singleLineRowHeight.value, (plain.bottom - plain.top).value, 0.5f)
        // The row's clickable merges its Text into the row node; the unmerged tree finds the Text itself.
        val label = composeRule.onNodeWithText("MMMMMM", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.listTextStart.value, label.left.value, 1f)
        val bitmap = composeRule.drawRoot()
        assertTrue(composeRule.pixelsIn(bitmap, label).any { isColorNear(onSurface, it) })
        val dangerLabel = composeRule.onNodeWithText("WWWWWW", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(composeRule.pixelsIn(bitmap, dangerLabel).any { isColorNear(destructive, it) })
    }

    // OneUiTextField: no indicator line, and the container is the passed colour. Control: the label renders.
    // Mutation: keep the default TextField indicator → a 1–2 dp line in onSurfaceVariant at the field bottom → RED.
    @Test
    fun textFieldHasNoIndicatorLineAndUsesItsContainer() {
        var container = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                container = MaterialTheme.colorScheme.surfaceContainerHighest
                OneUiTextField(
                    value = "",
                    onValueChange = {},
                    label = "Playlist name",
                    containerColor = container,
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        composeRule.onNodeWithText("Playlist name").assertExists()
        val bitmap = composeRule.drawRoot()
        val field = composeRule.onNodeWithTag("field").getUnclippedBoundsInRoot()
        // The 1 dp unfocused indicator fills the field's last dp row (a stroke centred at height - 0.5 dp), so the
        // probe runs to field.bottom; a probe ending at bottom - 1 dp stops one row above the line.
        val bottomRow = DpRect(field.left + 24.dp, field.bottom - 2.dp, field.right - 24.dp, field.bottom)
        assertTrue("no indicator line", composeRule.pixelsIn(bitmap, bottomRow).all { isColorNear(container, it) })
    }

    // The field draws the container it is passed. textFieldHasNoIndicatorLineAndUsesItsContainer passes
    // surfaceContainerHighest, which is also M3's default filled container, and every real container token sits within
    // PIXEL_CHANNEL_TOLERANCE of it, so this fixture passes a separator colour. Control: the label renders. Mutation:
    // drop the three containerColor arguments → the default container is drawn → RED.
    @Test
    fun textFieldDrawsThePassedContainerNotTheDefault() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiTextField(
                    value = "",
                    onValueChange = {},
                    label = "Playlist name",
                    containerColor = CONTAINER_SEPARATOR,
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        composeRule.onNodeWithText("Playlist name").assertExists()
        val bitmap = composeRule.drawRoot()
        val field = composeRule.onNodeWithTag("field").getUnclippedBoundsInRoot()
        val bottomRow = DpRect(field.left + 24.dp, field.bottom - 2.dp, field.right - 24.dp, field.bottom)
        val probe = composeRule.pixelsIn(bitmap, bottomRow)
        assertTrue("passed container", probe.all { isColorNear(CONTAINER_SEPARATOR, it) })
    }

    // Fix F1 (3-button navigation): the sheet's edge-to-edge window keeps the platform's navigation-bar contrast scrim
    // (enforceNavigationBarContrast defaults to true), which DecorView paints over the sheet's own scrim and surface as
    // an undimmed white or black strip. PhoneWindow reads that attribute only when the app targets API 29+, and a
    // library's test manifest targets minSdk, so the fixture raises the target to the app's (34) first; without it
    // the window never enforces contrast and the test is blind. Control: the sheet's content exists. Mutation: drop
    // the isNavigationBarContrastEnforced = false line → RED.
    @Test
    fun bottomSheetWindowDropsTheNavigationBarContrastScrim() {
        val applicationInfo = composeRule.activity.applicationInfo
        val previousTargetSdk = applicationInfo.targetSdkVersion
        applicationInfo.targetSdkVersion = APP_TARGET_SDK
        try {
            composeRule.setContent {
                RollaMusicPlayerTheme(darkTheme = true) {
                    OneUiBottomSheet(onDismissRequest = {}) { Text("Row content") }
                }
            }
            composeRule.onNodeWithText("Row content").assertExists()
            val window = composeRule.latestDialogWindow()
            assertFalse(
                "the sheet window must not enforce navigation-bar contrast (it paints a strip over the sheet)",
                window.isNavigationBarContrastEnforced,
            )
        } finally {
            applicationInfo.targetSdkVersion = previousTargetSdk
        }
    }

    // Fix F1: with the contrast scrim gone the navigation buttons sit on the sheet, so their appearance follows the
    // app's resolved theme, not the system's, and so do the status-bar icons (M3 1.3.0 sets both from
    // isSystemInDarkTheme(); Robolectric's system is light). One composition flips the theme. Control: dark theme gives
    // light (white) icons, which fails under "never set" (M3 leaves dark icons). Mutations: drop the
    // isAppearanceLightNavigationBars line → RED; drop the isAppearanceLightStatusBars line → RED.
    @Test
    fun bottomSheetSystemBarIconsFollowTheAppTheme() {
        var dark by mutableStateOf(true)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = dark) {
                OneUiBottomSheet(onDismissRequest = {}) { Text("Row content") }
            }
        }
        composeRule.onNodeWithText("Row content").assertExists()
        listOf(true, false).forEach { theme ->
            composeRule.runOnIdle { dark = theme }
            composeRule.waitForIdle()
            val window = composeRule.latestDialogWindow()
            val (lightButtons, lightStatusIcons) = composeRule.runOnIdle {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightNavigationBars to controller.isAppearanceLightStatusBars
            }
            assertEquals("dark=$theme: navigation-bar buttons must contrast with the sheet", !theme, lightButtons)
            assertEquals("dark=$theme: status-bar icons must follow the app theme", !theme, lightStatusIcons)
        }
    }

    private fun ComposeTestRule.latestDialogWindow(): Window =
        runOnIdle { checkNotNull(ShadowDialog.getLatestDialog()?.window) { "no sheet window was shown" } }

    /** A destructive text button tagged "button" over a destructive sheet row tagged "row". */
    @Composable
    private fun DestructiveButtonAndRow() {
        Column {
            OneUiTextButton(text = "WWWWWW", onClick = {}, destructive = true, modifier = Modifier.testTag("button"))
            SheetActionRow(
                RollaIcons.Delete,
                "WWWWWW",
                onClick = {},
                destructive = true,
                modifier = Modifier.testTag("row"),
            )
        }
    }

    private companion object {
        /** The app's targetSdk (see RollaAndroidApplicationPlugin); PhoneWindow honours the contrast flag from 29. */
        const val APP_TARGET_SDK = 34

        /** A test-only text-field container, far from every container token. */
        val CONTAINER_SEPARATOR = Color(0xFF0000FF)

        /** A test-only stand-in for colorScheme.error, far from every token on a dark screen. */
        val ERROR_SEPARATOR = Color(0xFF00FF00)
    }
}
