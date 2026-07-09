@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.screenTitle

private val ContentPadding = 16.dp

/**
 * Placeholder for the open-source-licenses screen, reached from Settings' "Open-source licenses"
 * row (see MainActivity's `composable<Licenses>`).
 *
 * Prompt 6 fills this in with the real license list -- ui-builder's job. This stub exists only so
 * the Settings -> Licenses navigation edge is real and testable now: a TopAppBar with a back arrow
 * plus a single placeholder line, tokens only, no ViewModel yet.
 */
@Composable
fun LicensesRoute(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    LicensesScreen(onNavigateUp = onNavigateUp, modifier = modifier)
}

@Composable
private fun LicensesScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LicensesTopBar(onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(ContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Open-source licenses -- coming soon",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesTopBar(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "Open-source licenses",
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Licenses - Light")
@Preview(name = "Licenses - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLicensesScreen() {
    RollaMusicPlayerTheme {
        LicensesScreen(onNavigateUp = {})
    }
}
