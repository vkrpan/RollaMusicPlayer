package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawRoot
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureCardPlaceholderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Phase 7 ruling P13: the empty card's placeholder is artworkPlaceholder (#454547, as measured), not the darker
    // artworkPlaceholderLarge. The probe is 12 dp in from the art box's top-start corner: inside the 20 dp rounded
    // corner and clear of the centred note. Control: the two tokens differ in the dark theme. Mutation: the card's
    // ArtworkPlaceholder keeps large = true → RED.
    @Test
    fun emptyCardPlaceholderIsArtworkPlaceholder() {
        var placeholder = Color.Unspecified
        var large = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                placeholder = MaterialTheme.colorScheme.artworkPlaceholder
                large = MaterialTheme.colorScheme.artworkPlaceholderLarge
                FeatureCard("Favourite tracks", "0 tracks", emptyList(), onClick = {})
            }
        }
        assertNotEquals("control: the two placeholder tokens differ", large, placeholder)
        val bitmap = composeRule.drawRoot()
        val art = composeRule.onNodeWithTag(FEATURE_CARD_ART_TAG, useUnmergedTree = true).getBoundsInRoot()
        val pixel = with(composeRule.density) {
            bitmap.getPixel((art.left + 12.dp).roundToPx(), (art.top + 12.dp).roundToPx())
        }
        assertColorNear(placeholder, pixel)
    }
}
