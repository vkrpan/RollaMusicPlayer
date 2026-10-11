@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.settingSubtitle
import com.rolla.musicplayer.core.designsystem.theme.settingTitle
import com.rolla.musicplayer.core.designsystem.theme.settingValue

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

/**
 * One settings-style card: the app name with its version below in settingValue blue (the value-row pattern), then
 * the tagline as a gray block under a row divider.
 */
// One flat scaffold/card layout; splitting it would only scatter three Text calls.
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
        topBar = { OneUiDetailTopBar(title = "About", onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(RollaDimens.settingsSectionGap))
            SettingsSectionCard {
                SettingsTextBlock {
                    Text(
                        text = "RollaMusicPlayer",
                        style = MaterialTheme.typography.settingTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Version ${buildInfo.versionName} (${buildInfo.versionCode})",
                        style = MaterialTheme.typography.settingValue,
                        color = MaterialTheme.colorScheme.accentText,
                    )
                }
                SettingsRowDivider()
                SettingsTextBlock {
                    Text(
                        text = ABOUT_TAGLINE,
                        style = MaterialTheme.typography.settingSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(RollaDimens.settingsSectionGap))
        }
    }
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
