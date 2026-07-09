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
 * first-class parity. The accent (`primary`) stays brand-stable even under dynamic color.
 *
 * Owned by m3-design-system-agent. Never hardcode colors elsewhere — reference
 * `MaterialTheme.colorScheme.*` or the semantic extensions at the bottom of this file.
 */

// ---- Brand accent (stable in both themes) ----
private val Blue = Color(0xFF3D7BFF)
private val OnBlue = Color(0xFFFFFFFF)

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
    primary = Blue,
    onPrimary = OnBlue,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkOnSurfaceVariant,
    onSecondary = Color(0xFF000000),
    secondaryContainer = DarkSurfaceContainerHigh,
    onSecondaryContainer = Color(0xFFE2E2E5),
    tertiary = Blue,
    onTertiary = OnBlue,
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
    primary = Blue,
    onPrimary = OnBlue,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightOnSurfaceVariant,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSurfaceContainerHigh,
    onSecondaryContainer = Color(0xFF1A1C1E),
    tertiary = Blue,
    onTertiary = OnBlue,
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
 */
val ColorScheme.miniPlayerContainer: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalRollaDarkTheme.current) Color(0xFF241F2E) else Color(0xFFECEAF2)

val ColorScheme.sliderInactiveTrack: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalRollaDarkTheme.current) Color(0xFF3A3A3C) else Color(0xFFC4C6CA)

val ColorScheme.fastScrollIndex: Color
    @Composable @ReadOnlyComposable
    get() = onSurfaceVariant.copy(alpha = 0.6f)
