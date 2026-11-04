package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The selection-mode checkbox is the kit's round [com.rolla.musicplayer.core.designsystem.component.OneUiCheckbox]. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongListItemCheckboxPixelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // 6b ruling P4 / Phase 7: the 48 dp box centres a round 20 dp checkbox (14..34 dp). The probe at (16, 16) dp lies
    // outside that circle (11.3 dp from its centre), so it shows the selected row's highlight; M3's Checkbox draws
    // an 18 dp square with 2 dp corners there (15..33 dp), so the same probe is inside its primary fill. Control: the
    // probe at (24, 17) dp, inside both shapes, is primary (fails under a checkbox that draws nothing). Mutation: put
    // back M3's Checkbox → the corner probe is primary → RED.
    @Test
    fun theSelectionCheckboxIsRound() {
        var highlight = Color.Unspecified
        var primary = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                highlight = MaterialTheme.colorScheme.surfaceContainerHigh
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.width(360.dp)) {
                    SongListItem(song, onClick = {}, selected = true, selectionModeActive = true)
                }
            }
        }
        val bitmap = composeRule.drawRoot()
        val box = composeRule.onNodeWithTag(SONG_SELECTION_CHECKBOX_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        fun probe(x: Dp, y: Dp): Int =
            composeRule.pixelsIn(bitmap, DpRect(box.left + x, box.top + y, box.left + x + 1.dp, box.top + y + 1.dp))
                .first()
        assertColorNear(primary, probe(24.dp, 17.dp))
        assertColorNear(highlight, probe(16.dp, 16.dp))
    }

    private val song = Song(
        id = "1",
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 10L,
        durationMs = 354_000L,
        trackNumber = 11,
        year = 1975,
        contentUri = "content://media/external/audio/media/1",
        artworkUri = "",
    )
}
