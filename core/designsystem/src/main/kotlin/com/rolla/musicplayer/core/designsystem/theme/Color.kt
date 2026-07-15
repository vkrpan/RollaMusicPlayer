package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for RollaMusicPlayer.
 *
 * Implements `.claude/rules/ui-style-guide.md` §2 (One UI–inspired dark: OLED black,
 * single blue accent, soft rounded dark surfaces). Dark is the primary theme; light is
 * first-class parity. The accent (`primary`) stays the same brand hue (~221°) in both
 * schemes, but is NOT the same hex in both — see the WCAG note below.
 *
 * Owned by m3-design-system-agent. Never hardcode colors elsewhere — reference
 * `MaterialTheme.colorScheme.*` or the semantic extensions at the bottom of this file.
 */

// ---- Brand accent (WCAG AA audit 2026-07-15) ----
// A single #3D7BFF blue could NOT simultaneously satisfy (a) white-on-it >= 4.5:1 and
// (b) it-as-text-on-dark-surfaceContainer #1C1C1E >= 4.5:1 -- the two luminance windows
// don't intersect (proven by the audit). Fixed with the M3-canonical per-scheme split
// below: same ~221° hue, different lightness per theme, chosen so both the
// fill-with-onPrimary-text case and the primary-as-text case clear their thresholds in
// their own scheme. Do not re-merge these into one shared constant.
//
// Dark: relative luminance must be >= ~0.228 to clear 4.5:1 as text on surfaceContainer
// #1C1C1E (the tightest of background/surface/surfaceContainer, since surfaceContainer is
// the lightest of the three dark surfaces). #4780FF measures ~0.240 -- clears with margin.
private val DarkPrimary = Color(0xFF4780FF)

// Lifted primary is too light for white text to clear 4.5:1 (~3.7:1) -- pair it with a
// near-black navy on-color instead (luminance <= ~0.0144 required at this primary lightness).
private val DarkOnPrimary = Color(0xFF001B3F)

// Light: relative luminance must be <= ~0.160 to clear 4.5:1 as text on light
// surfaceContainer #F2F3F5 (the tightest surface -- lower luminance than #FFFFFF
// background, so it's the binding constraint). #0F5CFF measures ~0.150 -- clears with
// margin, and white text on this darker fill clears 4.5:1 too (with more margin still).
private val LightPrimary = Color(0xFF0F5CFF)
private val LightOnPrimary = Color(0xFFFFFFFF)

// ---- Dark palette ----
private val DarkBackground = Color(0xFF000000)
private val DarkOnBackground = Color(0xFFFFFFFF)
private val DarkSurface = Color(0xFF0E0E0E)
private val DarkOnSurface = Color(0xFFFFFFFF)
private val DarkSurfaceVariant = Color(0xFF2A2A2C)
private val DarkOnSurfaceVariant = Color(0xFF9CA0A6)
private val DarkSurfaceContainerLowest = Color(0xFF000000)
private val DarkSurfaceContainerLow = Color(0xFF141416)
private val DarkSurfaceContainer = Color(0xFF1C1C1E)
private val DarkSurfaceContainerHigh = Color(0xFF2A2A2C)
private val DarkSurfaceContainerHighest = Color(0xFF333335)
private val DarkPrimaryContainer = Color(0xFF1E3A66)
private val DarkOnPrimaryContainer = Color(0xFFD6E2FF)
private val DarkOutline = Color(0xFF5A5A5C)
private val DarkOutlineVariant = Color(0xFF2C2C2E)
private val DarkError = Color(0xFFFF5A5A)
private val DarkOnError = Color(0xFF000000)

// ---- Light palette ----
private val LightBackground = Color(0xFFFFFFFF)
private val LightOnBackground = Color(0xFF111111)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF111111)
private val LightSurfaceVariant = Color(0xFFE2E3E5)
private val LightOnSurfaceVariant = Color(0xFF5F6368)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF6F7F8)
private val LightSurfaceContainer = Color(0xFFF2F3F5)
private val LightSurfaceContainerHigh = Color(0xFFECEDEF)
private val LightSurfaceContainerHighest = Color(0xFFE6E7E9)
private val LightPrimaryContainer = Color(0xFFD6E2FF)
private val LightOnPrimaryContainer = Color(0xFF001A41)
private val LightOutline = Color(0xFF74777C)
private val LightOutlineVariant = Color(0xFFC4C6CA)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkOnSurfaceVariant,
    onSecondary = Color(0xFF000000),
    secondaryContainer = DarkSurfaceContainerHigh,
    onSecondaryContainer = Color(0xFFE2E2E5),
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    scrim = Color(0xFF000000),
)

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightOnSurfaceVariant,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSurfaceContainerHigh,
    onSecondaryContainer = Color(0xFF1A1C1E),
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    scrim = Color(0xFF000000),
)

/**
 * Semantic colors not covered by the Material role set (ui-style-guide §2).
 *
 * Implemented as theme-aware extensions so call sites read `MaterialTheme.colorScheme.miniPlayerContainer`.
 * (Alternative: promote these to a CompositionLocal-backed `RollaColors` if the set grows.)
 * Must be read inside a composable under RollaMusicPlayerTheme.
 *
 * These branch on [LocalRollaDarkTheme] -- the *resolved* theme RollaMusicPlayerTheme is actually
 * rendering -- not `isSystemInDarkTheme()`. The two diverge whenever the user forces Light/Dark in
 * Settings against the device's own system setting; reading the system flag here would silently
 * pick the wrong branch (e.g. Dark forced on a light-mode device would render this pill in the
 * *light* palette while `onSurface` text above it is already the *dark* palette's white -- a
 * contrast failure, not just a cosmetic mismatch).
 *
 * WCAG audit 2026-07-15: verified passing, unchanged -- `onSurface`/`onSurfaceVariant` text on
 * this pill both clear 4.5:1 in both themes. `primary` was NOT checked as text on this pill and
 * must never be used that way (it is not one of the audited pairings) -- treat any future
 * primary-as-text-on-miniPlayerContainer usage as a new pairing requiring its own audit.
 */
val ColorScheme.miniPlayerContainer: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalRollaDarkTheme.current) Color(0xFF241F2E) else Color(0xFFECEAF2)

// WCAG audit 2026-07-15: the old #3A3A3C (dark) / #C4C6CA (light) only cleared 1.5-1.9:1
// against the surfaces they're drawn on -- far under the 3:1 UI-component floor.
// Dark: needs relative luminance >= ~0.135 to clear 3:1 vs the lightest dark surface it's
// drawn on (surfaceContainer #1C1C1E). #6E6E73 measures ~0.157 -- clears with margin, and
// stays well under DarkPrimary's ~0.240 so the active track still reads as more prominent.
// Light: needs relative luminance <= ~0.265 to clear 3:1 vs the tightest light surface
// (surfaceContainer #F2F3F5, darker than #FFFFFF background). #838890 measures ~0.245 --
// clears with margin, and stays well above LightPrimary's ~0.150 (active track is darker/
// more saturated, so it still reads as more prominent in light mode too).
val ColorScheme.sliderInactiveTrack: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalRollaDarkTheme.current) Color(0xFF6E6E73) else Color(0xFF838890)

// WCAG audit 2026-07-15: at 60% alpha over surfaceContainer this token was UNUSED but
// authored broken -- the composited result only cleared ~3.2:1 (dark) / ~2.5:1 (light)
// against surfaceContainer, both under the 4.5:1 text floor a 12sp fast-scroll letter needs.
// Fixed to full-opacity `onSurfaceVariant`, which already clears 4.5:1 as text on
// background/surface/surfaceContainer in both themes (6.5:1 dark / 5.4:1 light against
// surfaceContainer, the tightest of the three) -- do not reintroduce alpha here.
val ColorScheme.fastScrollIndex: Color
    @Composable @ReadOnlyComposable
    get() = onSurfaceVariant
