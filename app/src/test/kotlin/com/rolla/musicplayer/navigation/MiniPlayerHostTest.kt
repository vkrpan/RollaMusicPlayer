package com.rolla.musicplayer.navigation

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.feature.player.MiniPlayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics + a 360 dp screen: the real-pill test measures a row holding text. The plain Application keeps
// Robolectric from booting RollaApp's Hilt graph; nothing here needs it. Robolectric reports no system insets, so the
// navigation-bar and IME tests dispatch their own to the compose view.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniPlayerHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var contentInset: Dp = UNREAD

    @Test
    fun contentReadsThePillsMeasuredHeightWhileShown() {
        setHost(showPill = { true })

        assertDpEquals(FAKE_PILL_HEIGHT, contentInset)
    }

    @Test
    fun contentReadsZeroOnceThePillIsHidden() {
        var showPill by mutableStateOf(true)
        setHost(showPill = { showPill })
        assertDpEquals(FAKE_PILL_HEIGHT, contentInset)

        showPill = false
        composeRule.waitForIdle()

        // The pill was measured before it hid, so a host that keeps providing that height fails here.
        assertDpEquals(0.dp, contentInset)
    }

    @Test
    fun contentReadsZeroWhileTheImeIsVisible() {
        setHost(showPill = { true })
        assertDpEquals(FAKE_PILL_HEIGHT, contentInset)

        dispatchInsets(
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px(IME_HEIGHT)))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build(),
        )
        assertDpEquals(0.dp, contentInset)

        dispatchInsets(NO_IME)
        assertDpEquals(FAKE_PILL_HEIGHT, contentInset)
    }

    @Test
    fun theRealPillReportsItsOwnFootprintExcludingTheNavigationBar() {
        setRealPill()
        dispatchInsets(
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(NAV_BAR_HEIGHT)))
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build(),
        )

        // The dispatched bar reached the host: the pill now sits the bar's height above the host's bottom edge.
        val hostBottom = composeRule.onNodeWithTag(HOST_TAG).getUnclippedBoundsInRoot().bottom
        val pillBottom = composeRule.onNodeWithTag(PILL_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
        assertEquals("pill lifted above the navigation bar", NAV_BAR_HEIGHT.value, (hostBottom - pillBottom).value, TOL)
        // Today's pill: a 64 dp minimum-height row plus its own 4 dp top and bottom margins. The bar is padded by the
        // host, outside the measured modifier, so it is never part of the value.
        assertDpEquals(REAL_PILL_FOOTPRINT, contentInset)
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun setRealPill() {
        composeRule.setContent {
            RollaMusicPlayerTheme {
                SharedTransitionLayout {
                    MiniPlayerHost(
                        showPill = true,
                        modifier = Modifier.testTag(HOST_TAG),
                        pill = { measure ->
                            // The tag sits on a wrapping Box: tagging the pill's own chain would merge it into the
                            // row's inner semantics node, whose bounds exclude the pill's margins.
                            val visibilityScope = this
                            Box(modifier = Modifier.testTag(PILL_TAG)) {
                                RealPill(this@SharedTransitionLayout, visibilityScope, measure)
                            }
                        },
                    ) {
                        contentInset = LocalMiniPlayerInset.current
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun RealPill(
        sharedTransitionScope: SharedTransitionScope,
        animatedVisibilityScope: AnimatedVisibilityScope,
        modifier: Modifier,
    ) {
        MiniPlayer(
            song = SONG,
            isPlaying = false,
            onPrevious = {},
            onTogglePlayPause = {},
            onNext = {},
            onQueue = {},
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    }

    private fun setHost(showPill: () -> Boolean) {
        composeRule.setContent {
            RollaMusicPlayerTheme {
                MiniPlayerHost(
                    showPill = showPill(),
                    pill = { measure -> Box(measure.fillMaxWidth().height(FAKE_PILL_HEIGHT)) },
                ) {
                    contentInset = LocalMiniPlayerInset.current
                }
            }
        }
        composeRule.waitForIdle()
        dispatchInsets(NO_IME)
    }

    /** Sends [insets] to the ComposeView, where Compose's own insets listener picks them up. */
    private fun dispatchInsets(insets: WindowInsetsCompat) {
        composeRule.runOnUiThread {
            val composeView = composeRule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            ViewCompat.dispatchApplyWindowInsets(composeView, insets)
        }
        composeRule.waitForIdle()
    }

    private fun px(dp: Dp): Int = with(composeRule.density) { dp.roundToPx() }

    private fun assertDpEquals(expected: Dp, actual: Dp) {
        assertEquals("LocalMiniPlayerInset seen by the content", expected.value, actual.value, TOL)
    }

    private companion object {
        const val HOST_TAG = "host"
        const val PILL_TAG = "pill"
        val UNREAD = (-1).dp
        val FAKE_PILL_HEIGHT = 70.dp
        val REAL_PILL_FOOTPRINT = 72.dp
        val NAV_BAR_HEIGHT = 48.dp
        val IME_HEIGHT = 300.dp
        const val TOL = 0.5f

        // Until a view receives its first insets, Compose reports the IME as visible; a device dispatches real insets
        // on attach, Robolectric dispatches none, so each test starts by sending an IME-hidden state.
        val NO_IME: WindowInsetsCompat
            get() = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
        val SONG = Song(
            id = "1",
            title = "Song title",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180_000L,
            trackNumber = 1,
            year = 2024,
            contentUri = "content://media/1",
            artworkUri = "",
        )
    }
}
