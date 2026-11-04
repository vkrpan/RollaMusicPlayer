package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.artistDetailDestination
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.testing.FakeArtistRepository
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.feature.library.ArtistDetailViewModel
import com.rolla.musicplayer.feature.library.ArtistsTab
import com.rolla.musicplayer.feature.library.ArtistsTabViewModel
import com.rolla.musicplayer.feature.library.ScanState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review Focus 2 (Phase 7 ruling 3): the Artists tab shows MediaStore's `<unknown>` artist as "Unknown", and tapping
 * that row still opens ArtistDetail keyed by the raw `<unknown>` (artists are keyed by name, Phase 4a ruling 12).
 *
 * Harness (ruling P2): Home's real destination needs Hilt (HomeViewModel and every tab's hiltViewModel()), so the
 * start destination hosts the real Artists tab (`ArtistsTab` with its real row click wiring and a real
 * ArtistsTabViewModel over a fake repository) and forwards the name exactly as MainActivity's `composable<Home>` does
 * (`onArtistClick = { name -> navController.navigate(ArtistDetail(artistName = name)) { launchSingleTop = true } }`,
 * a pass-through of the name). ArtistDetail is MainActivity's real `artistDetailDestination`, whose ViewModel reads its
 * SavedStateHandle from the back-stack entry, so the repository sees the name the route carried.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistDetailRouteUnknownTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    // Trivial stub: the row's click does nothing → ArtistDetail never becomes current → RED. Mutation: route with the
    // display name (ArtistsTab passes artistDisplayName(artist.name)) → ArtistDetail("Unknown") → RED on the route arg.
    @Test
    fun tappingTheUnknownArtistOpensArtistDetailKeyedByTheRawName() {
        val tabRepository = FakeArtistRepository().apply {
            emitArtists(listOf(unknownArtist(), Artist(id = 2L, name = "Sade", albumCount = 1, songCount = 2)))
        }
        val detailRepository = FakeArtistRepository().apply { emitArtist(unknownArtist()) }
        setGraph(tabRepository, detailRepository)
        assertEquals("control: the Artists tab shows the <unknown> artist as Unknown", 1, textCount("Unknown"))
        assertEquals("control: the Artists tab never shows the raw <unknown>", 0, textCount("<unknown>"))

        composeRule.onNodeWithText("Unknown").performClick()
        composeRule.waitForIdle()

        assertArtistDetailIsKeyedByTheRawName(detailRepository)
        assertEquals("ArtistDetail's title shows the artist as Unknown", 1, textCount("Unknown"))
        assertEquals("ArtistDetail never shows the raw <unknown>", 0, textCount("<unknown>"))
    }

    /** Home hosts the real Artists tab; ArtistDetail is MainActivity's real destination. */
    private fun setGraph(tabRepository: FakeArtistRepository, detailRepository: FakeArtistRepository) {
        composeRule.setContent {
            navController = rememberNavController()
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides 0.dp) {
                    NavHost(navController = navController, startDestination = Home, modifier = Modifier.fillMaxSize()) {
                        artistsTabHome(tabRepository)
                        composable<TagEditor> {}
                        artistDetailDestination(navController) { artistDetailViewModel(detailRepository) }
                    }
                }
            }
        }
    }

    /** MainActivity's `composable<Home>` Artists exit: the tapped row's name goes into ArtistDetail unchanged. */
    private fun NavGraphBuilder.artistsTabHome(tabRepository: FakeArtistRepository) {
        composable<Home> {
            ArtistsTab(
                scanState = ScanState.Done(added = 0, removed = 0),
                onArtistClick = { name ->
                    navController.navigate(ArtistDetail(artistName = name)) { launchSingleTop = true }
                },
                viewModel = viewModel { ArtistsTabViewModel(tabRepository) },
            )
        }
    }

    /** Built in the back-stack entry's store, so its SavedStateHandle carries the route's artistName. */
    @Composable
    private fun artistDetailViewModel(repository: FakeArtistRepository): ArtistDetailViewModel = viewModel {
        ArtistDetailViewModel(
            savedStateHandle = createSavedStateHandle(),
            artistRepository = repository,
            playbackController = mockk<PlaybackController>(relaxed = true),
            playlistRepository = FakePlaylistRepository(),
        )
    }

    private fun assertArtistDetailIsKeyedByTheRawName(detailRepository: FakeArtistRepository) {
        composeRule.runOnIdle {
            val entry = navController.currentBackStackEntry
            assertTrue("tapping Unknown opens ArtistDetail", entry?.destination?.hasRoute<ArtistDetail>() == true)
            assertEquals(
                "ArtistDetail is keyed by the raw <unknown>, not the display name",
                UNKNOWN,
                entry?.toRoute<ArtistDetail>()?.artistName,
            )
            assertEquals(
                "ArtistDetail's ViewModel asks the repository for the raw <unknown>",
                listOf(UNKNOWN),
                detailRepository.requestedArtistNames,
            )
        }
    }

    private fun unknownArtist() = Artist(id = 1L, name = UNKNOWN, albumCount = 1, songCount = 3)

    private fun textCount(text: String): Int =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        const val UNKNOWN = "<unknown>"
    }
}
