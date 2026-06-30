package com.rolla.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.feature.library.LibraryRoute
import com.rolla.musicplayer.navigation.Library
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RollaMusicPlayerTheme {
                RollaNavHost()
            }
        }
    }
}

@Composable
private fun RollaNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Library,
    ) {
        composable<Library> {
            LibraryRoute()
        }
    }
}
