package com.rolla.musicplayer.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/** Every route in the graph. Keep in sync with Routes.kt (MiniPlayerVisibilityTest counts it). */
internal val allRoutes: List<KClass<out Route>> = listOf(
    Home::class, NowPlaying::class, Equalizer::class, PlaylistDetail::class, SmartPlaylist::class,
    AlbumDetail::class, ArtistDetail::class, TagEditor::class, BatchTagEditor::class, Search::class,
    Settings::class, About::class, Licenses::class, Privacy::class,
)

/** Spec §7.3: the pill is hidden on the player itself and on settings, editor and info screens. */
private val routesWithoutMiniPlayer: Set<KClass<out Route>> = setOf(
    NowPlaying::class,
    Equalizer::class,
    Settings::class,
    About::class,
    Licenses::class,
    Privacy::class,
    TagEditor::class,
    BatchTagEditor::class,
)

internal fun showsMiniPlayer(routeClass: KClass<out Route>?): Boolean =
    routeClass != null && routeClass !in routesWithoutMiniPlayer

internal fun NavDestination?.routeClass(): KClass<out Route>? =
    this?.let { destination -> allRoutes.firstOrNull { destination.hasRoute(it) } }
