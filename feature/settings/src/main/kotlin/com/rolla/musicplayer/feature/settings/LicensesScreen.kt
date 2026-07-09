@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.screenTitle

private val ContentPadding = 16.dp
private val RowVerticalPadding = 12.dp
private val RowMinHeight = 56.dp
private val RowLabelGap = 16.dp
private val FooterPadding = 16.dp

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

@Composable
private fun LicensesScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LicensesTopBar(onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ContentPadding),
        ) {
            items(items = OSS_LIBRARIES, key = { library -> library.name }) { library ->
                OssLibraryRow(library = library)
            }
            item(key = "licenses-footer") {
                Text(
                    text = LICENSES_FOOTER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = FooterPadding),
                )
            }
        }
    }
}

/** One dependency: name + project/author on the left, license id in primary on the right. */
@Composable
private fun OssLibraryRow(library: OssLibrary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(vertical = RowVerticalPadding)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = library.project,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(RowLabelGap))
        Text(
            text = library.license,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
