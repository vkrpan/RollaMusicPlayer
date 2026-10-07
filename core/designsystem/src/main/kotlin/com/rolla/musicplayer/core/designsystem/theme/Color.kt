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
    val tabUnselected: Color,
    val artworkPlaceholder: Color,
    val artworkPlaceholderLarge: Color,
    val artworkPlaceholderGlyph: Color,
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

internal val DarkPalette = RollaPalette(
    background = Color(0xFF000000),
    onSurface = Color(0xFFFCFCFE),
    surfaceVariant = Color(0xFF2D2D2F),
    onSurfaceVariant = Color(0xFF9B9B9D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0F10),
    surfaceContainer = Color(0xFF171719),
    surfaceContainerHigh = Color(0xFF2D2D2F),
    surfaceContainerHighest = Color(0xFF333333),
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
    tabUnselected = Color(0xFF7E7E80),
    artworkPlaceholder = Color(0xFF454547),
    artworkPlaceholderLarge = Color(0xFF3B3B3B),
    artworkPlaceholderGlyph = Color(0xFFFCFCFE),
    miniPlayerContainer = Color(0xFF282035),
    miniPlayerArtPlaceholder = Color(0x2EFFFFFF), // white @ 18 % over the pill = #4F4859 (measured #504C5A)
    miniPlayerArtGlyph = Color(0xFFFCFCFE),
    sliderInactiveTrack = Color(0xFF646466),
    switchThumb = Color(0xFFFCFCFE),
    fastScrollTrack = Color(0xFF333333),
    eqGridLine = Color(0xFF1D1D1F),
    seekTrackActive = Color(0xFFFCFCFE),
    seekTrackInactive = Color(0x5EFFFFFF), // white @ 37 %: lowest whole % reaching 3.1:1 on middle and washEnd
    nowPlayingGradient = NowPlayingGradient(
        top = Color(0xFF000000),
        middle = Color(0xFF120F16),
        washStart = Color(0xFF231C2C),
        washEnd = Color(0xFF293332),
    ),
)

internal val LightPalette = RollaPalette(
    background = Color(0xFFF4F4F6),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFE8E8EA),
    onSurfaceVariant = Color(0xFF646467),
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
    tabUnselected = Color(0xFF646467),
    artworkPlaceholder = Color(0xFFE6E6E8),
    artworkPlaceholderLarge = Color(0xFFE9E9EB),
    artworkPlaceholderGlyph = Color(0xFF7A7A7E),
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
