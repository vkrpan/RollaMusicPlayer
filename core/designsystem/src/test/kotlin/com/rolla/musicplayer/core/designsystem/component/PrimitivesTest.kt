package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrimitivesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun artworkPlaceholderIsDecorativeForAccessibility() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                ArtworkPlaceholder(modifier = Modifier.size(48.dp))
            }
        }
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(0)
    }

    @Test
    fun miniPlayerInsetDefaultsToZeroAndCanBeProvided() {
        var defaultInset: Dp? = null
        var providedInset: Dp? = null
        composeRule.setContent {
            defaultInset = LocalMiniPlayerInset.current
            CompositionLocalProvider(LocalMiniPlayerInset provides 72.dp) {
                providedInset = LocalMiniPlayerInset.current
            }
        }
        assertEquals(0.dp, defaultInset)
        assertEquals(72.dp, providedInset)
    }
}
