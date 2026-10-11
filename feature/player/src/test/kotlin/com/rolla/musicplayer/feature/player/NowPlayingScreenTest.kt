package com.rolla.musicplayer.feature.player

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A 360 dp screen and NATIVE graphics: the screen is measured against the One UI geometry and holds text. The host
// matches NowPlayingScreenSmokeTest: SharedTransitionLayout + AnimatedContent around the production NowPlayingScreen.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The target artist differs from the playing song's artist, so the item must read its target, not the song.
    // Mutations: swap the album/artist items → RED; wire Settings to onEqualizerClick → RED; take the artist from
    // song.artist → RED; drop expanded = false from one item (the menu stays open) → RED.
    @Test
    fun overflowItemsOpenTheirTargetsAndCloseTheMenu() {
        val calls = mutableListOf<String>()
        setScreen(
            targets = NowPlayingOverflowTargets(songId = 42L, albumId = 7L, artistName = "Target Artist"),
            onEditTags = { calls += "tags:$it" },
            onAlbum = { calls += "album:$it" },
            onArtist = { calls += "artist:$it" },
            onSettings = { calls += "settings" },
            onEqualizer = { calls += "equalizer" },
        )
        listOf("Edit tags", "Go to album", "Go to artist", "Settings").forEach { item ->
            composeRule.onNodeWithContentDescription("More options").performClick()
            composeRule.onNodeWithText(item).performClick()
            composeRule.onNodeWithText("Go to album").assertDoesNotExist()
        }
        assertEquals(listOf("tags:42", "album:7", "artist:Target Artist", "settings"), calls)
    }

    // Review Focus 3, a song that left the library: no song id and no album id, so only items with a target stay
    // enabled. Mutations: ignore a null target (always enabled) → RED; disable every item when albumId is null → RED.
    @Test
    fun overflowItemsWithoutATargetAreDisabled() {
        setScreen(targets = NowPlayingOverflowTargets(songId = null, albumId = null, artistName = "Target Artist"))
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Edit tags").assertIsNotEnabled()
        composeRule.onNodeWithText("Go to album").assertIsNotEnabled()
        composeRule.onNodeWithText("Go to artist").assertIsEnabled()
        composeRule.onNodeWithText("Settings").assertIsEnabled()
    }

    // Mutations: leave Volume or Queue as onClick = {} → RED (spec §12).
    @Test
    fun volumeAndQueueAreWired() {
        var volume = 0
        var queue = 0
        setScreen(onVolume = { volume++ }, onQueue = { queue++ })
        composeRule.onNodeWithContentDescription("Volume").performClick()
        composeRule.onNodeWithContentDescription("Queue").performClick()
        assertEquals(1, volume)
        assertEquals(1, queue)
    }

    // Every state of both toggles, driven through one recomposing screen.
    // Mutations: put the state into contentDescription ("Shuffle on") instead of stateDescription → RED; swap the
    // Repeat OFF/ALL labels → RED; a constant "Shuffle on" → RED.
    @Test
    fun shuffleAndRepeatExposeStateDescriptions() {
        val inputs = NowPlayingTestInputs()
        setScreen(inputs = inputs)
        mapOf(ShuffleMode.OFF to "Shuffle off", ShuffleMode.ON to "Shuffle on").forEach { (mode, state) ->
            inputs.shuffle = mode
            composeRule.onNodeWithContentDescription("Shuffle")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
        }
        val repeatStates = mapOf(
            RepeatMode.OFF to "Repeat off",
            RepeatMode.ALL to "Repeat all",
            RepeatMode.ONE to "Repeat one",
        )
        repeatStates.forEach { (mode, state) ->
            inputs.repeat = mode
            composeRule.onNodeWithContentDescription("Repeat")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
        }
    }

    // Mutation: keep the old fillMaxWidth().aspectRatio(1f) artwork → RED.
    @Test
    fun artworkIsNpArtSizeAndCentered() {
        setScreen()
        val art = composeRule.onNodeWithTag(NOW_PLAYING_ARTWORK_TAG, useUnmergedTree = true).getBoundsInRoot()
        art.width.assertIsEqualTo(RollaDimens.npArtSize, "art width", tolerance = 0.5.dp)
        ((art.left + art.right) / 2).assertIsEqualTo(180.dp, "art centre x", tolerance = 0.5.dp)
    }

    // The no-fill half lives in NowPlayingScreenPixelTest.playHasNoCircleFill.
    // Mutations: keep the 72 dp circle (or a 48 dp IconButton) → RED; drop role = Role.Button → RED.
    @Test
    fun playIsA64DpButton() {
        setScreen()
        val play = composeRule.onNodeWithContentDescription("Play")
        play.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val bounds = play.getBoundsInRoot()
        (bounds.right - bounds.left).assertIsEqualTo(RollaDimens.npPlayTouchTarget, "play target", tolerance = 0.5.dp)
    }

    // Mutation: rename the seekbar description → RED (BaselineProfileGenerator waits on it).
    @Test
    fun seekbarKeepsItsContentDescription() {
        setScreen()
        composeRule.onNodeWithContentDescription("Playback position").assertExists()
    }

    // Review Focus 4. Mutation: drop verticalScroll from the content column → performScrollTo fails → RED.
    @Test
    fun transportRowIsReachableAtFontScale2() {
        setScreen(fontScale = 2f)
        composeRule.onNodeWithTag(NOW_PLAYING_TRANSPORT_TAG).performScrollTo().assertIsDisplayed()
    }

    // Review Focus 4 ("nothing overlaps the seekbar"). At font scale 2 the time labels are taller than the slack under
    // the thumb in a 48 dp box, so they must sit below the thumb rather than be pinned to the box bottom.
    // Mutation: the fixed-height Box with the labels aligned BottomCenter → RED.
    @Test
    fun timeLabelsClearTheSeekThumbAtFontScale2() {
        setScreen(fontScale = 2f)
        composeRule.onNodeWithText(DURATION_LABEL).performScrollTo()
        val bar = composeRule.onNodeWithContentDescription("Playback position").getBoundsInRoot()
        val label = composeRule.onNodeWithText(DURATION_LABEL).getBoundsInRoot()
        val thumbBottom = (bar.top + bar.bottom) / 2 + RollaDimens.npSeekThumb / 2
        assertTrue("label top ${label.top} overlaps the thumb (bottom $thumbBottom)", label.top >= thumbBottom - 0.5.dp)
    }

    // At font scale 1 the labels keep the spec's place: the bottom of the seekbar's 48 dp touch box. The slider's
    // semantics bounds are its drawn (thumb-high) size, not the 48 dp box, so the box bottom derives from the centre.
    // Mutation: drop align(BottomCenter) from the labels (they end 2 dp above the box bottom) → RED.
    @Test
    fun timeLabelsSitAtTheSeekBoxBottomAtFontScale1() {
        setScreen()
        val bar = composeRule.onNodeWithContentDescription("Playback position").getBoundsInRoot()
        val label = composeRule.onNodeWithText(DURATION_LABEL).getBoundsInRoot()
        val boxBottom = (bar.top + bar.bottom) / 2 + RollaDimens.minTouchTarget / 2
        label.bottom.assertIsEqualTo(boxBottom, "label bottom", tolerance = 0.5.dp)
    }

    // The top spacer subtracts the status bar and the header, so the artwork lands npArtTop below the screen top.
    // Mutation: drop the top spacer (art directly under the 64 dp header) → RED.
    @Test
    fun artworkTopIsNpArtTop() {
        setScreen()
        val art = composeRule.onNodeWithTag(NOW_PLAYING_ARTWORK_TAG, useUnmergedTree = true).getBoundsInRoot()
        art.top.assertIsEqualTo(RollaDimens.npArtTop, "art top", tolerance = 0.5.dp)
    }

    // Step 6: the weighted spacer pushes the controls down, so the transport row ends npBottomGap above the bottom.
    // 800 dp tall: at 640 dp Robolectric's root is 584 dp (system bars), 5 dp shorter than the fixed content, so the
    // spacer is already zero there and the screen scrolls; a taller screen gives the spacer slack to prove itself.
    // Mutation: delete Spacer(Modifier.weight(1f)) (controls stack right under the artist) → RED.
    @Config(qualifiers = "w360dp-h800dp")
    @Test
    fun controlsArePushedToTheBottom() {
        setScreen()
        val root = composeRule.onRoot().getBoundsInRoot()
        val transport = composeRule.onNodeWithTag(NOW_PLAYING_TRANSPORT_TAG).getBoundsInRoot()
        transport.bottom.assertIsEqualTo(root.bottom - RollaDimens.npBottomGap, "transport bottom", tolerance = 0.5.dp)
    }

    // The host lives in NowPlayingTestHost.kt (shared with NowPlayingScreenPixelTest).
    @Suppress("LongParameterList")
    private fun setScreen(
        targets: NowPlayingOverflowTargets = NowPlayingOverflowTargets(),
        inputs: NowPlayingTestInputs = NowPlayingTestInputs(),
        fontScale: Float = 1f,
        onEditTags: (Long) -> Unit = {},
        onAlbum: (Long) -> Unit = {},
        onArtist: (String) -> Unit = {},
        onSettings: () -> Unit = {},
        onEqualizer: () -> Unit = {},
        onVolume: () -> Unit = {},
        onQueue: () -> Unit = {},
    ) = composeRule.setNowPlayingScreen(
        inputs = inputs,
        targets = targets,
        fontScale = fontScale,
        onEditTags = onEditTags,
        onAlbum = onAlbum,
        onArtist = onArtist,
        onSettings = onSettings,
        onEqualizer = onEqualizer,
        onVolume = onVolume,
        onQueue = onQueue,
    )

    private companion object {
        const val DURATION_LABEL = "3:20"
    }
}
