package com.rolla.musicplayer.feature.playlists

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 7 calibration: the reference draws no hairline under the last user playlist ("Neuro"), while the rows above
 * keep theirs. The Playlists tab renders three user playlists; the hairline's centre row is probed under the
 * second-to-last row (divider) and the last row (none), in one composition. 3 px/dp, so the 1 dp hairline has a solid
 * middle pixel row. Rendering: view.draw (drawRoot).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistsLastRowDividerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Trivial stub (never draw a divider) → the second-to-last row has none → RED.
    // Mutations: PlaylistsTab passes showDivider = true for every row (always show) → RED; PlaylistRow ignores
    // showDivider → RED.
    @Test
    fun userPlaylistsDrawNoDividerUnderTheLastRow() {
        var divider = Color.Unspecified
        val playlists = listOf("Chill", "Focus", "Neuro").mapIndexed { index, name ->
            Playlist(id = index + 1L, name = name, songCount = 0, createdAt = 0L, updatedAt = 0L)
        }
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                divider = MaterialTheme.colorScheme.outlineVariant
                PlaylistsTabContent(
                    smartPlaylists = emptyList(),
                    userPlaylists = playlists,
                    showCreateDialog = false,
                    onDismissCreateDialog = {},
                    onCreatePlaylist = {},
                    onPlaylistClick = {},
                    onSmartPlaylistClick = {},
                )
            }
        }
        val bitmap = composeRule.drawRoot()
        val secondToLast = hairlinePixel(bitmap, "Focus")
        val last = hairlinePixel(bitmap, "Neuro")
        assertTrue(
            "the second-to-last row keeps its divider: ${Integer.toHexString(secondToLast)}",
            isColorNear(divider, secondToLast),
        )
        assertFalse("the last row has no divider: ${Integer.toHexString(last)}", isColorNear(divider, last))
    }

    /** The pixel on the hairline's centre row under the row titled [title], 40 dp along the divider. */
    private fun hairlinePixel(bitmap: Bitmap, title: String): Int {
        val row = composeRule.onNodeWithText(title).getBoundsInRoot()
        return with(composeRule.density) {
            val x = (row.left + RollaDimens.listTextStart + 40.dp).roundToPx()
            val y = (row.bottom - RollaDimens.dividerThickness / 2).toPx().toInt()
            bitmap.getPixel(x, y)
        }
    }
}
