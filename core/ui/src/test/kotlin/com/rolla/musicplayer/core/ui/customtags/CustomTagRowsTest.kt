package com.rolla.musicplayer.core.ui.customtags

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tags and Subtags rows of the tag editors (custom tags phase 2, Task 3). */
// NATIVE graphics: under LEGACY, Robolectric measures text at a fixed size, which makes the wrap geometry meaningless.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomTagRowsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // A file that cannot carry custom tags (M4A): the rows explain why and ignore taps. Control: while enabled the same
    // row reports its tap and shows no note. Stub: clickable always. Mutation: keep the row clickable when disabled →
    // the second tap fires → RED.
    @Test
    fun rows_disabled_showNoteAndIgnoreTaps() {
        var enabled by mutableStateOf(true)
        var tagClicks = 0
        var subTagClicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CustomTagRows(
                    tagNames = listOf("Techno"),
                    subTagNames = emptyList(),
                    enabled = enabled,
                    onTagsClick = { tagClicks++ },
                    onSubTagsClick = { subTagClicks++ },
                )
            }
        }
        composeRule.onNodeWithText(MP3_FLAC_NOTE).assertDoesNotExist()
        composeRule.onNodeWithText("Tags").performClick()
        assertEquals("enabled: the Tags row reports its tap", 1, tagClicks)

        composeRule.runOnIdle { enabled = false }
        composeRule.onNodeWithText(MP3_FLAC_NOTE).assertIsDisplayed()
        composeRule.onNodeWithText("Tags").performClick()
        composeRule.onNodeWithText("Subtags").performClick()
        assertEquals("disabled: the Tags row ignores its tap", 1, tagClicks)
        assertEquals("disabled: the Subtags row ignores its tap", 0, subTagClicks)
    }

    // A disabled row says why itself (per-screen concerns, Ruling 3): TalkBack reads the note as the row's state, not
    // only as the separate text after it. Control: the visible note is there. Mutation: drop the row's
    // stateDescription → RED.
    @Test
    fun rows_disabled_announceNoteInRowSemantics() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CustomTagRows(
                    tagNames = listOf("Techno"),
                    subTagNames = emptyList(),
                    enabled = false,
                    onTagsClick = {},
                    onSubTagsClick = {},
                )
            }
        }
        composeRule.onNodeWithText(MP3_FLAC_NOTE).assertIsDisplayed()
        composeRule.onNodeWithText("Tags")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, MP3_FLAC_NOTE))
    }

    // Ruling 4: at fontScale 1.5 on a 360 dp phone, in a card inset like the tag editor's, chips wrap onto more lines
    // and a name too long for one line wraps inside its chip instead of ellipsizing: no chip's text overflows, and no
    // chip runs past the card. Stub / mutation: maxLines = 1 with an ellipsis in the chip → the 40-character name
    // overflows → RED.
    @Test
    fun chipFlow_largeFont_wrapsWithoutClipping() {
        val long = "Progressive House And Melodic Techno Mix"
        val names = (1..5).map { "Progressive House 0$it" } + long
        check(long.length == 40 && names.dropLast(1).all { it.length == 20 })
        setRowsAtLargeFont(names)
        val card = composeRule.onNodeWithTag(CUSTOM_TAG_ROWS_TAG).getUnclippedBoundsInRoot()
        val innerRight = card.right - RollaDimens.settingsRowPadding
        names.forEach { name ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(name, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("\"$name\" is not clipped or ellipsized", layouts.single().hasVisualOverflow)
        }
        val chips = composeRule.onAllNodesWithTag(CUSTOM_TAG_CHIP_TAG, useUnmergedTree = true)
        chips.assertCountEquals(names.size)
        val bounds = (0 until names.size).map { chips[it].getUnclippedBoundsInRoot() }
        bounds.forEachIndexed { index, chip ->
            assertTrue("chip $index ends at ${chip.right}, inside $innerRight", chip.right <= innerRight + 0.5.dp)
        }
        val tops = bounds.map { it.top.value.toInt() }.distinct()
        assertTrue("the chips wrap onto at least two rows: tops $tops", tops.size >= 2)
    }

    /**
     * The rows with [tagNames] across the 360 dp window, inset settingsCardInset on each side like the tag editor's
     * cards, at fontScale [LARGE_FONT].
     */
    private fun setRowsAtLargeFont(tagNames: List<String>) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT)) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = RollaDimens.settingsCardInset)) {
                        CustomTagRows(
                            tagNames = tagNames,
                            subTagNames = emptyList(),
                            enabled = true,
                            onTagsClick = {},
                            onSubTagsClick = {},
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val LARGE_FONT = 1.5f
        const val MP3_FLAC_NOTE = "Tags can be saved to MP3 and FLAC files only."
    }
}
