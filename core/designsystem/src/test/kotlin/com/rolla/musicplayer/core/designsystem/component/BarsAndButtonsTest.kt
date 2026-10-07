package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BarsAndButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun circleIconButtonClicksAndHasA48dpTouchTarget() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CircleIconButton(icon = RollaIcons.Shuffle, contentDescription = "Shuffle all", onClick = { clicks++ })
            }
        }
        composeRule.onNodeWithContentDescription("Shuffle all")
            .assertHasClickAction()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledCircleIconButtonIgnoresClicks() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CircleIconButton(
                    icon = RollaIcons.Shuffle,
                    contentDescription = "Shuffle all",
                    onClick = { clicks++ },
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Shuffle all")
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, clicks)
    }

    // Robolectric's default screen is 320 dp wide, which would clamp the 360 dp box.
    @Config(qualifiers = "w360dp-h640dp")
    @Test
    fun sortHeaderCirclesMatchMeasuredGeometry() {
        val panelWidth = 360.dp
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(panelWidth)) {
                    ContentPanel {
                        SortHeader("Name") {
                            CircleIconButton(RollaIcons.Shuffle, "Shuffle", onClick = {})
                            CircleIconButton(RollaIcons.Play, "Play", onClick = {})
                        }
                    }
                }
            }
        }
        // getBoundsInRoot is the node's own (drawn) bounds; touch bounds are always widened to 48 dp for clickables.
        val shuffle = composeRule.onNodeWithContentDescription("Shuffle").getBoundsInRoot()
        val play = composeRule.onNodeWithContentDescription("Play").getBoundsInRoot()
        play.width.assertIsEqualTo(RollaDimens.circleButtonSize, "play circle width")
        (panelWidth - play.right).assertIsEqualTo(RollaDimens.circleButtonEnd, "play circle end inset")
        (play.left - shuffle.right).assertIsEqualTo(RollaDimens.circleButtonGap, "gap between circles")
    }

    @Test
    fun detailTopBarNavigatesUpAndExposesTitleAsHeading() {
        var ups = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDetailTopBar(title = "Equaliser", onNavigateUp = { ups++ }, windowInsets = WindowInsets(0))
            }
        }
        composeRule.onNode(isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Equaliser").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(1, ups)
    }

    @Test
    fun detailTopBarUsesTheCallerNavigationIconAndDescription() {
        var ups = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDetailTopBar(
                    title = "2 selected",
                    onNavigateUp = { ups++ },
                    navigationIcon = RollaIcons.Close,
                    navigationContentDescription = "Close selection",
                    windowInsets = WindowInsets(0),
                )
            }
        }
        composeRule.onNodeWithContentDescription("Navigate up").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close selection").assertHasClickAction().performClick()
        assertEquals(1, ups)
    }

    @Test
    fun disabledIconButtonReportsNotEnabledAndIgnoresClicks() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiIconButton(
                    icon = RollaIcons.Search,
                    contentDescription = "Search",
                    onClick = { clicks++ },
                    enabled = false,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Search")
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun iconButtonDrawsItsGlyphAtTheRequestedSize() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiIconButton(
                    icon = RollaIcons.Search,
                    contentDescription = "Search",
                    onClick = {},
                    iconSize = 20.dp,
                )
            }
        }
        // OneUiIconButton hands its description to the Icon, which sets it (with Role.Image) on its own semantics
        // node after its size modifier. In the merged tree that description is folded into the 48 dp button; the
        // unmerged tree still has the Icon's own node, whose bounds are the glyph.
        composeRule.onNode(
            hasContentDescription("Search") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image),
            useUnmergedTree = true,
        )
            .assertWidthIsEqualTo(20.dp)
            .assertHeightIsEqualTo(20.dp)
        composeRule.onNodeWithContentDescription("Search")
            .assertTouchWidthIsEqualTo(RollaDimens.minTouchTarget)
    }

    @Test
    fun topBarShowsTitleAndActions() {
        var searches = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiTopBar(title = "Rolla Music", windowInsets = WindowInsets(0)) {
                    OneUiIconButton(icon = RollaIcons.Search, contentDescription = "Search", onClick = { searches++ })
                }
            }
        }
        composeRule.onNodeWithText("Rolla Music").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(1, searches)
    }

    @Test
    fun sortHeaderShowsLabelAndTrailingContent() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                ContentPanel {
                    SortHeader(label = "Name") { Text("trailing") }
                }
            }
        }
        composeRule.onNodeWithText("Name").assertIsDisplayed()
        composeRule.onNodeWithText("trailing").assertIsDisplayed()
    }
}
