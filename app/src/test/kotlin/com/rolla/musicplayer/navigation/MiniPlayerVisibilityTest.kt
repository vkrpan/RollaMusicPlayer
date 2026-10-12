package com.rolla.musicplayer.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityTest {

    // Spec §7.3 lists FolderDetail among the screens that show the pill.
    // Mutation: add FolderDetail::class to routesWithoutMiniPlayer → RED.
    @Test
    fun shownOnBrowsingScreens() {
        listOf(
            Home::class,
            Search::class,
            AlbumDetail::class,
            ArtistDetail::class,
            FolderDetail::class,
            PlaylistDetail::class,
            SmartPlaylist::class,
        ).forEach { assertTrue(it.simpleName, showsMiniPlayer(it)) }
    }

    @Test
    fun hiddenOnPlayerSettingsAndEditorScreens() { // spec §7.3
        listOf(
            NowPlaying::class,
            Equalizer::class,
            Settings::class,
            About::class,
            Licenses::class,
            Privacy::class,
            TagEditor::class,
            BatchTagEditor::class,
        ).forEach { assertFalse(it.simpleName, showsMiniPlayer(it)) }
    }

    @Test
    fun hiddenWhenThereIsNoDestination() {
        assertFalse(showsMiniPlayer(null))
    }

    @Test
    fun everyRouteIsClassified() {
        // Pins removals from allRoutes: dropping a route makes routeClass() return null for its destination, which
        // hides the pill there. RouteClassTest checks every destination of the real graph maps to its route.
        assertEquals(15, allRoutes.size)
    }
}
