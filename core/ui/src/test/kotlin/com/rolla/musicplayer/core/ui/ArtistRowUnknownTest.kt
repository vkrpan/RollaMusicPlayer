package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Artist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phase 7 ruling 3: an artist row shows MediaStore's `<unknown>` as "Unknown"; a real name shows as itself. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class ArtistRowUnknownTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Trivial stub: the raw name (title = artist.name) → "Unknown" missing and "<unknown>" shown → RED. A constant
    // "Unknown" stub fails the Sade control. Mutation: title = artist.name (no artistDisplayName) → RED.
    @Test
    fun unknownArtistRowShowsUnknown() {
        var artist by mutableStateOf(Artist(id = 1L, name = "<unknown>", albumCount = 1, songCount = 3))
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                ArtistRow(artist = artist, onClick = {})
            }
        }
        assertEquals("the <unknown> artist's row shows Unknown", 1, textCount("Unknown"))
        assertEquals("the <unknown> artist's row never shows the raw <unknown>", 0, textCount("<unknown>"))

        composeRule.runOnIdle { artist = Artist(id = 2L, name = "Sade", albumCount = 1, songCount = 3) }
        assertEquals("control: a real artist's row shows its name", 1, textCount("Sade"))
        assertEquals("control: a real artist's row does not show Unknown", 0, textCount("Unknown"))
    }

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
}
