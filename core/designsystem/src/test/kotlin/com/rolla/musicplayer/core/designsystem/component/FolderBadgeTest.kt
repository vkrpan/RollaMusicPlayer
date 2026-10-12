package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholder
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BADGE_TAG = "badge"
private const val GRID = 24f

// SDK 30 + NATIVE: view.draw renders reliably there (OneUiTabRowTest precedent). xxhdpi makes the 1.6-unit stroke
// ~4 px wide, so a single sample pixel lands inside it.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderBadgeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Light theme: the placeholder (#E6E6E8) differs from both badge colours. Mutations:
    // drop the fill Icon → the body centre shows the placeholder → RED;
    // drop the stroke Icon → the stroke sample shows the placeholder → RED;
    // swap the two tints → both samples flip → RED;
    // a background behind the badge Box → the corner sample changes → RED.
    @Test
    fun whiteOutlineOverBlackFillWithNoBackingSquare() {
        var placeholder = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = false) {
                placeholder = MaterialTheme.colorScheme.artworkPlaceholder
                Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                    FolderBadge(Modifier.testTag(BADGE_TAG))
                }
            }
        }
        val bounds = composeRule.onNodeWithTag(BADGE_TAG).fetchSemanticsNode().boundsInRoot
        val bitmap = composeRule.drawRoot()
        fun pixelAt(gridX: Float, gridY: Float): Int = bitmap.getPixel(
            (bounds.left + bounds.width * gridX / GRID).toInt(),
            (bounds.top + bounds.height * gridY / GRID).toInt(),
        )
        assertColorNear(Color.Black, pixelAt(12f, 13.5f))
        assertColorNear(Color.White, pixelAt(3.0f, 13.0f))
        assertColorNear(placeholder, pixelAt(1f, 1f))
    }
}
