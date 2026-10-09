package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.appDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Builds the production graph (MainActivity's appDestinations) without composing any destination, so no Hilt graph
// is needed; the plain Application keeps Robolectric from booting RollaApp.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class RouteClassTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun everyDestinationOfTheRealGraphMapsToItsOwnRoute() {
        lateinit var graph: NavGraph
        composeRule.setContent {
            val navController = rememberNavController()
            SharedTransitionLayout {
                val sharedTransitionScope = this
                // Built directly (no remember): this host composes once, and lint can't type createGraph's result
                // inside remember (RememberReturnType false positive).
                graph = navController.createGraph(startDestination = Home) {
                    appDestinations(navController = navController, sharedTransitionScope = sharedTransitionScope)
                }
            }
        }
        composeRule.waitForIdle()

        val destinations = graph.toList()
        assertEquals("destinations in the app graph", allRoutes.size, destinations.size)
        destinations.forEach { destination ->
            val routeClass = destination.routeClass()
            assertNotNull("routeClass() of ${destination.route}", routeClass)
            // A type-safe route is "<serial name>", "<serial name>/{arg}" or "<serial name>?arg={arg}".
            val serialName = destination.route.orEmpty().substringBefore('/').substringBefore('?')
            assertEquals("routeClass() of ${destination.route}", serialName, routeClass?.qualifiedName)
        }
    }
}
