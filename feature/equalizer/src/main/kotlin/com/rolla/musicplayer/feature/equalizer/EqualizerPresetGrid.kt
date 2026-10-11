@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.component.PillChip
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.model.EqualizerPreset

private const val PRESET_GRID_COLUMNS = 2
private const val CUSTOM_CHIP_LABEL = "Custom"
private const val DELETE_PRESET_LABEL = "Delete preset"

/**
 * One short description per built-in preset, in BUILT_IN_EQUALIZER_PRESETS display order
 * (Balanced, Bass boost, Smooth, Dynamic, Clear, Treble boost -- see that file's KDoc). Zipped by
 * position (via [BUILT_IN_PRESET_CAPTIONS] below) rather than keyed on a hardcoded id or name, so
 * this stays correct even if the built-in list's contents change; only its length and order matter
 * here.
 */
private val BUILT_IN_PRESET_DESCRIPTIONS = listOf(
    "Even response across every band — a neutral starting point.",
    "Extra low-end weight and punch for bass-heavy tracks.",
    "A gentle, easy-listening curve with soft highs and lows.",
    "A bold, energetic curve for loud, exciting playback.",
    "Lifts vocals and instruments in the presence range for clarity.",
    "Brightens the top end for crisp cymbals and detail.",
)

private val BUILT_IN_PRESET_CAPTIONS: Map<Long, String> =
    BUILT_IN_EQUALIZER_PRESETS.mapIndexed { index, preset -> preset.id to BUILT_IN_PRESET_DESCRIPTIONS[index] }.toMap()

private const val CUSTOM_CAPTION = "Custom — adjust the sliders to shape the sound."
private const val USER_PRESET_CAPTION = "Your saved preset."

/** The caption under the preset grid: the selected built-in's description, Custom's hint, or the user-preset line. */
internal fun presetCaption(selectedPresetId: Long?): String = when {
    selectedPresetId == null -> CUSTOM_CAPTION
    else -> BUILT_IN_PRESET_CAPTIONS[selectedPresetId] ?: USER_PRESET_CAPTION
}

/**
 * One chip in [PresetChipGrid]. [preset] is null for the synthetic "Custom" chip, which represents "the live gains
 * don't match any saved preset": tapping it does nothing (there's nothing to select into); it only ever reflects
 * [EqualizerUiState.selectedPresetId] being null.
 */
private data class PresetChipUiModel(val preset: EqualizerPreset?, val label: String)

/**
 * Spec §8.5 order: the built-ins (Balanced, Bass boost, Smooth, Dynamic, Clear, Treble boost), then the synthetic
 * Custom chip, then the user's saved presets.
 */
private fun orderedPresetChips(presets: List<EqualizerPreset>): List<PresetChipUiModel> =
    presets.filterNot { it.isCustom }.map { PresetChipUiModel(it, it.name) } +
        PresetChipUiModel(preset = null, label = CUSTOM_CHIP_LABEL) +
        presets.filter { it.isCustom }.map { PresetChipUiModel(it, it.name) }

/**
 * The 2-column preset grid (spec §8.5): columns [RollaDimens.chipGap] apart, rows [RollaDimens.chipRowLayoutGap]
 * apart (each chip occupies a 48 dp touch height, which renders the measured gap), inset [RollaDimens.chipGridStart].
 * Single-select, so it is a selectableGroup. When the EQ is off every chip is disabled (spec §8.5).
 */
// Each parameter is a distinct input of the grid (data, selection, enabled, two callbacks); one flat grid layout.
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun PresetChipGrid(
    presets: List<EqualizerPreset>,
    selectedPresetId: Long?,
    enabled: Boolean,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = remember(presets) { orderedPresetChips(presets) }
    Column(
        modifier = modifier
            .padding(horizontal = RollaDimens.chipGridStart)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.chipRowLayoutGap),
    ) {
        chips.chunked(PRESET_GRID_COLUMNS).forEach { rowChips ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RollaDimens.chipGap),
            ) {
                rowChips.forEach { chip ->
                    PresetGridChip(
                        chip = chip,
                        selectedPresetId = selectedPresetId,
                        enabled = enabled,
                        onSelectPreset = onSelectPreset,
                        onDeletePreset = onDeletePreset,
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd last row keeps its single chip in the first column, at the same width as the others.
                if (rowChips.size < PRESET_GRID_COLUMNS) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * One grid cell. Deleting is a long press reserved for user-saved presets, announced to TalkBack as "Delete preset";
 * built-ins and the Custom chip expose no long-click action at all. The delete is immediate, as before.
 */
@Suppress("LongParameterList")
@Composable
private fun PresetGridChip(
    chip: PresetChipUiModel,
    selectedPresetId: Long?,
    enabled: Boolean,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preset = chip.preset
    val userPresetId = preset?.takeIf { it.isCustom }?.id
    PillChip(
        label = chip.label,
        selected = preset?.id == selectedPresetId,
        onClick = { preset?.let(onSelectPreset) },
        modifier = modifier,
        enabled = enabled,
        onLongClick = userPresetId?.let { id -> { onDeletePreset(id) } },
        onLongClickLabel = userPresetId?.let { DELETE_PRESET_LABEL },
    )
}
