@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.screenTitle

private val ContentPadding = 16.dp
private val AppNameSpacing = 8.dp
private val TaglineSpacing = 16.dp

private const val ABOUT_TAGLINE =
    "A fully offline, privacy-first music player. Your music never leaves your device."

/**
 * Version identity of the installed app, resolved by the caller (MainActivity reads its own
 * PackageInfo -- feature modules can't see the app module's BuildConfig, and PackageManager works
 * regardless of whether the buildConfig build feature is enabled).
 */
data class AppBuildInfo(
    val versionName: String,
    val versionCode: Long,
)

/**
 * The About screen, reached from Settings' "About" row (see MainActivity's `composable<About>`,
 * which resolves [buildInfo] from PackageInfo and passes it in).
 */
@Composable
fun AboutRoute(
    buildInfo: AppBuildInfo,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AboutScreen(buildInfo = buildInfo, onNavigateUp = onNavigateUp, modifier = modifier)
}

@Suppress("LongMethod")
@Composable
private fun AboutScreen(
    buildInfo: AppBuildInfo,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AboutTopBar(onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(ContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "RollaMusicPlayer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(AppNameSpacing))
                Text(
                    text = "Version ${buildInfo.versionName} (${buildInfo.versionCode})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.accentText,
                )
                Spacer(Modifier.height(TaglineSpacing))
                Text(
                    text = ABOUT_TAGLINE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTopBar(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "About",
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
@Preview(name = "About - Light")
@Preview(name = "About - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAboutScreen() {
    RollaMusicPlayerTheme {
        AboutScreen(
            buildInfo = AppBuildInfo(versionName = "1.0", versionCode = 1L),
            onNavigateUp = {},
        )
    }
}
