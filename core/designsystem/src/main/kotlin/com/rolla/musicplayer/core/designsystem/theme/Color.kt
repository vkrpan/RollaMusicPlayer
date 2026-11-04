package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the One UI redesign (spec §5.1, docs/superpowers/specs/2026-10-06-oneui-redesign-design.md).
 *
 * The values are measured from the Samsung Music reference screenshots (docs/design/oneui-measurements.md).
 * Four are documented AA-safe substitutes for what was measured:
 *  - primary #2F6FF0 (measured #377AFF; white text on it only reached 3.9:1)
 *  - accentText #6094FF (measured #5B8FFD; 4.45:1 on surfaceContainerHigh)
 *  - sliderInactiveTrack #646466 (measured #5F5F61; 2.8:1 on the panel)
 *  - seekTrackInactive white @ 37 % dark / black @ 44 % light (measured 30 % / 25 %; below 3:1 on the Now Playing
 *    gradient), the lowest alphas that clear 3:1 over both its middle and washEnd
 *
 * Every pairing the UI uses is asserted by ContrastTest. Change a value here only together with that test.
 * Owned by m3-design-system-agent. Never hardcode colors elsewhere: read MaterialTheme.colorScheme.* or the semantic
 * extensions at the bottom of this file.
 */

/** Static Now Playing background colors, drawn by Modifier.nowPlayingBackground (spec §5.1). */
@Immutable
data class NowPlayingGradient(
    val top: Color,
    val middle: Color,
    val washStart: Color,
    val washEnd: Color,
)

/** One scheme's complete palette: Material roles plus semantic extensions. Internal so ContrastTest can audit it. */
@Suppress("LongParameterList")
@Immutable
internal data class RollaPalette(
    val background: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val labelSecondary: Color,
    val eqCaption: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val onSecondary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val accentText: Color,
    val destructiveText: Color,
    val tabUnselected: Color,
    val artworkPlaceholder: Color,
    val artworkPlaceholderLarge: Color,
    val artworkPlaceholderGlyph: Color,
    val sheetArtworkPlaceholder: Color,
    val dialogFieldContainer: Color,
    val folderBadgeStroke: Color,
    val folderBadgeFill: Color,
    val miniPlayerContainer: Color,
    val miniPlayerArtPlaceholder: Color,
    val miniPlayerArtGlyph: Color,
    val sliderInactiveTrack: Color,
    val switchThumb: Color,
    val fastScrollTrack: Color,
    val eqGridLine: Color,
    val seekTrackActive: Color,
    val seekTrackInactive: Color,
    val nowPlayingGradient: NowPlayingGradient,
)

// Dark sheets and dialogs keep the plain placeholder and the field one step above the dialog; declared once so the
// pairs cannot drift apart (Phase 7 F2).
private val DarkArtworkPlaceholder = Color(0xFF454547)
private val DarkSurfaceContainerHighest = Color(0xFF333333)

internal val DarkPalette = RollaPalette(
    background = Color(0xFF000000),
    onSurface = Color(0xFFFCFCFE),
    surfaceVariant = Color(0xFF2D2D2F),
    onSurfaceVariant = Color(0xFF9B9B9D),
    labelSecondary = Color(0xFFA4A4A6), // calibrated 2026-10-31 (measured #A4A3A9): 7.19:1 on surfaceContainer
    eqCaption = Color(0xFFE6E6E6), // calibrated 2026-10-31 (measured #E6E6E6): 16.83:1 on background
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0F10),
    surfaceContainer = Color(0xFF171719),
    surfaceContainerHigh = Color(0xFF2D2D2F),
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    primary = Color(0xFF2F6FF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A66),
    onPrimaryContainer = Color(0xFFD6E2FF),
    onSecondary = Color(0xFF000000),
    outline = Color(0xFF5A5A5C),
    outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFFF5A5A),
    onError = Color(0xFF000000),
    accentText = Color(0xFF6094FF),
    destructiveText = Color(0xFFFF6060), // error #FF5A5A is only 4.49:1 on surfaceContainerHigh; this is 4.64:1
    tabUnselected = Color(0xFF7E7E80),
    artworkPlaceholder = DarkArtworkPlaceholder,
    artworkPlaceholderLarge = Color(0xFF3B3B3B),
    artworkPlaceholderGlyph = Color(0xFFFCFCFE),
    sheetArtworkPlaceholder = DarkArtworkPlaceholder,
    dialogFieldContainer = DarkSurfaceContainerHighest,
    // On artwork, not a theme surface: the same in both themes (reference: white outline over black).
    folderBadgeStroke = Color(0xFFFFFFFF),
    // On artwork, not a theme surface: the same in both themes (reference: white outline over black).
    folderBadgeFill = Color(0xFF000000),
    miniPlayerContainer = Color(0xFF282035),
    miniPlayerArtPlaceholder = Color(0xFF504C5A), // calibrated 2026-10-31: the measured #504C5A, opaque
    miniPlayerArtGlyph = Color(0xFFFCFCFE),
    sliderInactiveTrack = Color(0xFF646466),
    switchThumb = Color(0xFFFCFCFE),
    fastScrollTrack = Color(0xFF333333),
    eqGridLine = Color(0xFF1D1D1F),
    seekTrackActive = Color(0xFFFCFCFE),
    seekTrackInactive = Color(0x5EFFFFFF), // white @ 37 %: lowest whole % reaching 3.1:1 on middle and washEnd
    nowPlayingGradient = NowPlayingGradient(
        top = Color(0xFF080808), // calibrated 2026-10-31 (measured #080808 just below the status bar)
        middle = Color(0xFF120F16),
        washStart = Color(0xFF231C2C),
        washEnd = Color(0xFF293332),
    ),
)

// Light onSurface and onSurfaceVariant, declared once: light eqCaption and labelSecondary have no light reference and
// reuse them (Phase 7 ruling P13), so the pairs cannot drift apart.
private val LightOnSurface = Color(0xFF111113)
private val LightOnSurfaceVariant = Color(0xFF646467)

internal val LightPalette = RollaPalette(
    background = Color(0xFFF4F4F6),
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFE8E8EA),
    onSurfaceVariant = LightOnSurfaceVariant,
    labelSecondary = LightOnSurfaceVariant, // no light reference (5.90:1 on white)
    eqCaption = LightOnSurface, // no light reference (17.17:1 on background)
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8E8EA),
    surfaceContainerHighest = Color(0xFFEBEBED),
    primary = Color(0xFF2F6FF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    onSecondary = Color(0xFFFFFFFF),
    outline = Color(0xFF8A8A8E),
    outlineVariant = Color(0xFFDEDEE0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    accentText = Color(0xFF1F5FE0),
    destructiveText = Color(0xFFBA1A1A), // the same as light error (5.28:1 on surfaceContainerHigh)
    tabUnselected = Color(0xFF646467),
    artworkPlaceholder = Color(0xFFE6E6E8),
    artworkPlaceholderLarge = Color(0xFFE9E9EB),
    artworkPlaceholderGlyph = Color(0xFF7A7A7E),
    // Phase 7 F2: #E6E6E8 on the #E8E8EA sheet is 1.02:1; white is 1.22:1 and its glyph 4.27:1 (a grey #D5D5D8 gives
    // only 1.20:1 and a 2.92:1 glyph).
    sheetArtworkPlaceholder = Color(0xFFFFFFFF),
    // Phase 7 F2: surfaceContainerHighest #EBEBED on the #E8E8EA dialog is 1.02:1; white is 1.22:1.
    dialogFieldContainer = Color(0xFFFFFFFF),
    // On artwork, not a theme surface: the same in both themes (reference: white outline over black).
    folderBadgeStroke = Color(0xFFFFFFFF),
    // On artwork, not a theme surface: the same in both themes (reference: white outline over black).
    folderBadgeFill = Color(0xFF000000),
    miniPlayerContainer = Color(0xFFE9E4F2),
    miniPlayerArtPlaceholder = Color(0x1A000000), // black @ 10 %
    miniPlayerArtGlyph = Color(0xFF707074),
    sliderInactiveTrack = Color(0xFF8A8A8E),
    switchThumb = Color(0xFFFFFFFF),
    fastScrollTrack = Color(0xFFF0F0F2),
    eqGridLine = Color(0xFFEEEEF0),
    seekTrackActive = Color(0xFF111113),
    seekTrackInactive = Color(0x70000000), // black @ 44 %: lowest whole % reaching 3.1:1 on middle and washEnd
    nowPlayingGradient = NowPlayingGradient(
        top = Color(0xFFFFFFFF),
        middle = Color(0xFFF7F5FA),
        washStart = Color(0xFFEFE9F5),
        washEnd = Color(0xFFE9F1EF),
    ),
)

// surfaceTint is transparent: One UI surfaces are flat, so M3 tonal elevation must not tint them blue.
private fun RollaPalette.toColorScheme(base: ColorScheme): ColorScheme = base.copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = onSurfaceVariant,
    onSecondary = onSecondary,
    secondaryContainer = surfaceContainerHigh,
    onSecondaryContainer = onSurface,
    tertiary = primary,
    onTertiary = onPrimary,
    background = background,
    onBackground = onSurface,
    surface = background,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline,
    outlineVariant = outlineVariant,
    error = error,
    onError = onError,
    scrim = Color.Black,
)

val DarkColorScheme: ColorScheme = DarkPalette.toColorScheme(darkColorScheme())

val LightColorScheme: ColorScheme = LightPalette.toColorScheme(lightColorScheme())

/**
 * The palette of the theme RollaMusicPlayerTheme is actually rendering. It branches on LocalRollaDarkTheme, never
 * isSystemInDarkTheme(): the two diverge whenever the user forces Light or Dark in Settings (ThemeTokensTest).
 */
private val currentPalette: RollaPalette
    @Composable @ReadOnlyComposable
    get() = if (LocalRollaDarkTheme.current) DarkPalette else LightPalette

/** Blue value/link text. Never use `primary` for text (it is a fill color). */
val ColorScheme.accentText: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.accentText

/**
 * Destructive action text (Delete, Remove from playlist). Never `error` as text: dark `error` is 4.49:1 on
 * `surfaceContainerHigh`.
 */
val ColorScheme.destructiveText: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.destructiveText

/**
 * Secondary labels on the content panel and the EQ card, where the reference is a touch lighter than
 * onSurfaceVariant: the sort label ("Name") and the EQ band value and frequency labels. Dark #A4A4A6 (7.19:1 on
 * surfaceContainer); light = light onSurfaceVariant. Usage: `color = MaterialTheme.colorScheme.labelSecondary`.
 */
val ColorScheme.labelSecondary: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.labelSecondary

/**
 * The EQ preset description under the chip grid, on the screen background. Dark #E6E6E6 (16.83:1 on background), a
 * softer white than onSurface; light = light onSurface. Usage: `color = MaterialTheme.colorScheme.eqCaption`.
 */
val ColorScheme.eqCaption: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.eqCaption

val ColorScheme.tabUnselected: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.tabUnselected

val ColorScheme.artworkPlaceholder: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.artworkPlaceholder

val ColorScheme.artworkPlaceholderLarge: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.artworkPlaceholderLarge

val ColorScheme.artworkPlaceholderGlyph: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.artworkPlaceholderGlyph

/** The artwork placeholder inside a sheet or dialog (ArtworkPlaceholder picks it there); light is white (F2). */
val ColorScheme.sheetArtworkPlaceholder: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.sheetArtworkPlaceholder

/** A text field's container inside a dialog: one step above the dialog in dark, white in light (Phase 7 F2). */
val ColorScheme.dialogFieldContainer: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.dialogFieldContainer

/** The folder badge's outline (FolderBadge). It sits on artwork, so it is white in both themes. */
val ColorScheme.folderBadgeStroke: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.folderBadgeStroke

/** The folder badge's body under the outline (FolderBadge). It sits on artwork, so it is black in both themes. */
val ColorScheme.folderBadgeFill: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.folderBadgeFill

/** The floating mini-player pill. Text on it is always `onSurface` (audited); never `primary`. */
val ColorScheme.miniPlayerContainer: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.miniPlayerContainer

val ColorScheme.miniPlayerArtPlaceholder: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.miniPlayerArtPlaceholder

/** The music-note glyph drawn on miniPlayerArtPlaceholder inside the pill (audited over the composite). */
val ColorScheme.miniPlayerArtGlyph: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.miniPlayerArtGlyph

val ColorScheme.sliderInactiveTrack: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.sliderInactiveTrack

val ColorScheme.switchThumb: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.switchThumb

val ColorScheme.fastScrollTrack: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.fastScrollTrack

/** A–Z letters: full-opacity onSurfaceVariant (4.55:1 dark / 5.18:1 light on fastScrollTrack). */
val ColorScheme.fastScrollIndex: Color
    @Composable @ReadOnlyComposable
    get() = onSurfaceVariant

val ColorScheme.eqGridLine: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.eqGridLine

val ColorScheme.seekTrackActive: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.seekTrackActive

val ColorScheme.seekTrackInactive: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.seekTrackInactive

val ColorScheme.nowPlayingGradient: NowPlayingGradient
    @Composable @ReadOnlyComposable
    get() = currentPalette.nowPlayingGradient
