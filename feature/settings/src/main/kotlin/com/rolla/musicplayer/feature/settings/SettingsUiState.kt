package com.rolla.musicplayer.feature.settings

import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.data.repository.DEFAULT_PLAYBACK_SPEED
import com.rolla.musicplayer.core.model.ThemeMode

/**
 * Settings screen UI state -- a single immutable snapshot combining every observable setting
 * [SettingsViewModel] exposes, plus one purely device-derived flag.
 *
 * [themeMode]/[useDynamicColor]/[playbackSpeed]/[skipSilence]/[equalizerEnabled] each mirror one
 * `observeX()` stream ([SettingsViewModel.uiState]'s KDoc has the exact wiring); [equalizerEnabled]
 * in particular is read-only here -- this screen surfaces it as the Equalizer row's On/Off value
 * text, but changing it happens on the Equalizer screen itself, not here.
 *
 * [isDynamicColorAvailable] is NOT backed by a repository flow: Material You dynamic color only
 * exists on API 31+ (Android 12), so this is a static, device-derived capability flag the screen
 * uses to decide whether to render the dynamic-color row at all. See
 * [SettingsViewModel.isDynamicColorAvailable] for how it's computed.
 */
@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val playbackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
    val skipSilence: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val isDynamicColorAvailable: Boolean = false,
)
