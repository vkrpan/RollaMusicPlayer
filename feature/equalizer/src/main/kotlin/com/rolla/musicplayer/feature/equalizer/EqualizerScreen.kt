@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.equalizer

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.EqVerticalSlider
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiDialog
import com.rolla.musicplayer.core.designsystem.component.OneUiSwitch
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTextField
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.caption
import com.rolla.musicplayer.core.designsystem.theme.eqFrequency
import com.rolla.musicplayer.core.designsystem.theme.eqGridLine
import com.rolla.musicplayer.core.designsystem.theme.eqValue
import com.rolla.musicplayer.core.designsystem.theme.settingTitle
import com.rolla.musicplayer.core.media.equalizer.BUILT_IN_EQUALIZER_PRESETS
import com.rolla.musicplayer.core.media.equalizer.TARGET_FREQUENCIES_HZ
import com.rolla.musicplayer.core.model.EqualizerPreset
import kotlin.math.roundToInt

/** Test tag on the enable card's toggleable row, the single toggle target of the "Equaliser" switch. */
internal const val EQ_ENABLE_ROW_TAG = "eq_enable_row"

/** Test tag on the response curve, which is composed only while a band slider is being dragged (ruling 10). */
internal const val EQ_CURVE_TAG = "eq_response_curve"

// Gain-slider motion spec (ui-style-guide.md §9: spring for interactive elements). Calm, non-bouncy settle -- a
// preset swap should read as the whole EQ curve confidently resolving into its new shape, not bouncing past it.
internal val GainValueSpring: FiniteAnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private const val MILLIBEL_PER_DECIBEL = 100

/**
 * Visible frequency labels, index-aligned with TARGET_FREQUENCIES_HZ (EqualizerBands.kt in :core:media): the app's
 * real nine bands, 40 Hz..10 kHz, with the "Hz" suffix dropped, verbatim from spec §8.5. A fixed display table rather
 * than formatted at runtime, so the wording matches the spec exactly. These are what the eye reads; TalkBack reads
 * [SPOKEN_FREQUENCY_LABELS], which keep the unit.
 */
private val FREQUENCY_LABELS = listOf("40", "80", "160", "315", "630", "1.25k", "2.5k", "5k", "10k")

/**
 * The band names each slider's contentDescription is built from ("40Hz gain" … "10kHz gain"), index-aligned with
 * [FREQUENCY_LABELS]. Kept separate so dropping "Hz" from the visible labels never changes what TalkBack announces:
 * without a unit, "1.25k gain" is ambiguous (ruling 1).
 */
private val SPOKEN_FREQUENCY_LABELS =
    listOf("40Hz", "80Hz", "160Hz", "315Hz", "630Hz", "1.25kHz", "2.5kHz", "5kHz", "10kHz")

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

/**
 * The Equaliser screen (spec §8.5): the One UI detail bar, a small enable card, the edge-to-edge slider card, the
 * preset chip grid, then the preset caption and, in the Custom state only (ruling 11), "Save as preset". The
 * Scaffold keeps owning the navigation-bar inset; the pill is hidden here, so there is no mini-player inset to pad.
 */
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
        topBar = { OneUiDetailTopBar(title = "Equaliser", onNavigateUp = onNavigateUp) },
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

/**
 * No horizontal padding on the column: the slider card is edge to edge, and every other block insets itself.
 * The gaps between the slider card, the grid, the caption and the bottom reuse [RollaDimens.eqCardTop]; the
 * reference measures none of them (Phase 7 calibrates).
 */
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
            .verticalScroll(rememberScrollState()),
    ) {
        EnableCard(enabled = uiState.enabled, onSetEnabled = onSetEnabled)
        Spacer(Modifier.height(RollaDimens.eqCardTop))
        EqualizerSlidersCard(
            enabled = uiState.enabled,
            gainsMillibel = uiState.gainsMillibel,
            minGainMillibel = uiState.minGainMillibel,
            maxGainMillibel = uiState.maxGainMillibel,
            onBandGainChange = onBandGainChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(RollaDimens.eqCardTop))
        PresetChipGrid(
            presets = uiState.presets,
            selectedPresetId = uiState.selectedPresetId,
            enabled = uiState.enabled,
            onSelectPreset = onSelectPreset,
            onDeletePreset = onDeletePreset,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(RollaDimens.eqCardTop))
        Text(
            text = presetCaption(uiState.selectedPresetId),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = RollaDimens.chipGridStart),
        )
        if (uiState.selectedPresetId == null) {
            SaveAsPresetButton(
                onClick = onSaveAsPresetClick,
                modifier = Modifier.padding(horizontal = RollaDimens.chipGridStart),
            )
        }
        Spacer(Modifier.height(RollaDimens.eqCardTop))
    }
}

/**
 * The small enable card: one toggleable row (the single toggle target and the only Role.Switch node) holding the
 * "Equaliser" label and a purely visual [OneUiSwitch] (onCheckedChange = null, so it adds no second toggleable).
 */
@Composable
private fun EnableCard(enabled: Boolean, onSetEnabled: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(horizontal = RollaDimens.settingsCardInset)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EQ_ENABLE_ROW_TAG)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onSetEnabled)
                .heightIn(min = RollaDimens.minTouchTarget)
                .padding(horizontal = RollaDimens.settingsRowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Equaliser",
                style = MaterialTheme.typography.settingTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OneUiSwitch(checked = enabled, onCheckedChange = null)
        }
    }
}

/**
 * The edge-to-edge slider card: faint [eqGridLine] horizontals behind nine band columns centered as a group, with the
 * response curve layered under the sliders while one is being dragged. All nine sliders share one interaction source,
 * so "a slider is being dragged" is a single collected state (ruling 10).
 *
 * The card's inner vertical padding borrows [RollaDimens.settingsRowPadding] (the One UI card inner padding); the
 * reference does not measure the EQ card's own (Phase 7 calibrates).
 */
// The card's gain state, range and callback are distinct inputs; one flat card/grid/curve/row layout.
@Suppress("LongMethod", "LongParameterList")
@Composable
private fun EqualizerSlidersCard(
    enabled: Boolean,
    gainsMillibel: BandGains,
    minGainMillibel: Short,
    maxGainMillibel: Short,
    onBandGainChange: (Int, Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Remembered so every band slider keeps receiving the SAME ClosedFloatingPointRange<Float> instance across
    // recompositions instead of a freshly allocated one every drag frame (see BandGains).
    val valueRange = remember(minGainMillibel, maxGainMillibel) {
        minGainMillibel.toFloat()..maxGainMillibel.toFloat()
    }
    val sliderInteractions = remember { MutableInteractionSource() }
    val isDragging by sliderInteractions.collectIsDraggedAsState()
    val gridColor = MaterialTheme.colorScheme.eqGridLine

    val gainLabels = TARGET_FREQUENCIES_HZ.indices.map { band -> gainLabel(gainsMillibel.values.getOrElse(band) { 0 }) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = RollaDimens.settingsRowPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BandLabelRow(labels = gainLabels, style = MaterialTheme.typography.eqValue)
            // The slider band: exactly eqSliderLength tall, so the grid and the curve are framed on the sliders alone
            // (never on the labels, whose height follows the font scale) and 0 dB sits on its vertical center.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RollaDimens.eqSliderLength)
                    .drawBehind { drawEqGrid(gridColor) },
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    ResponseCurveOverlay(
                        visible = isDragging,
                        gainsMillibel = gainsMillibel,
                        minGainMillibel = minGainMillibel,
                        maxGainMillibel = maxGainMillibel,
                        modifier = Modifier.matchParentSize(),
                    )
                    EqualizerBandsRow(
                        enabled = enabled,
                        gainsMillibel = gainsMillibel,
                        valueRange = valueRange,
                        sliderInteractions = sliderInteractions,
                        isDragging = isDragging,
                        onBandGainChange = onBandGainChange,
                    )
                }
            }
            BandLabelRow(labels = FREQUENCY_LABELS, style = MaterialTheme.typography.eqFrequency)
        }
    }
}

/**
 * Horizontal grid lines every [RollaDimens.eqGridPitch], mirrored about the slider band's vertical center (where 0 dB
 * sits on every slider) out to the band's edges, drawn across the card's full width.
 */
private fun DrawScope.drawEqGrid(color: Color) {
    val pitch = RollaDimens.eqGridPitch.toPx()
    if (pitch <= 0f) return
    val halfExtent = size.height / 2f
    val thickness = RollaDimens.dividerThickness.toPx()
    val centerY = size.height / 2f
    var offset = 0f
    while (offset <= halfExtent) {
        drawLine(color, Offset(0f, centerY - offset), Offset(size.width, centerY - offset), thickness)
        if (offset > 0f) {
            drawLine(color, Offset(0f, centerY + offset), Offset(size.width, centerY + offset), thickness)
        }
        offset += pitch
    }
}

/**
 * The gains-driven response curve, composed only while [visible] (a slider is being dragged; ruling 10). It fades
 * in and out with the calm Rolla spring, and appears and disappears instantly under reduced motion.
 */
@Composable
private fun ResponseCurveOverlay(
    visible: Boolean,
    gainsMillibel: BandGains,
    minGainMillibel: Short,
    maxGainMillibel: Short,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = rollaSpringOrSnap<Float>(reducedMotion)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(spec),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(spec),
    ) {
        EqualizerResponseCurve(
            gainsMillibel = gainsMillibel,
            minGainMillibel = minGainMillibel,
            maxGainMillibel = maxGainMillibel,
            isDragging = visible,
            modifier = Modifier
                .fillMaxSize()
                .testTag(EQ_CURVE_TAG),
        )
    }
}

// Each parameter is a distinct input of the band row (state, range, shared drag source, callback).
@Suppress("LongParameterList")
@Composable
private fun EqualizerBandsRow(
    enabled: Boolean,
    gainsMillibel: BandGains,
    valueRange: ClosedFloatingPointRange<Float>,
    sliderInteractions: MutableInteractionSource,
    isDragging: Boolean,
    onBandGainChange: (Int, Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nine fixed eqColumnPitch columns, centered as a group (spec §8.5). They fit a 360 dp screen without scrolling.
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        TARGET_FREQUENCIES_HZ.indices.forEach { band ->
            BandGainSlider(
                gainMillibel = gainsMillibel.values.getOrElse(band) { 0 },
                valueRange = valueRange,
                enabled = enabled,
                sliderInteractions = sliderInteractions,
                isDragging = isDragging,
                contentDescription = "${SPOKEN_FREQUENCY_LABELS[band]} gain",
                onGainChange = { gain -> onBandGainChange(band, gain) },
                modifier = Modifier
                    .width(RollaDimens.eqColumnPitch)
                    .height(RollaDimens.eqSliderLength),
            )
        }
    }
}

/**
 * One row of nine per-band labels (gain values above the sliders, frequencies below), each centered in an
 * eqColumnPitch cell so it lines up with its slider; the row is centered as a group, exactly like the slider row.
 */
@Composable
private fun BandLabelRow(labels: List<String>, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        labels.forEach { label ->
            Box(modifier = Modifier.width(RollaDimens.eqColumnPitch), contentAlignment = Alignment.Center) {
                BandLabel(text = label, style = style)
            }
        }
    }
}

/**
 * The gain value above and the frequency below each band. Single-line and measured at its own width, centered on the
 * 28.7 dp column rather than clipped to it: "1.25k" is about as wide as the column at 1.0 font scale, and clipping
 * would cut the unit letter. Neighboring short labels leave room for the overhang.
 */
@Composable
private fun BandLabel(text: String, style: TextStyle) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.wrapContentWidth(unbounded = true),
    )
}

/**
 * One band's [EqVerticalSlider]. The slider never renders [gainMillibel] directly: it renders an [Animatable] that
 * snaps while a slider is being dragged (zero lag under the finger) and springs with [GainValueSpring] otherwise, so
 * a preset selection, which rewrites all nine gains at once, reads as the whole curve settling into its new shape.
 * Under reduced motion it always snaps. [onGainChange] reports the raw per-frame value; [EqualizerViewModel]
 * debounces persistence.
 *
 * [sliderInteractions] is shared by all nine sliders (it drives the response curve), so [isDragging] is true while
 * ANY band is dragged. Only the dragged band's gain changes then, so snapping the others is visible only if a drag
 * starts while a preset tap's spring (about 200 ms) is still settling: those bands jump to their targets. Negligible.
 */
@Suppress("LongParameterList")
@Composable
private fun BandGainSlider(
    gainMillibel: Short,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    sliderInteractions: MutableInteractionSource,
    isDragging: Boolean,
    contentDescription: String,
    onGainChange: (Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val displayedValue = remember { Animatable(gainMillibel.toFloat()) }
    LaunchedEffect(gainMillibel, isDragging, reducedMotion) {
        if (isDragging || reducedMotion) {
            displayedValue.snapTo(gainMillibel.toFloat())
        } else {
            displayedValue.animateTo(gainMillibel.toFloat(), GainValueSpring)
        }
    }
    EqVerticalSlider(
        value = displayedValue.value,
        onValueChange = { onGainChange(it.roundToInt().toShort()) },
        valueRange = valueRange,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        interactionSource = sliderInteractions,
    )
}

private fun gainLabel(gainMillibel: Short): String {
    val decibels = gainMillibel / MILLIBEL_PER_DECIBEL
    return if (decibels > 0) "+$decibels" else "$decibels"
}

@Composable
private fun SaveAsPresetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OneUiTextButton(
        text = "Save as preset",
        onClick = onClick,
        modifier = modifier.heightIn(min = RollaDimens.minTouchTarget),
    )
}

// Internal for SavePresetDialogTest: the dialog opens only from EqualizerRoute, which needs a Hilt ViewModel.
@Composable
internal fun SavePresetDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    val isNameBlank = name.trim().isEmpty()

    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "Save as preset",
        text = {
            OneUiTextField(
                value = name,
                onValueChange = { name = it },
                label = "Preset name",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        confirmButton = {
            OneUiTextButton(text = "Save", onClick = { onConfirm(name.trim()) }, enabled = !isNameBlank)
        },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        modifier = modifier,
    )
}

private fun previewUiState(): EqualizerUiState {
    val bassBoost = BUILT_IN_EQUALIZER_PRESETS.first { it.name == "Bass boost" }
    return EqualizerUiState(
        enabled = true,
        gainsMillibel = BandGains(bassBoost.gainsMillibel),
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
    gainsMillibel = BandGains(PREVIEW_CUSTOM_GAINS_MILLIBEL),
    presets = BUILT_IN_EQUALIZER_PRESETS,
    selectedPresetId = null,
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Equaliser - Light")
@Preview(name = "Equaliser - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
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
@Preview(name = "Equaliser Custom - Light")
@Preview(name = "Equaliser Custom - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
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
