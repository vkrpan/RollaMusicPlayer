package com.rolla.musicplayer.feature.player

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    // Phase 7 calibration: the artwork lands npArtTopGap below the top of the window content, i.e. below the status bar
    // (npArtTop's screen-top anchoring is gone). The test sends a status bar, so "below the status bar" and "from the
    // screen top" differ. Control: the status bar is really there (the top bar's chevron sits below it).
    // Mutations: the old screen-top anchoring, npArtTopGap - statusBarTop - headerHeight → art top 95 dp → RED; drop
    // the top spacer (art directly under the 64 dp header) → RED.
    @Test
    fun artworkTopIsNpArtTopGapBelowTheStatusBar() {
        lateinit var view: View
        setScreen(onView = { view = it })
        val statusBar = 24.dp
        composeRule.runOnIdle {
            val px = with(composeRule.density) { statusBar.roundToPx() }
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px, 0, 0))
                    .build(),
            )
        }
        val chevron = composeRule.onNodeWithContentDescription("Close player").getBoundsInRoot()
        assertTrue(
            "control: the top bar sits below the $statusBar status bar, at ${chevron.top}",
            chevron.top > statusBar,
        )
        val art = composeRule.onNodeWithTag(NOW_PLAYING_ARTWORK_TAG, useUnmergedTree = true).getBoundsInRoot()
        art.top.assertIsEqualTo(statusBar + RollaDimens.npArtTopGap, "art top", tolerance = 0.5.dp)
    }

    // Phase 7 calibration, measured on the 406.7 dp Motorola and against the 360 dp reference: shuffle's and repeat's
    // centres sit npControlEdgeInset + 24 dp (43 dp) from the edges, previous and next npTransportInnerCentre (111 dp)
    // from the nearer edge, play centred. One composition: the screen width flips from 406.7 to 360 dp.
    // Mutation: Arrangement.SpaceEvenly over the full width (the old row) → shuffle at 49.1 dp → RED.
    @Config(qualifiers = "w411dp-h800dp")
    @Test
    fun nowPlayingTransportSitsAtTheReferenceInsets() {
        var width by mutableStateOf(MOTOROLA_WIDTH)
        setScreen(screenWidth = { width })
        assertTransportAtTheReferenceInsets(MOTOROLA_WIDTH)
        width = REFERENCE_WIDTH
        composeRule.waitForIdle()
        assertTransportAtTheReferenceInsets(REFERENCE_WIDTH)
    }

    // Ruling P22 (device re-measure): the action row's + draws in npActionAddGlyph and the transport's previous and
    // next in npSkipGlyph; Queue keeps npActionGlyph and shuffle npTransportGlyph. The unmerged Icon node's bounds are
    // the glyph box. Control: Queue's and shuffle's boxes differ from the new ones, so each check reads its own token.
    // Mutations: the + back on npActionGlyph → RED; previous back on npTransportGlyph → RED.
    @Test
    fun addAndSkipGlyphBoxesUseTheirTokens() {
        setScreen()
        assertTrue("control: + box differs from Queue's", iconWidth("Queue") != RollaDimens.npActionAddGlyph)
        assertTrue("control: skip box differs from shuffle's", iconWidth("Shuffle") != RollaDimens.npSkipGlyph)
        iconWidth("Add to playlist").assertIsEqualTo(RollaDimens.npActionAddGlyph, "+ glyph box", tolerance = 0.5.dp)
        iconWidth("Previous").assertIsEqualTo(RollaDimens.npSkipGlyph, "previous glyph box", tolerance = 0.5.dp)
        iconWidth("Next").assertIsEqualTo(RollaDimens.npSkipGlyph, "next glyph box", tolerance = 0.5.dp)
    }

    // Ruling P21: the transport stays left to right in an RTL layout (Material keeps media playback controls
    // unmirrored): shuffle on the left, repeat on the right, previous left of play. Control: the RTL layout reaches the
    // screen, so the top bar's close chevron moves to the right half. Mutation: TransportLayout places its children
    // with placeRelative → shuffle on the right → RED.
    @Test
    fun transportStaysLeftToRightInRtl() {
        setScreen(layoutDirection = LayoutDirection.Rtl)
        assertTrue("control: RTL mirrors the top bar's close chevron", centreX("Close player") > SCREEN_WIDTH / 2)
        val edgeCentre = RollaDimens.npControlEdgeInset + RollaDimens.minTouchTarget / 2
        centreX("Shuffle").assertIsEqualTo(edgeCentre, "RTL: shuffle centre from the left", tolerance = 1.dp)
        (SCREEN_WIDTH - centreX("Repeat")).assertIsEqualTo(edgeCentre, "RTL: repeat centre from the right", 1.dp)
        assertTrue("RTL: previous left of play", centreX("Previous") < centreX("Play"))
    }

    // Phase 7 calibration: the action row's end 48 dp boxes sit npControlEdgeInset from the edges (centres 43 dp in);
    // the middle heart stays centred. Mutation: pad the row by npSeekInset (the old row) → Queue centre 53 dp → RED.
    @Test
    fun actionRowEndsSitAtTheControlInset() {
        setScreen()
        val edgeCentre = RollaDimens.npControlEdgeInset + RollaDimens.minTouchTarget / 2
        centreX("Queue").assertIsEqualTo(edgeCentre, "Queue centre from the start", tolerance = 1.dp)
        (SCREEN_WIDTH - centreX("Add to playlist")).assertIsEqualTo(edgeCentre, "Add centre from the end", 1.dp)
        centreX("Add to favourites").assertIsEqualTo(SCREEN_WIDTH / 2, "heart centre", tolerance = 1.dp)
    }

    // Phase 7 calibration: the action glyphs draw in npActionGlyph boxes and the transport side glyphs in
    // npTransportGlyph boxes; the unmerged Icon node's bounds are the box. Control: the two tokens differ, so the two
    // assertions read different boxes. Mutations: drop iconSize from the Queue button (24 dp) → RED; give the shuffle
    // button npActionGlyph → RED.
    @Test
    fun actionAndTransportGlyphBoxesUseTheirTokens() {
        setScreen()
        assertTrue("control: the glyph tokens differ", RollaDimens.npActionGlyph != RollaDimens.npTransportGlyph)
        iconWidth("Queue").assertIsEqualTo(RollaDimens.npActionGlyph, "Queue glyph box", tolerance = 0.5.dp)
        iconWidth("Shuffle").assertIsEqualTo(RollaDimens.npTransportGlyph, "Shuffle glyph box", tolerance = 0.5.dp)
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

    private fun assertTransportAtTheReferenceInsets(width: Dp) {
        val edgeCentre = RollaDimens.npControlEdgeInset + RollaDimens.minTouchTarget / 2
        val inner = RollaDimens.npTransportInnerCentre
        centreX("Shuffle").assertIsEqualTo(edgeCentre, "at $width: shuffle centre from the start", tolerance = 1.dp)
        centreX("Previous").assertIsEqualTo(inner, "at $width: previous centre from the start", tolerance = 1.dp)
        centreX("Play").assertIsEqualTo(width / 2, "at $width: play centre", tolerance = 1.dp)
        (width - centreX("Next")).assertIsEqualTo(inner, "at $width: next centre from the end", tolerance = 1.dp)
        (width - centreX("Repeat")).assertIsEqualTo(edgeCentre, "at $width: repeat centre from the end", 1.dp)
    }

    private fun centreX(description: String): Dp {
        val bounds = composeRule.onNodeWithContentDescription(description).getBoundsInRoot()
        return (bounds.left + bounds.right) / 2
    }

    /** The width of the unmerged Icon node that carries [description]: the glyph's box. */
    private fun iconWidth(description: String): Dp =
        composeRule.onNodeWithContentDescription(description, useUnmergedTree = true).getBoundsInRoot().width

    // The host lives in NowPlayingTestHost.kt (shared with NowPlayingScreenPixelTest).
    @Suppress("LongParameterList")
    private fun setScreen(
        targets: NowPlayingOverflowTargets = NowPlayingOverflowTargets(),
        inputs: NowPlayingTestInputs = NowPlayingTestInputs(),
        fontScale: Float = 1f,
        screenWidth: () -> Dp = { Dp.Unspecified },
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        onView: (View) -> Unit = {},
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
        screenWidth = screenWidth,
        layoutDirection = layoutDirection,
        onView = onView,
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

        // The class's 360 dp screen, and the two widths the Phase 7 calibration compared (Motorola, reference).
        val SCREEN_WIDTH = 360.dp
        val MOTOROLA_WIDTH = 406.7.dp
        val REFERENCE_WIDTH = 360.dp
    }
}
