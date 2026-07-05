// TooManyFunctions: this file's private composables are already split as small and single-purpose
// as the screen's structure allows (top bar / content / card / row / column / slider / chip grid /
// dialog, each a distinct visual unit); the file-level threshold (20) is a few functions below what
// this One-UI-style screen's layout actually needs, most recently pushed over by adding the
// isReducedMotion() motion-preference helper (see VerticalGainSlider's KDoc).
@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.rolla.musicplayer.feature.equalizer

import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.media.equalizer.TARGET_FREQUENCIES_HZ
import com.rolla.musicplayer.core.model.EqualizerPreset
import kotlin.math.roundToInt

private val ScreenHorizontalPadding = 16.dp
private val CardPadding = 16.dp
private val CardSectionSpacing = 16.dp
private val SectionSpacing = 24.dp
private val CaptionSpacing = 8.dp
private val SaveButtonSpacing = 8.dp
private val BottomSpacing = 24.dp
private val MinTouchTarget = 48.dp

private val BandColumnWidth = 48.dp
private val BandColumnSpacing = 8.dp
private val BandLabelSpacing = 4.dp
private val SliderTrackHeight = 220.dp
private const val SLIDER_ROTATION_DEGREES = 270f

// Gain-slider motion specs (ui-style-guide.md §9: 200-300ms, spring for interactive elements).
// Calm, non-bouncy settle -- a preset swap should read as the whole EQ curve confidently
// resolving into its new shape, not bouncing past it; same spring family as
// NowPlayingTransitionKey.artworkBoundsTransform and PlaylistDetailScreen's drag-lift specs.
internal val GainValueSpring: FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)
private const val DRAGGED_THUMB_SCALE = 1.3f
private val ThumbScaleSpring: FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private val ChipSpacing = 12.dp
private val PresetChipMinHeight = 48.dp
private val PresetChipHorizontalPadding = 16.dp

private const val MILLIBEL_PER_DECIBEL = 100

/**
 * Frequency labels, index-aligned with TARGET_FREQUENCIES_HZ (EqualizerBands.kt in :core:media):
 * 40Hz..10kHz. Kept as a fixed display table (rather than derived/formatted at runtime) so the
 * exact wording matches the visual spec (ui-style-guide.md section 6) verbatim.
 */
private val FREQUENCY_LABELS = listOf("40Hz", "80Hz", "160Hz", "315Hz", "630Hz", "1.25kHz", "2.5kHz", "5kHz", "10kHz")

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

/**
 * Stateful entry point for the equalizer screen. Same Route/Screen split as every other screen in
 * the codebase (see NowPlayingRoute in feature:player, PlaylistDetailRoute in feature:playlists).
 * Owns only the save-preset dialog's local visibility; every other piece of state lives in
 * [EqualizerViewModel].
 */
@Suppress("LongMethod")
@Composable
fun EqualizerRoute(
    onNavigateUp: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSavePresetDialog by rememberSaveable { mutableStateOf(false) }

    EqualizerScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onSetEnabled = remember(viewModel) { viewModel::setEnabled },
        onBandGainChange = remember(viewModel) { viewModel::setBandGain },
        onSelectPreset = remember(viewModel) { viewModel::selectPreset },
        onDeletePreset = remember(viewModel) { viewModel::deletePreset },
        onSaveAsPresetClick = { showSavePresetDialog = true },
        modifier = modifier,
    )

    if (showSavePresetDialog) {
        SavePresetDialog(
            onConfirm = { name ->
                viewModel.saveCurrentAsPreset(name)
                showSavePresetDialog = false
            },
            onDismiss = { showSavePresetDialog = false },
        )
    }
}

@Suppress("LongParameterList")
@Composable
fun EqualizerScreen(
    uiState: EqualizerUiState,
    onNavigateUp: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onBandGainChange: (Int, Short) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onSaveAsPresetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { EqualizerTopBar(onNavigateUp = onNavigateUp) },
    ) { innerPadding ->
        EqualizerContent(
            uiState = uiState,
            onSetEnabled = onSetEnabled,
            onBandGainChange = onBandGainChange,
            onSelectPreset = onSelectPreset,
            onDeletePreset = onDeletePreset,
            onSaveAsPresetClick = onSaveAsPresetClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerTopBar(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "Equalizer",
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

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun EqualizerContent(
    uiState: EqualizerUiState,
    onSetEnabled: (Boolean) -> Unit,
    onBandGainChange: (Int, Short) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onSaveAsPresetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(CaptionSpacing))
        EqualizerSlidersCard(
            enabled = uiState.enabled,
            gainsMillibel = uiState.gainsMillibel,
            minGainMillibel = uiState.minGainMillibel,
            maxGainMillibel = uiState.maxGainMillibel,
            onSetEnabled = onSetEnabled,
            onBandGainChange = onBandGainChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SectionSpacing))
        PresetChipGrid(
            presets = uiState.presets,
            selectedPresetId = uiState.selectedPresetId,
            onSelectPreset = onSelectPreset,
            onDeletePreset = onDeletePreset,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CaptionSpacing))
        Text(
            text = presetCaption(uiState.selectedPresetId),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.selectedPresetId == null) {
            Spacer(Modifier.height(SaveButtonSpacing))
            SaveAsPresetButton(onClick = onSaveAsPresetClick)
        }
        Spacer(Modifier.height(BottomSpacing))
    }
}

@Suppress("LongParameterList")
@Composable
private fun EqualizerSlidersCard(
    enabled: Boolean,
    gainsMillibel: List<Short>,
    minGainMillibel: Short,
    maxGainMillibel: Short,
    onSetEnabled: (Boolean) -> Unit,
    onBandGainChange: (Int, Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(CardPadding)) {
            EnabledToggleRow(enabled = enabled, onSetEnabled = onSetEnabled)
            Spacer(Modifier.height(CardSectionSpacing))
            EqualizerResponseCurve(
                gainsMillibel = gainsMillibel,
                minGainMillibel = minGainMillibel,
                maxGainMillibel = maxGainMillibel,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CardSectionSpacing))
            EqualizerBandsRow(
                enabled = enabled,
                gainsMillibel = gainsMillibel,
                valueRange = minGainMillibel.toFloat()..maxGainMillibel.toFloat(),
                onBandGainChange = onBandGainChange,
            )
        }
    }
}

@Composable
private fun EnabledToggleRow(enabled: Boolean, onSetEnabled: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Enable equalizer",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Default M3 Switch colors already map ON to MaterialTheme.colorScheme.primary -- no
        // color overrides needed here (ui-style-guide.md section 6, "Switches: primary ON").
        Switch(checked = enabled, onCheckedChange = onSetEnabled)
    }
}

@Composable
private fun EqualizerBandsRow(
    enabled: Boolean,
    gainsMillibel: List<Short>,
    valueRange: ClosedFloatingPointRange<Float>,
    onBandGainChange: (Int, Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nine fixed-width bands (>=48dp each, for touch target) don't fit most phone widths inside
    // their surrounding 16dp-inset card -- horizontalScroll guarantees the minimum touch width
    // instead of letting Arrangement.SpaceEvenly compress each band below 48dp. This nests inside
    // the screen's outer verticalScroll, but the two scroll axes are orthogonal (no gesture
    // conflict), unlike nesting two scrollables on the same axis.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(BandColumnSpacing),
    ) {
        TARGET_FREQUENCIES_HZ.forEachIndexed { index, _ ->
            EqualizerBandColumn(
                frequencyLabel = FREQUENCY_LABELS[index],
                gainMillibel = gainsMillibel.getOrElse(index) { 0 },
                valueRange = valueRange,
                enabled = enabled,
                onGainChange = { gain -> onBandGainChange(index, gain) },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun EqualizerBandColumn(
    frequencyLabel: String,
    gainMillibel: Short,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onGainChange: (Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(BandColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = gainLabel(gainMillibel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(BandLabelSpacing))
        VerticalGainSlider(
            gainMillibel = gainMillibel,
            valueRange = valueRange,
            enabled = enabled,
            onGainChange = onGainChange,
            modifier = Modifier
                .width(BandColumnWidth)
                .height(SliderTrackHeight),
        )
        Spacer(Modifier.height(BandLabelSpacing))
        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A single vertical gain slider: a standard M3 [Slider] rotated -90 degrees via the standard
 * Compose rotate-and-remeasure technique. [modifier]'s width/height (applied by the caller) become
 * the *rotated* slider's on-screen width/height; internally the constraints are swapped so the
 * unrotated [Slider] measures with the caller's height as its track length and the caller's width
 * as its thickness, then [graphicsLayer] rotates the result back into a vertical orientation
 * around its top-left corner.
 *
 * Motion (ui-style-guide.md §9): the slider never renders [gainMillibel] directly. Instead it
 * renders [displayedValue], an [Animatable] driven from [gainMillibel] + live drag state --
 * `snapTo` while the thumb is actively dragged (zero lag, tracks the finger 1:1), `animateTo` with
 * [GainValueSpring] the rest of the time. That "rest of the time" case is what makes a preset chip
 * selection -- which rewrites all nine bands' [gainMillibel] at once from the caller -- read as the
 * whole EQ curve springing into its new shape instead of jumping. [onGainChange] still reports the
 * slider's raw per-frame value upward unchanged; [EqualizerViewModel] debounces persistence, so
 * per-frame calls here are expected, not a perf problem.
 */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalGainSlider(
    gainMillibel: Short,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onGainChange: (Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = isReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val displayedValue = remember { Animatable(gainMillibel.toFloat()) }
    LaunchedEffect(gainMillibel, isDragged, reducedMotion) {
        if (isDragged || reducedMotion) {
            displayedValue.snapTo(gainMillibel.toFloat())
        } else {
            displayedValue.animateTo(gainMillibel.toFloat(), GainValueSpring)
        }
    }

    // Thumb-scale micro-interaction while dragging. `thumbScale` is read only inside the
    // graphicsLayer lambda passed to SliderDefaults.Thumb below (never destructured with `by`
    // here), so this per-frame scale animation never invalidates/recomposes the slider itself --
    // only the thumb's own draw layer repaints. Reduced motion disables the scale outright (stays
    // at 1f) rather than snapping it instantly, per ui-style-guide.md §9.
    val thumbScale = animateFloatAsState(
        targetValue = if (isDragged && !reducedMotion) DRAGGED_THUMB_SCALE else 1f,
        animationSpec = if (reducedMotion) snap() else ThumbScaleSpring,
        label = "gainThumbScale",
    )

    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
        disabledThumbColor = MaterialTheme.colorScheme.sliderInactiveTrack,
        disabledActiveTrackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
        disabledInactiveTrackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
    )

    Slider(
        value = displayedValue.value,
        onValueChange = { onGainChange(it.roundToInt().toShort()) },
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = sliderColors,
        thumb = {
            // Rotation-invariant: scaleX == scaleY, so this reads correctly under the 270-degree
            // rotation applied to the whole slider below without any offset compensation.
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = sliderColors,
                enabled = enabled,
                modifier = Modifier.graphicsLayer {
                    scaleX = thumbScale.value
                    scaleY = thumbScale.value
                },
            )
        },
        modifier = modifier
            .graphicsLayer {
                rotationZ = SLIDER_ROTATION_DEGREES
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    ),
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            },
    )
}

// Third private copy of this exact helper in the codebase (see PlaylistDetailScreen.kt and
// feature:player's NowPlayingTransitionKey.kt) -- a rule-of-three candidate for extraction into a
// shared :core:ui (or :core:designsystem) motion util, left as-is this pass per scope.
// internal (not private) so EqualizerResponseCurve.kt -- which mirrors this same
// gains-driven-motion behavior for the decorative response curve -- can reuse it instead of
// duplicating the Settings.Global lookup.
@Composable
internal fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

private fun gainLabel(gainMillibel: Short): String {
    val decibels = gainMillibel / MILLIBEL_PER_DECIBEL
    return if (decibels > 0) "+$decibels" else "$decibels"
}

private fun presetCaption(selectedPresetId: Long?): String = when {
    selectedPresetId == null -> CUSTOM_CAPTION
    else -> BUILT_IN_PRESET_CAPTIONS[selectedPresetId] ?: USER_PRESET_CAPTION
}

/**
 * One chip in [PresetChipGrid]. [preset] is null for the trailing synthetic "Custom" chip, which
 * represents "the live gains don't match any saved preset" -- it is never itself selectable via
 * click (there's nothing to select into), it only ever reflects [EqualizerUiState.selectedPresetId]
 * being null.
 */
private data class PresetChipUiModel(val preset: EqualizerPreset?, val label: String)

@Composable
private fun PresetChipGrid(
    presets: List<EqualizerPreset>,
    selectedPresetId: Long?,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onDeletePreset: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = remember(presets) {
        presets.map { preset -> PresetChipUiModel(preset, preset.name) } + PresetChipUiModel(null, "Custom")
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ChipSpacing)) {
        chips.chunked(2).forEach { rowChips ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            ) {
                rowChips.forEach { chip ->
                    val chipPreset = chip.preset
                    PresetChip(
                        chip = chip,
                        selected = chipPreset?.id == selectedPresetId,
                        onClick = { chipPreset?.let(onSelectPreset) },
                        // Deletion is a long-press gesture reserved for user-saved presets --
                        // built-ins and the synthetic Custom chip silently ignore a long-press.
                        onLongClick = {
                            if (chipPreset != null && chipPreset.isCustom) {
                                onDeletePreset(chipPreset.id)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    chip: PresetChipUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .heightIn(min = PresetChipMinHeight)
            .background(color = containerColor, shape = CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = PresetChipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = chip.label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SaveAsPresetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MinTouchTarget)) {
        Text(
            text = "Save as preset",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun SavePresetDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    val isNameBlank = name.trim().isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save as preset",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Preset name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = !isNameBlank) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    )
}

private fun previewUiState(): EqualizerUiState {
    val bassBoost = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Bass boost" }
    return EqualizerUiState(
        enabled = true,
        gainsMillibel = bassBoost.gainsMillibel,
        presets = BUILT_IN_EQUALIZER_PRESETS,
        selectedPresetId = bassBoost.id,
    )
}

// A representative "shaped the sound manually" curve for the Custom-state preview -- a plain
// property declaration (rather than inline literals in previewCustomUiState()'s body) so detekt's
// ignorePropertyDeclaration covers these gains the same way it already covers
// BuiltInEqualizerPresets.kt's preset tables.
private val PREVIEW_CUSTOM_GAINS_MILLIBEL: List<Short> = listOf(200, 100, 0, -50, -100, 50, 150, 250, 300)

private fun previewCustomUiState(): EqualizerUiState = EqualizerUiState(
    enabled = true,
    gainsMillibel = PREVIEW_CUSTOM_GAINS_MILLIBEL,
    presets = BUILT_IN_EQUALIZER_PRESETS,
    selectedPresetId = null,
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Equalizer - Light")
@Preview(name = "Equalizer - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEqualizerScreen() {
    RollaMusicPlayerTheme {
        EqualizerScreen(
            uiState = previewUiState(),
            onNavigateUp = {},
            onSetEnabled = {},
            onBandGainChange = { _, _ -> },
            onSelectPreset = {},
            onDeletePreset = {},
            onSaveAsPresetClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Equalizer Custom - Light")
@Preview(name = "Equalizer Custom - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewEqualizerScreenCustom() {
    RollaMusicPlayerTheme {
        EqualizerScreen(
            uiState = previewCustomUiState(),
            onNavigateUp = {},
            onSetEnabled = {},
            onBandGainChange = { _, _ -> },
            onSelectPreset = {},
            onDeletePreset = {},
            onSaveAsPresetClick = {},
        )
    }
}
