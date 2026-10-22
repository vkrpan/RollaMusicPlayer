package com.rolla.musicplayer.core.designsystem.component

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
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

/** The Phase 6b kit inputs (ruling 1): field errors, the search field, the pill button and full-height sheets. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiInputsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Ruling 1: supporting text is onSurfaceVariant, and destructiveText when isError (6a ruling P2: never error as
    // text). Dark error (#FF5A5A) sits within PIXEL_CHANNEL_TOLERANCE of dark destructiveText (#FF6060), so the
    // fixture repaints error far from every colour on screen. Trivial stub: supporting text never drawn → the first
    // assertExists fails. Mutation: the colour is always onSurfaceVariant → the error half fails.
    @Test
    fun errorSupportingTextIsDestructiveTextAndPlainTextIsGray() {
        var isError by mutableStateOf(false)
        var gray = Color.Unspecified
        var destructive = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(error = ERROR_SEPARATOR)) {
                    gray = MaterialTheme.colorScheme.onSurfaceVariant
                    destructive = MaterialTheme.colorScheme.destructiveText
                    YearFieldOnCard(isError = isError)
                }
            }
        }
        composeRule.onNodeWithText("Bad year", useUnmergedTree = true)
            .assertExists("the supporting text is drawn while the field is not in error")
        val plain = supportingInk()
        assertTrue("plain supporting text is onSurfaceVariant", plain.any { isColorNear(gray, it) })
        assertFalse("plain supporting text is not destructiveText", plain.any { isColorNear(destructive, it) })

        composeRule.runOnIdle { isError = true }
        composeRule.onNodeWithText("Bad year", useUnmergedTree = true)
            .assertExists("the supporting text is drawn while the field is in error")
        val error = supportingInk()
        assertTrue("error supporting text is destructiveText", error.any { isColorNear(destructive, it) })
        assertFalse("error supporting text must not draw error", error.any { isColorNear(ERROR_SEPARATOR, it) })
        assertFalse("error supporting text is no longer onSurfaceVariant", error.any { isColorNear(gray, it) })
    }

    // Ruling 1: the Close button shows only while the query is not empty, and calls onQueryChange(""). Trivial stub:
    // the clear button always shown → the first count fails. Mutation: drop the isNotEmpty guard → RED on the first
    // assertion.
    @Test
    fun searchFieldShowsClearOnlyWithTextAndClearEmptiesIt() {
        var query by mutableStateOf("")
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiSearchField(query = query, onQueryChange = { query = it }, placeholder = "Search tracks")
            }
        }
        assertEquals(
            "the placeholder shows while the query is empty",
            1,
            composeRule.onAllNodesWithText("Search tracks", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertEquals("no Clear search while the query is empty", 0, clearButtonCount())

        composeRule.runOnIdle { query = "abc" }
        assertEquals("Clear search shows once there is a query", 1, clearButtonCount())
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.runOnIdle { assertEquals("Clear search empties the query", "", query) }
    }

    // Ruling 1: the field is a minTouchTarget (48 dp) pill. Its corner radius is then 24 dp, so (6, 6) dp from the
    // field's top-left lies 25.46 dp from the corner centre (24, 24), outside the pill by 1.46 dp, but 14.14 dp from a
    // medium (16 dp) corner's centre, inside that shape by 1.86 dp. At xxhdpi both margins exceed 4 px. Control: the
    // centre-left probe (glyph-free, before the leading Search glyph) is the container. Trivial stub: shape medium →
    // the corner probe reads the container. Mutation: shape = MaterialTheme.shapes.medium → RED.
    @Test
    fun searchFieldIsAPill() {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                scheme = MaterialTheme.colorScheme
                EmptySearchFieldOnBackground()
            }
        }
        val bitmap = composeRule.drawRoot()
        val field = composeRule.onNodeWithTag("search").getUnclippedBoundsInRoot()
        val height = (field.bottom - field.top).value
        assertEquals("the field is minTouchTarget tall", RollaDimens.minTouchTarget.value, height, 0.5f)
        val centreLeft = bitmap.pixelAt(field.left + PROBE_INSET, (field.top + field.bottom) / 2)
        assertTrue(
            "the centre-left probe is the container (control), was ${centreLeft.hex()}",
            isColorNear(scheme.surfaceContainerHigh, centreLeft),
        )
        val corner = bitmap.pixelAt(field.left + PROBE_INSET, field.top + PROBE_INSET)
        assertTrue(
            "the corner probe lies outside the pill (CircleShape), was ${corner.hex()}",
            isColorNear(scheme.background, corner),
        )
    }

    // Ruling 1: filled is primary, tonal is surfaceContainerHigh. The probe sits inside the pill's round end, left of
    // the label (which starts chipHorizontalPadding in). Trivial stub: one colour for both → the tonal assertion fails.
    // Mutation: tonal ignored → RED after the flip.
    @Test
    fun filledAndTonalPillsUseTheirContainers() {
        var tonal by mutableStateOf(false)
        var primary = Color.Unspecified
        var high = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                high = MaterialTheme.colorScheme.surfaceContainerHigh
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    OneUiPillButton(text = "Save", onClick = {}, tonal = tonal, modifier = Modifier.testTag("pill"))
                }
            }
        }
        val filled = pillProbe()
        assertTrue("filled pill container is primary, was ${filled.hex()}", isColorNear(primary, filled))

        composeRule.runOnIdle { tonal = true }
        val tonalProbe = pillProbe()
        assertTrue(
            "tonal pill container is surfaceContainerHigh, was ${tonalProbe.hex()}",
            isColorNear(high, tonalProbe),
        )
    }

    // T1 M4: the filled label is onPrimary, the tonal label onSurface. The probe is every pixel inside the label's
    // bounds, whose glyph cores carry the label ink. Light theme on purpose: dark onPrimary (#FFFFFF) and onSurface
    // (#FCFCFE) lie within the pixel tolerance of each other, so in dark a swap of the two is invisible; light
    // onPrimary (#FFFFFF) and onSurface (#111113) are far apart. Trivial stub: one label colour for both → a tonal
    // half fails. Mutation: the filled label drawn in onSurface → RED on the filled half.
    @Test
    fun filledAndTonalPillLabelsUseTheirInk() {
        var tonal by mutableStateOf(false)
        var onPrimary = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = false) {
                onPrimary = MaterialTheme.colorScheme.onPrimary
                onSurface = MaterialTheme.colorScheme.onSurface
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    OneUiPillButton(text = "Save", onClick = {}, tonal = tonal)
                }
            }
        }
        val filledInk = labelInk()
        assertTrue("the filled pill's label is drawn in onPrimary", filledInk.any { isColorNear(onPrimary, it) })
        assertFalse("the filled pill's label is not onSurface", filledInk.any { isColorNear(onSurface, it) })

        composeRule.runOnIdle { tonal = true }
        val tonalInk = labelInk()
        assertTrue("the tonal pill's label is drawn in onSurface", tonalInk.any { isColorNear(onSurface, it) })
        assertFalse("the tonal pill's label is not onPrimary", tonalInk.any { isColorNear(onPrimary, it) })
    }

    // T1 M2: a loading pill with no loadingDescription still has an accessible name, its label. Control: while idle the
    // label is drawn as text and nothing is described "Save". Trivial stub: no description while loading → RED on the
    // last assertion. Mutation: describe the loading pill by loadingDescription alone (null here) → RED.
    @Test
    fun loadingPillWithNoDescriptionIsDescribedByItsLabel() {
        var loading by mutableStateOf(false)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiPillButton(text = "Save", onClick = {}, loading = loading)
            }
        }
        assertEquals("control: the label shows while idle", 1, textCount("Save"))
        assertEquals("control: nothing is described Save while idle", 0, descriptionCount("Save"))

        composeRule.runOnIdle { loading = true }
        assertEquals("the label is swapped out while loading", 0, textCount("Save"))
        assertEquals(
            "with no loadingDescription the loading pill is described by its label",
            1,
            descriptionCount("Save"),
        )
    }

    // Ruling 1: loading swaps the label for a progress ring, ignores taps and is described by loadingDescription.
    // Trivial stub: loading ignored → "Save" stays and the second tap counts. Mutation: drop the loading branch → RED.
    @Test
    fun loadingPillShowsProgressIgnoresTapsAndIsDescribed() {
        var loading by mutableStateOf(false)
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiPillButton(
                    text = "Save",
                    onClick = { clicks++ },
                    loading = loading,
                    loadingDescription = "Saving",
                    modifier = Modifier.testTag("pill"),
                )
            }
        }
        assertEquals("the label shows while idle", 1, textCount("Save"))
        composeRule.onNodeWithTag("pill").performClick()
        composeRule.runOnIdle { assertEquals("an idle tap counts", 1, clicks) }

        composeRule.runOnIdle { loading = true }
        assertEquals("the label is swapped out while loading", 0, textCount("Save"))
        assertEquals("the loading button is described by loadingDescription", 1, descriptionCount("Saving"))
        assertEquals("a progress ring shows while loading", 1, progressRingCount())
        composeRule.onNodeWithTag("pill").performClick()
        composeRule.runOnIdle { assertEquals("a tap while loading is ignored", 1, clicks) }
    }

    // Ruling 1: skipPartiallyExpanded opens a tall sheet at full height. One composition: key(skip) recreates the sheet
    // (and its state) when the flag flips. Control: the default sheet (skip = false) opens half way, so its title sits
    // below the root's half. Trivial stub: the parameter not passed to the state → the first half fails. Mutation:
    // rememberModalBottomSheetState() without it → RED on the first half.
    @Test
    fun skipPartiallyExpandedSheetOpensAtFullHeight() {
        var skip by mutableStateOf(true)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                key(skip) {
                    OneUiBottomSheet(onDismissRequest = {}, title = "Sheet title", skipPartiallyExpanded = skip) {
                        Column {
                            repeat(SHEET_ROWS) { Box(Modifier.fillMaxWidth().height(RollaDimens.singleLineRowHeight)) }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("Sheet title").assertExists("the full-height sheet shows its title")
        val (fullTop, fullHeight) = titleTopAndRootHeight()
        assertTrue(
            "full-height sheet: title top $fullTop is in the top quarter of $fullHeight",
            fullTop < fullHeight / 4,
        )

        composeRule.runOnIdle { skip = false }
        composeRule.onNodeWithText("Sheet title").assertExists("the default sheet shows its title")
        val (halfTop, halfHeight) = titleTopAndRootHeight()
        assertTrue("default sheet: title top $halfTop is below the half of $halfHeight", halfTop > halfHeight / 2)
    }

    /** A Year field with the "Bad year" supporting text on a surfaceContainer card, as the tag editors place fields. */
    @Composable
    private fun YearFieldOnCard(isError: Boolean) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
            OneUiTextField(
                value = "20255",
                onValueChange = {},
                label = "Year",
                isError = isError,
                supportingText = "Bad year",
            )
        }
    }

    /** An empty search field tagged "search", full width on the background with 16 dp around it. */
    @Composable
    private fun EmptySearchFieldOnBackground() {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            OneUiSearchField(
                query = "",
                onQueryChange = {},
                placeholder = "Search tracks",
                modifier = Modifier.fillMaxWidth().testTag("search"),
            )
        }
    }

    private fun descriptionCount(description: String): Int =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun progressRingCount(): Int = composeRule
        .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
        .fetchSemanticsNodes().size

    private fun supportingInk(): List<Int> {
        val bitmap = composeRule.drawRoot()
        val bounds = composeRule.onNodeWithText("Bad year", useUnmergedTree = true).getUnclippedBoundsInRoot()
        return composeRule.pixelsIn(bitmap, bounds)
    }

    private fun clearButtonCount(): Int =
        composeRule.onAllNodesWithContentDescription("Clear search").fetchSemanticsNodes().size

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Every pixel inside the pill label's ("Save") bounds. */
    private fun labelInk(): List<Int> {
        val bitmap = composeRule.drawRoot()
        val bounds = composeRule.onNodeWithText("Save", useUnmergedTree = true).getUnclippedBoundsInRoot()
        return composeRule.pixelsIn(bitmap, bounds)
    }

    private fun pillProbe(): Int {
        val bitmap = composeRule.drawRoot()
        val pill: DpRect = composeRule.onNodeWithTag("pill").getUnclippedBoundsInRoot()
        return bitmap.pixelAt(pill.left + PROBE_INSET, (pill.top + pill.bottom) / 2)
    }

    /** The sheet title's top and the dialog root's height, in the dialog's root coordinates (px). */
    private fun titleTopAndRootHeight(): Pair<Float, Float> {
        composeRule.waitForIdle()
        val root = composeRule.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("Sheet title").fetchSemanticsNode().boundsInRoot
        return (title.top - root.top) to root.height
    }

    private fun Bitmap.pixelAt(x: Dp, y: Dp): Int = with(composeRule.density) { getPixel(x.roundToPx(), y.roundToPx()) }

    private fun Int.hex(): String = Integer.toHexString(this)

    private companion object {
        /** A test-only stand-in for colorScheme.error, far from every token on a dark screen. */
        val ERROR_SEPARATOR = Color(0xFF00FF00)

        /** The pill probes' inset from the field's or button's top-left (see searchFieldIsAPill). */
        val PROBE_INSET = 6.dp

        /** Enough 56 dp rows to make the sheet taller than the 640 dp root. */
        const val SHEET_ROWS = 40
    }
}
