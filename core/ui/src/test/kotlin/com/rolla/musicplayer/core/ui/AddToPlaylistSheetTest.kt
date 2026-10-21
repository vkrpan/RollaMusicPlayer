package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The One UI add-to-playlist sheet (ruling 11) and [PlaylistRow]'s reserveRailSpace = false variant it uses. */
// NATIVE graphics: under LEGACY, Robolectric measures every text line at ~36 dp, which makes the row-height and
// trailing-text-edge assertions meaningless.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddToPlaylistSheetTest {

    // An activity rule (what createComposeRule() builds internally), so the divider probe can use drawRoot().
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Ruling 11: the sheet is titled and its "New playlist" entry is a single-line action row. Control: a playlist row
    // renders. Mutations: (a) drop the title → RED; (b) restore the 56 dp-thumbnail NewPlaylistRow → its height is not
    // singleLineRowHeight → RED.
    @Test
    fun titledSheetWithASingleLineNewPlaylistRow() {
        setSheet(listOf(playlist(1L, "Workout")))
        composeRule.onNode(hasText("Add to playlist") and isHeading()).assertExists()
        composeRule.onNodeWithText("Workout").assertExists()
        // The label merges into SheetActionRow's clickable node, so its own (unmerged) node's parent is the row.
        val row = composeRule.onNodeWithText("New playlist", useUnmergedTree = true).onParent()
            .getUnclippedBoundsInRoot()
        assertEquals(
            "the New playlist row is singleLineRowHeight tall",
            RollaDimens.singleLineRowHeight.value,
            (row.bottom - row.top).value,
            1f,
        )
    }

    // Ruling 11 / 2-L7: inside the sheet a playlist row's trailing count ends at listThumbStart, not before the rail,
    // and its divider runs to the row's end. Control: with reserveRailSpace = true (the default, in a plain host), the
    // count ends at listTrailingEnd and the divider stops listDividerEnd short, while both rows draw a divider mid-row.
    // Mutations: (a) ignore reserveRailSpace for the count's end padding → the sheet's count ends at listTrailingEnd →
    // RED; (b) ignore it for the divider's endInset → the rail-strip probe shows the background → RED.
    @Test
    fun sheetRowsDoNotReserveTheRail() {
        var background = Color.Unspecified
        var divider = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background
                divider = MaterialTheme.colorScheme.outlineVariant
                Column(Modifier.width(HOST_WIDTH).background(background)) {
                    PlaylistRow(playlist(1L, "Rail"), onClick = {}, reserveRailSpace = true)
                    PlaylistRow(playlist(2L, "Sheet"), onClick = {}, reserveRailSpace = false)
                }
            }
        }
        val counts = composeRule.onAllNodesWithText("3 tracks", useUnmergedTree = true)
        counts.assertCountEquals(2)
        // Sorted by top, so [0] is the reserveRailSpace = true row and [1] the false one.
        val bounds = (0 until 2).map { counts[it].getUnclippedBoundsInRoot() }.sortedBy { it.top.value }
        bounds[0].right.assertIsEqualTo(HOST_WIDTH - RollaDimens.listTrailingEnd, "rail row count right", 1.dp)
        bounds[1].right.assertIsEqualTo(HOST_WIDTH - RollaDimens.listThumbStart, "sheet row count right", 1.dp)

        // The 1 dp hairline overlays each row's bottom edge. Probe its centre line mid-row (control) and inside the
        // rail strip, halfway between listDividerEnd and the row's end.
        val bitmap = composeRule.drawRoot()
        val railStripX = HOST_WIDTH - RollaDimens.listDividerEnd / 2
        fun pixel(x: Dp, rowIndex: Int): Int = with(composeRule.density) {
            val rowBottom = RollaDimens.singleLineRowHeight * (rowIndex + 1)
            bitmap.getPixel(x.roundToPx(), (rowBottom - RollaDimens.dividerThickness / 2).toPx().toInt())
        }
        assertTrue("rail row divider mid-row", isColorNear(divider, pixel(HOST_WIDTH / 2, 0)))
        assertTrue("sheet row divider mid-row", isColorNear(divider, pixel(HOST_WIDTH / 2, 1)))
        assertTrue("rail row divider stops before the rail", isColorNear(background, pixel(railStripX, 0)))
        assertTrue("sheet row divider runs to the row's end", isColorNear(divider, pixel(railStripX, 1)))
    }

    private fun setSheet(playlists: List<Playlist>) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                AddToPlaylistSheet(
                    playlists = playlists,
                    onPlaylistSelected = {},
                    onCreateNewPlaylist = {},
                    onDismissRequest = {},
                )
            }
        }
    }

    private companion object {
        val HOST_WIDTH = 360.dp

        fun playlist(id: Long, name: String) =
            Playlist(id = id, name = name, songCount = 3, createdAt = 0L, updatedAt = 0L)
    }
}
