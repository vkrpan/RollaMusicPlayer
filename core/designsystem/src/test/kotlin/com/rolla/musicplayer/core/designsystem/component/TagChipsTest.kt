package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.subTagChipBorder
import com.rolla.musicplayer.core.designsystem.theme.tagChipContainer
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LINE_TAG = "line"
private const val ROW_TAG = "row"
private val LONG_LABEL = "Late night drive through the city with the windows down tonight".take(60)
private val LINE_WIDTH = 200.dp

// NATIVE graphics so text is measured with real fonts (the line's whole job is fitting widths); SDK 30, where
// view.draw renders reliably (OneUiTabRowTest precedent). xxhdpi gives the 1 dp outline 3 px for the pixel test.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagChipsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Trivial stub (never render): "Rock" is missing → RED. Mutation: always add a "+0" chip → one "+" node → RED.
    @Test
    fun line_allFit_noPlusN() {
        setLine(
            listOf(
                TagChipData("Rock", TagChipStyle.Tonal),
                TagChipData("Jazz", TagChipStyle.Tonal),
                TagChipData("Live", TagChipStyle.Outline),
            ),
        )
        assertEquals("every chip that fits is shown", listOf("Jazz", "Live", "Rock"), chipTexts("").sorted())
        assertEquals("no +N chip when every chip fits", 0, plusNodes().size)
    }

    // 120 dp holds two "Tag k" chips and the "+N" chip. Trivial stub: no "+N" chip → RED. Mutation: N = the total
    // (8) instead of the hidden count → "+8" ≠ "+6" → RED.
    @Test
    fun line_overflow_showsPlusNWithHiddenCount() {
        setLine((1..8).map { TagChipData("Tag $it", TagChipStyle.Tonal) }, width = 120.dp)
        val visible = chipTexts("Tag ").size
        val plus = plusNodes()
        assertEquals("exactly one +N chip when chips overflow", 1, plus.size)
        assertTrue("at least one chip and not all 8 are shown, was $visible", visible in 1..7)
        assertEquals("+N counts the hidden chips", "+${8 - visible}", plus.single().text())
        assertTrue("the +N chip ends inside the line", plus.single().right() <= lineRight() + 1f)
    }

    // 20 chips overflow two 200 dp lines; a full second line must drop its last chip to fit "+N" (fix round 1, I1).
    // Trivial stub: no chip at all → RED. Mutations: ignore maxLines (always one line) → the chips sit on one line →
    // RED; never drop chips for "+N" → "+N" runs past the line's end → RED.
    @Test
    fun line_twoLines_wrapsBeforePlusN() {
        setLine((1..20).map { TagChipData("Tag $it", TagChipStyle.Tonal) }, maxLines = 2)
        val chips = chipNodes("Tag ")
        val tops = chips.map { it.top().roundToInt() }.distinct().sorted()
        assertEquals("the chips fill exactly two lines", 2, tops.size)
        val plus = plusNodes()
        assertEquals("exactly one +N chip after two full lines", 1, plus.size)
        assertEquals("the +N chip sits on the second line", tops.last(), plus.single().top().roundToInt())
        assertEquals("+N counts the hidden chips", "+${20 - chips.size}", plus.single().text())
        assertPlusEndsLineWithoutOverlap(plus.single(), chips.filter { it.top().roundToInt() == tops.last() })
    }

    // The twoLines fixture inside a clickable (merging) row, as Task 3's track rows host it (fix round 2, I2). The chip
    // dropped for "+N" and the chip that ended line 2 are composed but not placed: their labels must not be in the
    // row's merged description, while shown labels stay. In the unmerged test tree Compose keeps a cleared node's
    // descendants on purpose (SemanticsNode.children passes includeReplacedSemantics = !mergingEnabled), so there the
    // test asserts what keeps them from TalkBack: each hidden label is unplaced and under a clearing node, which the
    // accessibility tree (getReplacedChildren) skips; shown labels are placed and not cleared. Trivial stub: the row
    // has no "+N" label → RED. Mutation: never clear a hidden chip's semantics (the round-1 code) → the row reads the
    // hidden labels → RED.
    @Test
    fun line_hiddenChips_leaveNoSemantics_inAMergingRow() {
        setLineInClickableRow((1..20).map { TagChipData("Tag $it", TagChipStyle.Tonal) }, maxLines = 2)
        val rowTexts = composeRule.onNodeWithTag(ROW_TAG).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertTrue("the row reads a +N label, was $rowTexts", rowTexts.any { it.startsWith("+") })
        val plus = rowTexts.single { it.startsWith("+") }
        val shownCount = 20 - plus.drop(1).toInt()
        val expected = (1..shownCount).map { "Tag $it" } + plus
        assertEquals("the row reads exactly the shown chips and +N", expected.sorted(), rowTexts.sorted())
        listOf("Tag ${shownCount + 1}", "Tag ${shownCount + 2}").forEach { hidden ->
            composeRule.onNodeWithText(hidden).assertDoesNotExist()
            composeRule.onAllNodes(hasText(hidden), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                assertTrue("hidden $hidden is not placed", !node.layoutInfo.isPlaced)
                assertTrue("hidden $hidden sits under a clearing node", node.isUnderClearingNode())
            }
        }
        composeRule.onNodeWithText("Tag $shownCount").assertExists()
        val shown = composeRule.onNodeWithText("Tag $shownCount", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("shown Tag $shownCount is placed", shown.layoutInfo.isPlaced)
        assertTrue("shown Tag $shownCount is not cleared", !shown.isUnderClearingNode())
    }

    // A 60-character chip and "Rock" at 200 dp, one line: "Rock" is hidden and the long chip narrows to sit beside
    // "+1" (fix round 1, M2). Trivial stub: the label is missing → RED. Mutation: drop the lone chip instead of
    // narrowing it → only "+2" shows → RED.
    @Test
    fun line_lastChipNarrowsBesidePlusN() {
        val label = LONG_LABEL
        setLine(listOf(TagChipData(label, TagChipStyle.Tonal), TagChipData("Rock", TagChipStyle.Tonal)))
        assertEquals("the long chip stays, narrowed", listOf(label), chipTexts(""))
        val plus = plusNodes()
        assertEquals("exactly one +N chip", 1, plus.size)
        assertEquals("+1 counts Rock", "+1", plus.single().text())
        assertPlusEndsLineWithoutOverlap(plus.single(), chipNodes(""))
    }

    // The same two chips at 40 dp: beside "+1" the long chip would get less room than two paddings and an ellipsis,
    // so it is dropped and counted (fix round 1, M2). Trivial stub: no "+N" chip → RED. Mutation: narrow whenever any
    // room is left (the old `room() > 0`) → a sliver of the long chip shows beside "+1" → RED.
    @Test
    fun line_tooLittleRoomBesidePlusN_dropsTheChip() {
        setLine(
            listOf(TagChipData(LONG_LABEL, TagChipStyle.Tonal), TagChipData("Rock", TagChipStyle.Tonal)),
            width = 40.dp,
        )
        val plus = plusNodes()
        assertEquals("exactly one +N chip", 1, plus.size)
        assertEquals("no chip is narrowed to a sliver", emptyList<String>(), chipTexts(""))
        assertEquals("+2 counts both chips", "+2", plus.single().text())
        assertTrue("the +N chip ends inside the line", plus.single().right() <= lineRight() + 1f)
    }

    // One long chip on a 16 dp line, narrower than the narrowest useful chip: the chip is not drawn as a sliver but
    // counted in "+1" (fix round 1, M2, the narrow-line path). Trivial stub: no "+N" chip → RED. Mutation: narrow the
    // first chip to any line width → the chip shows and there is no "+N" → RED.
    @Test
    fun line_narrowerThanAChip_showsOnlyPlusN() {
        setLine(listOf(TagChipData(LONG_LABEL, TagChipStyle.Tonal)), width = 16.dp)
        val plus = plusNodes()
        assertEquals("exactly one +N chip", 1, plus.size)
        assertEquals("+1 counts the chip", "+1", plus.single().text())
        assertEquals("no chip is narrowed to a sliver", emptyList<String>(), chipTexts(""))
    }

    // One 60-character label. Trivial stub: the label is missing → RED. Mutation: measure the first chip at its full
    // width (no re-measure) → it runs past the line's end → RED.
    @Test
    fun line_longFirstChip_ellipsizesInsideWidth() {
        val label = "Late night drive through the city with the windows down tonight".take(60)
        setLine(listOf(TagChipData(label, TagChipStyle.Tonal)))
        val text = composeRule.onNodeWithText(label).fetchSemanticsNode()
        val chipRight = text.right() + px(RollaDimens.tagChipHorizontalPadding)
        assertTrue("the chip ($chipRight px) ends inside the line (${lineRight()} px)", chipRight <= lineRight() + 1f)
        assertTrue("the label keeps some width", text.size.width > 0)
    }

    // Present, then absent, in one composition. Trivial stub: no line node → RED. Mutation: reserve one chip's height
    // when the list is empty → 18 dp → RED.
    @Test
    fun line_empty_hasZeroHeight() {
        var chips by mutableStateOf(listOf(TagChipData("Rock", TagChipStyle.Tonal)))
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                TagChipLine(chips = chips, modifier = Modifier.width(LINE_WIDTH).testTag(LINE_TAG))
            }
        }
        val withChip = lineNode().size.height
        assertTrue(
            "one chip gives the line at least tagChipHeight, was $withChip px",
            withChip >= px(RollaDimens.tagChipHeight) - 1f,
        )
        composeRule.runOnIdle { chips = emptyList() }
        assertEquals("an empty line has zero height", 0, lineNode().size.height)
    }

    // A wide chip alone on line 1, two chips on line 2. Trivial stub: no chips, no lines → RED. Mutation: ignore
    // `centred` → each line starts at the left edge → RED.
    @Test
    fun line_centred_centresEachLine() {
        setLine(
            listOf(
                TagChipData("Progressive house anthems", TagChipStyle.Tonal),
                TagChipData("Deep focus music mix", TagChipStyle.Outline),
                TagChipData("Rain", TagChipStyle.Outline),
            ),
            maxLines = 2,
            centred = true,
        )
        val pad = px(RollaDimens.tagChipHorizontalPadding)
        val lines = chipNodes("").groupBy { it.top().roundToInt() }.values
        assertEquals("the chips fill two lines", 2, lines.size)
        val lineLeft = lineNode().positionInRoot.x
        lines.forEach { line ->
            val startGap = line.minOf { it.left() } - pad - lineLeft
            val endGap = lineRight() - (line.maxOf { it.right() } + pad)
            val texts = line.map { it.text() }
            assertTrue("line $texts is not edge-aligned (start gap $startGap px)", startGap > px(2.dp))
            assertTrue("line $texts is centred: $startGap vs $endGap px", abs(startGap - endGap) <= 2f)
        }
    }

    // Tonal fills with tagChipContainer; Outline leaves the background showing inside its subTagChipBorder outline.
    // Trivial stub: nothing drawn → the Tonal sample is the background → RED. Mutation: Outline uses the Tonal fill →
    // the Outline interior sample is tagChipContainer → RED.
    @Test
    fun chip_outlineAndTonalDiffer() {
        val colors = setTonalAndOutlineChips()
        val tonal = composeRule.onNodeWithTag("tonal").fetchSemanticsNode().boundsInRoot
        val outline = composeRule.onNodeWithTag("outline").fetchSemanticsNode().boundsInRoot
        val bitmap = composeRule.drawRoot()
        // Inside the start padding, on the vertical centre: clear of the label, inside the pill's rounded end.
        val inset = px(3.dp)
        val tonalInside = bitmap.getPixel((tonal.left + inset).toInt(), tonal.center.y.toInt())
        val outlineInside = bitmap.getPixel((outline.left + inset).toInt(), outline.center.y.toInt())
        // The outline's top edge, mid-width: 1 dp = 3 px at xxhdpi, sampled on its middle pixel.
        val outlineEdge = bitmap.getPixel(outline.center.x.toInt(), (outline.top + 1f).toInt())
        assertTrue(
            "Tonal is filled with tagChipContainer, was ${hex(tonalInside)}",
            isColorNear(colors.container, tonalInside),
        )
        assertTrue("Outline has no fill, was ${hex(outlineInside)}", isColorNear(colors.background, outlineInside))
        assertTrue("Outline draws subTagChipBorder, was ${hex(outlineEdge)}", isColorNear(colors.border, outlineEdge))
    }

    // Trivial stub: no node labelled "Remove filter Rock" → RED. Mutation: no contentDescription → the node is not
    // found by its label → RED.
    @Test
    fun removable_click_callsOnRemoveOnce_andAnnounces() {
        var removes = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                RemovableChip(label = "Tag: Rock", removeLabel = "Remove filter Rock", onRemove = { removes++ })
            }
        }
        composeRule.onNodeWithContentDescription("Remove filter Rock")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("Tag: Rock"))
            .performClick()
        assertEquals("one tap removes once", 1, removes)
    }

    private fun setLine(chips: List<TagChipData>, width: Dp = LINE_WIDTH, maxLines: Int = 1, centred: Boolean = false) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                TagChipLine(
                    chips = chips,
                    modifier = Modifier.width(width).testTag(LINE_TAG),
                    maxLines = maxLines,
                    centred = centred,
                )
            }
        }
    }

    /** The line at [LINE_WIDTH] inside a clickable Box tagged [ROW_TAG], which merges descendants like a track row. */
    private fun setLineInClickableRow(chips: List<TagChipData>, maxLines: Int) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.clickable {}.testTag(ROW_TAG)) {
                    TagChipLine(chips = chips, modifier = Modifier.width(LINE_WIDTH), maxLines = maxLines)
                }
            }
        }
    }

    /** A Tonal "Rock" (tag "tonal") and an Outline "Live" (tag "outline") on the dark background; returns tokens. */
    private fun setTonalAndOutlineChips(): ChipSampleColors {
        val colors = ChipSampleColors()
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                colors.background = MaterialTheme.colorScheme.background
                colors.container = MaterialTheme.colorScheme.tagChipContainer
                colors.border = MaterialTheme.colorScheme.subTagChipBorder
                Row(Modifier.background(colors.background).padding(16.dp)) {
                    TagChip(label = "Rock", style = TagChipStyle.Tonal, modifier = Modifier.testTag("tonal"))
                    TagChip(
                        label = "Live",
                        style = TagChipStyle.Outline,
                        modifier = Modifier.padding(start = 16.dp).testTag("outline"),
                    )
                }
            }
        }
        return colors
    }

    /**
     * The "+N" chip ends inside the line, and every chip on its line ends before it starts: chip edges are their label
     * edges plus the chip padding.
     */
    private fun assertPlusEndsLineWithoutOverlap(plus: SemanticsNode, chipsOnItsLine: List<SemanticsNode>) {
        val pad = px(RollaDimens.tagChipHorizontalPadding)
        assertTrue(
            "the +N chip (${plus.right() + pad} px) ends inside the line (${lineRight()} px)",
            plus.right() + pad <= lineRight() + 1f,
        )
        val plusLeft = plus.left() - pad
        chipsOnItsLine.forEach { chip ->
            assertTrue(
                "chip ${chip.text()} (${chip.right() + pad} px) ends before +N starts ($plusLeft px)",
                chip.right() + pad <= plusLeft + 1f,
            )
        }
    }

    private fun lineNode(): SemanticsNode = composeRule.onNodeWithTag(LINE_TAG).fetchSemanticsNode()

    private fun lineRight(): Float = lineNode().let { it.positionInRoot.x + it.size.width }

    /** The placed chip labels starting with [prefix] (every chip label when empty), never a "+N" chip. */
    private fun chipNodes(prefix: String): List<SemanticsNode> = composeRule
        .onAllNodes(hasText(prefix, substring = true))
        .fetchSemanticsNodes()
        .filter { it.layoutInfo.isPlaced && it.text().isNotEmpty() && !it.text().startsWith("+") }

    private fun chipTexts(prefix: String): List<String> = chipNodes(prefix).map { it.text() }

    private fun plusNodes(): List<SemanticsNode> = composeRule
        .onAllNodes(hasText("+", substring = true))
        .fetchSemanticsNodes()
        .filter { it.layoutInfo.isPlaced }

    private fun px(dp: Dp): Float = with(composeRule.density) { dp.toPx() }
}

/** The tokens the pixel test samples against, read inside the theme. */
private class ChipSampleColors {
    var background = Color.Unspecified
    var container = Color.Unspecified
    var border = Color.Unspecified
}

private fun SemanticsNode.text(): String =
    config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

/** True when this node or an ancestor clears its descendants' semantics (the accessibility tree then skips them). */
private fun SemanticsNode.isUnderClearingNode(): Boolean =
    generateSequence(this) { it.parent }.any { it.config.isClearingSemantics }

// Unclipped edges: boundsInRoot clips a child to its parent, which would hide a chip that runs past the line.
private fun SemanticsNode.left(): Float = positionInRoot.x

private fun SemanticsNode.right(): Float = positionInRoot.x + size.width

private fun SemanticsNode.top(): Float = positionInRoot.y

private fun hex(argb: Int): String = Integer.toHexString(argb)
