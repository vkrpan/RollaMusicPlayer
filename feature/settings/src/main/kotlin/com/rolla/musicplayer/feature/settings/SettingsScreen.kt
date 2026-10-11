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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.data.repository.MAX_PLAYBACK_SPEED
import com.rolla.musicplayer.core.data.repository.MIN_PLAYBACK_SPEED
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.ThemeMode
import kotlin.math.roundToInt

private const val PLAYBACK_SPEED_STEP = 0.1f
private const val TENTHS_MULTIPLIER = 10
private val PlaybackSpeedSliderSteps =
    ((MAX_PLAYBACK_SPEED - MIN_PLAYBACK_SPEED) / PLAYBACK_SPEED_STEP).roundToInt() - 1

/**
 * Stateful entry point for the Settings screen (Settings route). Same Route/Screen split as every
 * other screen in the codebase (see TagEditorRoute in feature:tageditor).
 *
 * [onEqualizerClick]/[onPrivacyClick]/[onAboutClick]/[onLicensesClick] are hoisted navigation
 * callbacks wired by navigation-agent; the Rescan action is NOT hoisted -- it's a ViewModel
 * action ([SettingsViewModel.onRescanClick]), not a navigation edge, so it stays inside this
 * feature (same reasoning as SearchRoute binding song taps to its own ViewModel's play()).
 */
@Suppress("LongParameterList")
@Composable
fun SettingsRoute(
    onNavigateUp: () -> Unit,
    onEqualizerClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLicensesClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onThemeModeSelected = remember(viewModel) { viewModel::onThemeModeSelected },
        onUseDynamicColorChanged = remember(viewModel) { viewModel::onUseDynamicColorChanged },
        onPlaybackSpeedChanged = remember(viewModel) { viewModel::onPlaybackSpeedChanged },
        onSkipSilenceChanged = remember(viewModel) { viewModel::onSkipSilenceChanged },
        onEqualizerClick = onEqualizerClick,
        onRescanClick = remember(viewModel) { viewModel::onRescanClick },
        onDismissRescanMessage = remember(viewModel) { viewModel::dismissRescanMessage },
        onPrivacyClick = onPrivacyClick,
        onAboutClick = onAboutClick,
        onLicensesClick = onLicensesClick,
        modifier = modifier,
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateUp: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onSkipSilenceChanged: (Boolean) -> Unit,
    onEqualizerClick: () -> Unit,
    onRescanClick: () -> Unit,
    onDismissRescanMessage: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val rescanMessage = uiState.rescanMessage
    LaunchedEffect(rescanMessage) {
        if (rescanMessage != null) {
            snackbarHostState.showSnackbar(rescanMessage)
            onDismissRescanMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OneUiDetailTopBar(title = "Rolla Music settings", onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Each SettingsSectionHeader owns the gap above it and each card its side inset, so sections just stack.
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AppearanceSection(
                themeMode = uiState.themeMode,
                useDynamicColor = uiState.useDynamicColor,
                isDynamicColorAvailable = uiState.isDynamicColorAvailable,
                onThemeRowClick = { showThemeDialog = true },
                onUseDynamicColorChanged = onUseDynamicColorChanged,
                modifier = Modifier.fillMaxWidth(),
            )
            PlaybackSection(
                playbackSpeed = uiState.playbackSpeed,
                skipSilence = uiState.skipSilence,
                equalizerEnabled = uiState.equalizerEnabled,
                onPlaybackSpeedChanged = onPlaybackSpeedChanged,
                onSkipSilenceChanged = onSkipSilenceChanged,
                onEqualizerClick = onEqualizerClick,
                modifier = Modifier.fillMaxWidth(),
            )
            LibrarySection(
                isRescanning = uiState.isRescanning,
                onRescanClick = onRescanClick,
                modifier = Modifier.fillMaxWidth(),
            )
            PrivacySection(onPrivacyClick = onPrivacyClick, modifier = Modifier.fillMaxWidth())
            AboutSection(
                onAboutClick = onAboutClick,
                onLicensesClick = onLicensesClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(RollaDimens.settingsSectionGap))
        }
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            selected = uiState.themeMode,
            onSelect = onThemeModeSelected,
            onDismiss = { showThemeDialog = false },
        )
    }
}

/** Theme value row (opens [ThemeModeDialog]) and the Dynamic colour toggle, shown only when supported. */
@Suppress("LongParameterList")
@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    isDynamicColorAvailable: Boolean,
    onThemeRowClick: () -> Unit,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title = "Appearance")
        SettingsSectionCard {
            SettingsValueNavRow(
                label = "Theme",
                value = themeMode.displayLabel(),
                onClick = onThemeRowClick,
            )
            if (isDynamicColorAvailable) {
                SettingsRowDivider()
                SettingsToggleRow(
                    label = "Dynamic colour",
                    subLabel = "Use colours from your wallpaper",
                    checked = useDynamicColor,
                    onCheckedChange = onUseDynamicColorChanged,
                )
            }
        }
    }
}

/** Playback speed slider, Skip silence toggle, and the Equaliser On/Off value row. */
@Suppress("LongParameterList")
@Composable
private fun PlaybackSection(
    playbackSpeed: Float,
    skipSilence: Boolean,
    equalizerEnabled: Boolean,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onSkipSilenceChanged: (Boolean) -> Unit,
    onEqualizerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title = "Playback")
        SettingsSectionCard {
            SettingsSliderRow(
                label = "Playback speed",
                value = playbackSpeed,
                valueRange = MIN_PLAYBACK_SPEED..MAX_PLAYBACK_SPEED,
                steps = PlaybackSpeedSliderSteps,
                valueLabel = ::formatPlaybackSpeedLabel,
                onValueChangeFinished = onPlaybackSpeedChanged,
            )
            SettingsRowDivider()
            SettingsToggleRow(
                label = "Skip silence",
                subLabel = "Speed through quiet passages automatically",
                checked = skipSilence,
                onCheckedChange = onSkipSilenceChanged,
            )
            SettingsRowDivider()
            SettingsValueNavRow(
                label = "Equaliser",
                value = if (equalizerEnabled) "On" else "Off",
                onClick = onEqualizerClick,
            )
        }
    }
}

/** Single action row: kicks off a fresh MediaStore scan of the local library. */
@Composable
private fun LibrarySection(
    isRescanning: Boolean,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title = "Library")
        SettingsSectionCard {
            SettingsValueNavRow(
                label = "Rescan library",
                subLabel = "Scan device storage for new or changed music",
                onClick = onRescanClick,
                enabled = !isRescanning,
                showProgress = isRescanning,
            )
        }
    }
}

/** Single nav row into the permissions/privacy screen -- see :core:permissions. */
@Composable
private fun PrivacySection(onPrivacyClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title = "Privacy")
        SettingsSectionCard {
            SettingsValueNavRow(label = "Privacy & permissions", onClick = onPrivacyClick)
        }
    }
}

/** About and open-source-licenses nav rows. */
@Composable
private fun AboutSection(
    onAboutClick: () -> Unit,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title = "About")
        SettingsSectionCard {
            SettingsValueNavRow(label = "About", onClick = onAboutClick)
            SettingsRowDivider()
            SettingsValueNavRow(label = "Open-source licenses", onClick = onLicensesClick)
        }
    }
}

/** Formats a raw speed multiplier as e.g. "1.0x" -- avoids String.format/Locale entirely via integer math. */
private fun formatPlaybackSpeedLabel(speed: Float): String {
    val tenths = (speed * TENTHS_MULTIPLIER).roundToInt()
    val whole = tenths / TENTHS_MULTIPLIER
    val decimal = tenths % TENTHS_MULTIPLIER
    return "$whole.${decimal}x"
}

private fun previewUiState(): SettingsUiState = SettingsUiState(
    themeMode = ThemeMode.SYSTEM,
    useDynamicColor = true,
    playbackSpeed = 1.25f,
    skipSilence = true,
    equalizerEnabled = true,
    isDynamicColorAvailable = true,
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Settings - Light")
@Preview(name = "Settings - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSettingsScreen() {
    RollaMusicPlayerTheme {
        SettingsScreen(
            uiState = previewUiState(),
            onNavigateUp = {},
            onThemeModeSelected = {},
            onUseDynamicColorChanged = {},
            onPlaybackSpeedChanged = {},
            onSkipSilenceChanged = {},
            onEqualizerClick = {},
            onRescanClick = {},
            onDismissRescanMessage = {},
            onPrivacyClick = {},
            onAboutClick = {},
            onLicensesClick = {},
        )
    }
}
