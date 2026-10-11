@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.settingSubtitle
import com.rolla.musicplayer.core.designsystem.theme.settingTitle
import com.rolla.musicplayer.core.designsystem.theme.settingValue

private const val LICENSES_FOOTER =
    "This list is bundled with the app and maintained by hand — nothing is fetched. Full license " +
        "texts are available from each project's website."

/**
 * The open-source-licenses screen, reached from Settings' "Open-source licenses" row (see
 * MainActivity's `composable<Licenses>`). Renders [OSS_LIBRARIES] — a curated, compiled-in list;
 * no network fetch anywhere (see that list's maintenance note).
 */
@Composable
fun LicensesRoute(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    LicensesScreen(onNavigateUp = onNavigateUp, modifier = modifier)
}

/**
 * The bundled list as one settings-style card (rows separated by [SettingsRowDivider]), then the footer note aligned
 * with the row text. The list is a short, fixed set of [OSS_LIBRARIES] entries, so a scrolling Column replaces the
 * former LazyColumn: one card can't span lazy items without per-item corner shapes.
 */
// One flat scaffold/card/footer layout.
@Suppress("LongMethod")
@Composable
private fun LicensesScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OneUiDetailTopBar(title = "Open-source licenses", onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(RollaDimens.settingsSectionGap))
            SettingsSectionCard {
                OSS_LIBRARIES.forEachIndexed { index, library ->
                    if (index > 0) SettingsRowDivider()
                    OssLibraryRow(library = library)
                }
            }
            Text(
                text = LICENSES_FOOTER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = RollaDimens.settingsCardInset + RollaDimens.settingsRowPadding,
                    end = RollaDimens.settingsCardInset + RollaDimens.settingsRowPadding,
                    top = RollaDimens.settingsSectionGap,
                    bottom = RollaDimens.settingsSectionGap,
                ),
            )
        }
    }
}

/** One dependency: name and project/author on the left, the license id in settingValue blue on the right. */
@Composable
private fun OssLibraryRow(library: OssLibrary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .settingsRowLayout()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.settingTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = library.project,
                style = MaterialTheme.typography.settingSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(RollaDimens.listTrailingGap))
        Text(
            text = library.license,
            style = MaterialTheme.typography.settingValue,
            color = MaterialTheme.colorScheme.accentText,
        )
    }
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
