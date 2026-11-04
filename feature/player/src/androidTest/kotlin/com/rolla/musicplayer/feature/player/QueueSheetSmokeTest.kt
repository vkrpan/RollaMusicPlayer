package com.rolla.musicplayer.feature.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.QueueItem
import com.rolla.musicplayer.core.media.QueueState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §15 smoke path "queue sheet, tap an item" (Phase 7 ruling 5). Hosts the stateless [QueueSheet]; the hop from
 * onItemClick to the controller's seek is QueueViewModel's, pinned by QueueViewModelTest on the JVM (ruling P34).
 */
@RunWith(AndroidJUnit4::class)
class QueueSheetSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Control: nothing is reported before the tap (fails under a sheet that reports on open). Mutation: the row passes
    // the current item instead of its own → index 0 → RED.
    @Test
    fun tappingTheSecondItemReportsIndexOne() {
        val tapped = mutableListOf<Int>()
        val queue = QueueState(
            items = listOf(
                QueueItem(index = 0, mediaId = "1", title = "Bohemian Rhapsody", artist = "Queen", artworkUri = ""),
                QueueItem(index = 1, mediaId = "2", title = "So What", artist = "Miles Davis", artworkUri = ""),
            ),
            currentIndex = 0,
        )
        composeTestRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                QueueSheet(queue = queue, onItemClick = { tapped += it.index }, onDismissRequest = {})
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(emptyList<Int>(), tapped)

        composeTestRule.onNodeWithText("So What").performClick()

        assertEquals(listOf(1), tapped)
    }
}
