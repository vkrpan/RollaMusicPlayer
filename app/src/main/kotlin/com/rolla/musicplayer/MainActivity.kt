package com.rolla.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.feature.library.LibraryRoute
import com.rolla.musicplayer.feature.player.MiniPlayerRoute
import com.rolla.musicplayer.feature.player.MiniPlayerViewModel
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
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val currentSong by miniPlayerViewModel.currentSong.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentSong != null) {
                MiniPlayerRoute(viewModel = miniPlayerViewModel)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Library,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Library> {
                LibraryRoute()
            }
        }
    }
}
