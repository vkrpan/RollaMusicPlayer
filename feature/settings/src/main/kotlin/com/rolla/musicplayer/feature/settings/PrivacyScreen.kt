@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.screenTitle

private val ScreenHorizontalPadding = 16.dp
private val TopSpacing = 8.dp
private val SectionSpacing = 24.dp
private val SectionHeaderSpacing = 8.dp
private val ParagraphSpacing = 12.dp
private val BottomSpacing = 24.dp

private const val PRIVACY_STATEMENT =
    "RollaMusicPlayer is fully offline. It has no internet permission, so it cannot connect to " +
        "the network at all — nothing you play, search, or edit ever leaves this device."

private const val PRIVACY_NO_COLLECTION =
    "There are no analytics, no crash reporting to outside services, no accounts, and no ads. " +
        "Your library index, playlists, equalizer presets, and settings are stored only in this " +
        "app's local storage."

private const val PERMISSION_AUDIO_LABEL = "Music and audio access"
private const val PERMISSION_AUDIO_DETAIL =
    "READ_MEDIA_AUDIO on Android 13 and newer (READ_EXTERNAL_STORAGE on older versions). Used " +
        "for exactly one thing: finding and playing the music files already on your device."

private const val PERMISSION_LEGACY_WRITE_LABEL = "Legacy storage write (Android 9 and older only)"
private const val PERMISSION_LEGACY_WRITE_DETAIL =
    "WRITE_EXTERNAL_STORAGE, requested only when you save tag edits on Android 9 or older, " +
        "where no per-file consent dialog exists. On Android 10+ the app never holds this " +
        "permission — the system asks you per edit instead."

/**
 * Read-only privacy and permissions summary, reached from Settings' "Privacy & permissions" row.
 *
 * Deliberately contains NO toggles: the app collects and transmits nothing, so there is nothing to
 * opt in or out of, and adding switches here would only imply otherwise (see PHASE6-SETTINGS.md's
 * "no decorative toggles" note and CLAUDE.md's Offline & Privacy Architecture, which this text
 * restates in plain language). The permission facts mirror the actual manifests:
 * `:core:permissions` declares the version-aware audio-read permission, and `:feature:tageditor`
 * declares WRITE_EXTERNAL_STORAGE with maxSdkVersion=28 for the legacy tag-save path.
 */
@Composable
fun PrivacyRoute(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    PrivacyScreen(onNavigateUp = onNavigateUp, modifier = modifier)
}

@Suppress("LongMethod")
@Composable
private fun PrivacyScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PrivacyTopBar(onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding),
        ) {
            Spacer(Modifier.height(TopSpacing))
            SettingsSectionHeader(
                title = "Your privacy",
                modifier = Modifier.padding(bottom = SectionHeaderSpacing),
            )
            SettingsSectionCard {
                PrivacyParagraph(text = PRIVACY_STATEMENT)
                Spacer(Modifier.height(ParagraphSpacing))
                PrivacyParagraph(text = PRIVACY_NO_COLLECTION)
            }
            Spacer(Modifier.height(SectionSpacing))
            SettingsSectionHeader(
                title = "Permissions",
                modifier = Modifier.padding(bottom = SectionHeaderSpacing),
            )
            SettingsSectionCard {
                PermissionSummary(label = PERMISSION_AUDIO_LABEL, detail = PERMISSION_AUDIO_DETAIL)
                Spacer(Modifier.height(ParagraphSpacing))
                PermissionSummary(
                    label = PERMISSION_LEGACY_WRITE_LABEL,
                    detail = PERMISSION_LEGACY_WRITE_DETAIL,
                )
            }
            Spacer(Modifier.height(BottomSpacing))
        }
    }
}

@Composable
private fun PrivacyParagraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionSummary(label: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyTopBar(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "Privacy & permissions",
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
@Preview(name = "Privacy - Light")
@Preview(name = "Privacy - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPrivacyScreen() {
    RollaMusicPlayerTheme {
        PrivacyScreen(onNavigateUp = {})
    }
}
