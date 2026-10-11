package com.rolla.musicplayer.feature.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The One UI settings rows (spec §8.6), hosted through the production [SettingsSectionHeader] and [SettingsSectionCard]
 * stacked the way [SettingsScreen] stacks them. The screen's own copy and wiring live in SettingsScreenTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Mutation: render the value trailing (old layout) → value.top < label.bottom → RED (spec §8.6).
    @Test
    fun valueSitsBelowItsLabelAlignedToIt() {
        setRows { SettingsValueNavRow(label = "Theme", value = "System default", onClick = {}) }
        val label = bounds("Theme")
        val value = bounds("System default")
        assertTrue("value top ${value.top} is above label bottom ${label.bottom}", value.top >= label.bottom)
        assertEquals(label.left.value, value.left.value, 0.5f)
    }

    // Mutation: give OneUiSwitch the onCheckedChange too → two toggleable nodes → RED.
    @Test
    fun toggleRowIsOneSwitchTarget() {
        var checked = false
        setRows { SettingsToggleRow(label = "Skip silence", checked = checked, onCheckedChange = { checked = it }) }
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNodeWithText("Skip silence").performClick()
        assertTrue(checked)
    }

    // Mutations: commit inside onValueChange → several commits per drag → RED; commit the incoming `value` instead of
    // the dragged one (onValueChangeFinished(value)) → the old 1.0 speed is committed → RED; delete
    // `pendingValue = dragValue` → the label falls back to the host's unchanged 1.0x after release → RED.
    @Test
    fun sliderRowCommitsTheDraggedValueOnceAndShowsItOptimistically() {
        val commits = mutableListOf<Float>()
        setRows {
            SettingsSliderRow(
                label = "Playback speed",
                value = 1f, // the host never echoes the commit back, as a slow DataStore round-trip wouldn't yet
                valueRange = 0.5f..2f,
                valueLabel = { "%.1fx".format(it) },
                onValueChangeFinished = { commits += it },
            )
        }
        // Starts at the centre: the slider node's reported bounds are wider than its real track under NATIVE (the
        // graphicsLayer pairing), so swipeRight()'s default centerLeft start lands outside the slider and never drags.
        composeRule.onNodeWithContentDescription("Playback speed").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        assertEquals("one commit per drag", 1, commits.size)
        val committed = commits.single()
        assertTrue("committed $committed is not above the starting 1.0", committed > 1f)
        composeRule.onNodeWithText("%.1fx".format(committed), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("1.0x", useUnmergedTree = true).assertDoesNotExist()
    }

    // Mutations: drop the rows' vertical padding → the slider label sits on the card's top edge → RED; drop the
    // heightIn(singleLineRowHeight) floor → a single-line row is 54 dp → RED.
    @Test
    fun rowsKeepTheirVerticalPaddingAndSingleLineHeight() {
        setRows(cardTag = CARD_TAG) {
            SettingsSliderRow(
                label = "Playback speed",
                value = 1f,
                valueRange = 0.5f..2f,
                valueLabel = { "1.0x" },
                onValueChangeFinished = {},
            )
            Box(Modifier.testTag(ROW_HOST_TAG)) { SettingsValueNavRow(label = "About", onClick = {}) }
        }
        val card = composeRule.onNodeWithTag(CARD_TAG).getBoundsInRoot()
        (bounds("Playback speed").top - card.top)
            .assertIsEqualTo(RollaDimens.settingsRowVerticalPadding, "slider label inset from the card top")
        val host = composeRule.onNodeWithTag(ROW_HOST_TAG).getBoundsInRoot()
        assertTrue("single-line row is ${host.height}", host.height >= RollaDimens.singleLineRowHeight - 0.5.dp)
    }

    // Mutation: header padded by a literal 16 dp instead of settingsCardInset + settingsRowPadding → RED.
    @Test
    fun sectionHeaderAlignsWithRowText() {
        setRows(withHeader = true) { SettingsValueNavRow(label = "About", onClick = {}) }
        val inset = RollaDimens.settingsCardInset + RollaDimens.settingsRowPadding
        bounds("Playback").left.assertIsEqualTo(inset, "header start", tolerance = 0.5.dp)
        bounds("About").left.assertIsEqualTo(inset, "row text start", tolerance = 0.5.dp)
    }

    // Mutations: tint the value row's value with primary → RED; tint the slider's value label with primary → RED.
    // Dark accentText (#6094FF) and primary (#2F6FF0) differ, which the first assertion pins.
    @Test
    fun valuesAreTintedAccentText() {
        var accent = Color.Unspecified
        var primary = Color.Unspecified
        setRows {
            accent = MaterialTheme.colorScheme.accentText
            primary = MaterialTheme.colorScheme.primary
            SettingsValueNavRow(label = "Theme", value = "System default", onClick = {})
            SettingsSliderRow(
                label = "Playback speed",
                value = 1f,
                valueRange = 0.5f..2f,
                valueLabel = { "1.0x" },
                onValueChangeFinished = {},
            )
        }
        assertNotEquals(primary, accent)
        assertEquals(accent, textColor("System default"))
        assertEquals(accent, textColor("1.0x"))
    }

    // Mutation: drop SettingsRowDivider's endInset (or its startInset) → the hairline reaches that card edge → RED.
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi") // 3 px/dp, so the 1 dp hairline is 3 px and its middle row is solid
    fun dividerIsInsetFromBothCardEdges() {
        var container = Color.Unspecified
        var hairline = Color.Unspecified
        setRows {
            container = MaterialTheme.colorScheme.surfaceContainer
            hairline = MaterialTheme.colorScheme.outlineVariant
            SettingsValueNavRow(label = "Above", onClick = {})
            SettingsRowDivider()
            SettingsValueNavRow(label = "Below", onClick = {})
        }
        val row = composeRule.onNodeWithText("Above").fetchSemanticsNode().boundsInRoot // the full-width row, px
        val bitmap = drawRoot()
        val y = row.bottom.toInt() + 1
        val inset = with(composeRule.density) { RollaDimens.settingsDividerInset.toPx() }
        assertColorNear(container, bitmap.getPixel((row.left + inset - 2).toInt(), y))
        assertColorNear(hairline, bitmap.getPixel((row.left + inset + 1).toInt(), y))
        assertColorNear(hairline, bitmap.getPixel((row.right - inset - 2).toInt(), y))
        assertColorNear(container, bitmap.getPixel((row.right - inset + 1).toInt(), y))
    }

    private fun setRows(
        withHeader: Boolean = false,
        cardTag: String? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column {
                    if (withHeader) SettingsSectionHeader(title = "Playback")
                    SettingsSectionCard(modifier = cardTag?.let { Modifier.testTag(it) } ?: Modifier) { content() }
                }
            }
        }
    }

    // Unmerged: a row merges its texts, so the merged tree would answer every text with the whole row's bounds.
    private fun bounds(text: String): DpRect =
        composeRule.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()

    private fun textColor(text: String): Color = composeRule.textColorOf(text)

    // captureToImage() times out under Robolectric; a software draw of the content view runs the Compose draw phase.
    private fun drawRoot(): Bitmap {
        composeRule.waitForIdle()
        lateinit var bitmap: Bitmap
        composeRule.runOnUiThread {
            val view = composeRule.activity.findViewById<View>(android.R.id.content)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun assertColorNear(expected: Color, actual: Int) {
        val want = expected.toArgb()
        val close = listOf(24, 16, 8, 0).all { shift ->
            kotlin.math.abs((want shr shift and 0xFF) - (actual shr shift and 0xFF)) <= CHANNEL_TOLERANCE
        }
        assertTrue("expected ${Integer.toHexString(want)}, was ${Integer.toHexString(actual)}", close)
    }

    private companion object {
        const val CHANNEL_TOLERANCE = 6 // anti-aliasing puts a pixel within a few levels of its token
        const val CARD_TAG = "card"
        const val ROW_HOST_TAG = "rowHost"
    }
}
