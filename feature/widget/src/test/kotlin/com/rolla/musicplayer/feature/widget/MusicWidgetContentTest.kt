package com.rolla.musicplayer.feature.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

// One row as the Motorola edge 70 draws it (97.7 dp), wide enough to show the artwork, and under the 98.58 dp centred
// threshold (ruling P14), so these tests render the device-verified filled mode.
private val DEFAULT_SIZE = DpSize(375.dp, 98.dp)
private const val CONTROL_COUNT = 5

/**
 * The widget card's Glance tree (Phase 6c Task 3): copy, the five transport controls with their callbacks, the artwork
 * box and its hiding below 339 dp, and the card's open-app action.
 *
 * Glance's `assertExists`, `assertDoesNotExist` and `assertCountEquals` take no message argument; their failure text
 * names the matcher and the count found, so each RED still quotes on its own. `assert` takes a message lambda, which
 * these tests pass.
 *
 * Glance's public test matchers see text, descriptions, test tags and click actions, not size modifiers or alignment,
 * so they cannot tell ruling P14's centred mode from the filled one; the device check (Motorola edge 70, 2026-10-27)
 * covers that. The narrow test renders at 110 dp (centred) and the rest at [DEFAULT_SIZE] (filled), so both trees
 * render their copy and controls.
 */
@RunWith(AndroidJUnit4::class)
class MusicWidgetContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Stub: always the play icon. Mutation: invert isPlaying → "Pause" is missing while playing → RED.
    @Test
    fun playingStateShowsTitleArtistAndPause() = runGlanceAppWidgetUnitTest {
        render(MusicWidgetState(title = "Song A", artist = "Artist A", isPlaying = true))

        onNode(hasText("Song A")).assertExists()
        onNode(hasText("Artist A")).assertExists()
        onNode(hasContentDescriptionEqualTo("Pause") and hasRunCallbackClickAction<PlayPauseActionCallback>())
            .assertExists()
        onNode(hasContentDescriptionEqualTo("Play")).assertDoesNotExist()
    }

    // Stub: always the pause icon. Mutation: invert isPlaying → "Play" is missing while paused → RED.
    @Test
    fun pausedStateShowsPlay() = runGlanceAppWidgetUnitTest {
        render(MusicWidgetState(title = "Song A", artist = "Artist A", isPlaying = false))

        onNode(hasContentDescriptionEqualTo("Play") and hasRunCallbackClickAction<PlayPauseActionCallback>())
            .assertExists()
        onNode(hasContentDescriptionEqualTo("Pause")).assertDoesNotExist()
    }

    // Stub: no tagged controls. Mutation: swap the previous and next callbacks → node 0 pairs "Previous track" with
    // NextActionCallback → RED (description and callback are matched together, so a swap cannot pass).
    @Test
    fun controlsAreInTransportOrderWithTheirCallbacks() = runGlanceAppWidgetUnitTest {
        render(MusicWidgetState(title = "Song A", artist = "Artist A", isPlaying = false))

        val controls = onAllNodes(hasTestTag(WIDGET_CONTROL_TAG))
        controls.assertCountEquals(CONTROL_COUNT)
        val expected = listOf(
            "Previous track" to hasRunCallbackClickAction<PreviousActionCallback>(),
            "Skip back 15 seconds" to hasRunCallbackClickAction<SkipBack15ActionCallback>(),
            "Play" to hasRunCallbackClickAction<PlayPauseActionCallback>(),
            "Skip forward 15 seconds" to hasRunCallbackClickAction<SkipForward15ActionCallback>(),
            "Next track" to hasRunCallbackClickAction<NextActionCallback>(),
        )
        expected.forEachIndexed { index, (description, callback) ->
            controls[index].assert(hasContentDescriptionEqualTo(description) and callback) {
                "control $index is \"$description\" with its callback"
            }
        }
    }

    // Stub: never render the empty copy. Mutation: keep the old "Open RollaMusicPlayer…" string → RED.
    @Test
    fun emptyStateShowsNothingPlayingCopy() = runGlanceAppWidgetUnitTest {
        render(MusicWidgetState())

        onNode(hasText("Nothing playing")).assertExists()
        onNode(hasText("Open Rolla Music to start playback")).assertExists()
        onNode(hasContentDescriptionEqualTo("No album artwork")).assertExists()
        onAllNodes(hasTestTag(WIDGET_CONTROL_TAG)).assertCountEquals(CONTROL_COUNT)
    }

    // Stub: always the placeholder. Mutation: ignore artwork → "Album artwork for Song A" is missing → RED.
    @Test
    fun artworkBitmapReplacesThePlaceholder() {
        val state = MusicWidgetState(title = "Song A", artist = "Artist A")
        runGlanceAppWidgetUnitTest {
            render(state, artwork = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

            onNode(hasContentDescriptionEqualTo("Album artwork for Song A")).assertExists()
            onNode(hasContentDescriptionEqualTo("No album artwork")).assertDoesNotExist()
        }
        // Control: the same content without a bitmap shows the placeholder.
        runGlanceAppWidgetUnitTest {
            render(state, artwork = null)

            onNode(hasContentDescriptionEqualTo("No album artwork")).assertExists()
            onNode(hasContentDescriptionEqualTo("Album artwork for Song A")).assertDoesNotExist()
        }
    }

    // Stub: always draw the art. Mutation: draw the bitmap Image outside `if (layout.showArtwork)` → "Album artwork
    // for Song A" shows at 338.9 dp → RED. A real bitmap, so the "Album artwork for" absence can fail.
    @Test
    fun narrowWidgetHidesArtworkAndKeepsFiveControls() {
        val state = MusicWidgetState(title = "Song A", artist = "Artist A")
        val artwork = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        runGlanceAppWidgetUnitTest {
            render(state, artwork = artwork, size = DpSize(338.9.dp, 110.dp))

            onNode(hasContentDescriptionEqualTo("No album artwork")).assertDoesNotExist()
            onNode(hasContentDescription("Album artwork for")).assertDoesNotExist()
            onAllNodes(hasTestTag(WIDGET_CONTROL_TAG)).assertCountEquals(CONTROL_COUNT)
        }
        // Control: from 339 dp the artwork box is drawn, with the same bitmap.
        runGlanceAppWidgetUnitTest {
            render(state, artwork = artwork, size = DpSize(339.dp, 110.dp))

            onNode(hasContentDescriptionEqualTo("Album artwork for Song A")).assertExists()
        }
    }

    // Stub: no open-app node. Mutation: keep the old "Open RollaMusicPlayer" string → RED.
    @Test
    fun cardOpensRollaMusic() = runGlanceAppWidgetUnitTest {
        render(MusicWidgetState(title = "Song A", artist = "Artist A"))

        val mainActivity = ComponentName(context.packageName, "com.rolla.musicplayer.MainActivity")
        onNode(hasContentDescriptionEqualTo("Open Rolla Music")).assert(hasStartActivityClickAction(mainActivity)) {
            "the card opens MainActivity"
        }
    }

    private fun GlanceAppWidgetUnitTest.render(
        state: MusicWidgetState,
        artwork: Bitmap? = null,
        size: DpSize = DEFAULT_SIZE,
    ) {
        setContext(context)
        provideComposable {
            CompositionLocalProvider(LocalSize provides size) {
                GlanceTheme(colors = RollaWidgetColors) {
                    MusicWidgetContent(context = context, state = state, artwork = artwork)
                }
            }
        }
    }
}
