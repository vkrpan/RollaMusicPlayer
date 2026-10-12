package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Pins ruling 12 (the root folder's "" path is a valid route argument) and the SavedStateHandle key that
// FolderDetailViewModel reads. The plain Application keeps Robolectric from booting RollaApp's real Hilt graph.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FolderDetailRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Review Focus 1 (ruling 12): the root folder's empty path is a valid route argument.
    // Mutation run: rename the route's argument with @SerialName("path") on folderPath → the ViewModel handle's
    // ["folderPath"] is null → RED. (Renaming the Kotlin property itself does not compile: this test and MainActivity
    // name it.)
    @Test
    fun theRootFoldersEmptyPathRoundTrips() = assertRoundTrip("")

    // Review Focus 5. Mutation run: navigate with URLEncoder.encode(path, "UTF-8") (a lossy pre-encoding) → the route
    // argument comes back as "M%C3%BAsica%2FRock+%26+…" → RED. (trim('/') is equivalent here: folder paths are
    // volume-relative and never start or end with '/', so neither fixture changes.)
    @Test
    fun aPathWithReservedAndNonAsciiCharactersRoundTrips() = assertRoundTrip("Música/Rock & Roll #1 ?%/x y")

    private fun assertRoundTrip(path: String) {
        lateinit var navController: NavHostController
        var routeArgument: String? = null
        var savedStateArgument: String? = null
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> {}
                composable<FolderDetail> { entry ->
                    routeArgument = entry.toRoute<FolderDetail>().folderPath
                    // The handle a ViewModel scoped to this entry receives, seeded from the entry's
                    // defaultViewModelCreationExtras (DEFAULT_ARGS_KEY = the route arguments) — the same path
                    // hiltViewModel() takes for FolderDetailViewModel. (entry.savedStateHandle is the nav-result
                    // handle, built with null default args, so it never carries route arguments.)
                    val probe = viewModel(viewModelStoreOwner = entry) { SavedStateProbe(createSavedStateHandle()) }
                    savedStateArgument = probe.handle.get<String>("folderPath")
                }
            }
        }
        composeRule.runOnIdle { navController.navigate(FolderDetail(folderPath = path)) }
        composeRule.waitForIdle()
        assertEquals(path, routeArgument)
        assertEquals(path, savedStateArgument)
    }

    private class SavedStateProbe(val handle: SavedStateHandle) : ViewModel()
}
