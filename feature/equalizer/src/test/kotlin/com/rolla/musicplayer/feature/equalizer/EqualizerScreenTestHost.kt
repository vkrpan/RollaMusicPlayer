package com.rolla.musicplayer.feature.equalizer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.model.EqualizerPreset

internal val BassBoostPreset: EqualizerPreset = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Bass boost" }

/** A user preset whose gains equal no built-in, so "Mine" is distinguishable from every built-in preset. */
internal val MinePreset = EqualizerPreset(
    id = 100L,
    name = "Mine",
    isCustom = true,
    gainsMillibel = listOf(100, 200, 300, 200, 100, 0, -100, -200, -300),
)

/**
 * Sets the production [EqualizerScreen] as the content, with Bass boost's gains and the given state and callbacks.
 * [fontScale] overrides only the font scale of the ambient density (the OneUiTabRowTest precedent).
 */
// Every parameter is an independent knob of the fixture, all defaulted; a holder type would add nothing.
@Suppress("LongParameterList")
internal fun ComposeContentTestRule.setEqualizerScreen(
    enabled: Boolean = true,
    presets: List<EqualizerPreset> = BUILT_IN_EQUALIZER_PRESETS + MinePreset,
    selectedPresetId: Long? = BassBoostPreset.id,
    fontScale: Float = 1f,
    onSetEnabled: (Boolean) -> Unit = {},
    onBandGainChange: (Int, Short) -> Unit = { _, _ -> },
    onSelectPreset: (EqualizerPreset) -> Unit = {},
    onDelete: (Long) -> Unit = {},
) {
    val state = EqualizerUiState(
        enabled = enabled,
        gainsMillibel = BandGains(BassBoostPreset.gainsMillibel),
        presets = presets,
        selectedPresetId = selectedPresetId,
    )
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            RollaMusicPlayerTheme(darkTheme = true) {
                EqualizerScreen(
                    uiState = state,
                    onNavigateUp = {},
                    onSetEnabled = onSetEnabled,
                    onBandGainChange = onBandGainChange,
                    onSelectPreset = onSelectPreset,
                    onDeletePreset = onDelete,
                    onSaveAsPresetClick = {},
                )
            }
        }
    }
}
