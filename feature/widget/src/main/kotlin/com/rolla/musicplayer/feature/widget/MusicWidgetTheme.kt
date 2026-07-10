package com.rolla.musicplayer.feature.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders

// ui-style-guide.md §2 dark-role values, duplicated from res/values/colors.xml -- the public
// Glance API (androidx.glance.material3.ColorProviders) takes Compose ColorSchemes, not @ColorRes
// providers (the resource-based ColorProviders constructor is protected). colors.xml remains the
// source for the rounded-card background DRAWABLE; keep both in sync when the palette changes.
private val WidgetSurfaceContainer = Color(0xFF1C1C1E)
private val WidgetSurfaceContainerHigh = Color(0xFF2A2A2C)
private val WidgetOnSurface = Color(0xFFFFFFFF)
private val WidgetOnSurfaceVariant = Color(0xFF9CA0A6)
private val WidgetPrimary = Color(0xFF3D7BFF)
private val WidgetOnPrimary = Color(0xFFFFFFFF)
private val WidgetPrimaryContainer = Color(0xFF1E3A66)
private val WidgetTrack = Color(0xFF3A3A3C)
private val WidgetOutlineVariant = Color(0xFF2C2C2E)
private val WidgetError = Color(0xFFFF5A5A)

/**
 * The one Material 3 scheme this widget renders with -- `ui-style-guide.md` §8 prescribes the dark
 * mini-player look for the widget UNCONDITIONALLY, so the same dark scheme is used for both the
 * `light` and `dark` sides of [RollaWidgetColors] below (day == night, deliberately).
 *
 * Roles this layout never touches (secondary/tertiary/error containers, inverse roles, ...) are
 * mapped to the nearest on-brand role -- `primary`/`onPrimary` for the accent family (§1: "One
 * stable accent... everything else is white/gray") and `surface`/`onSurface` for structural roles --
 * rather than left as Material defaults, which would bleed off-brand color if a future built-in
 * Glance control reads one of them.
 */
private val WidgetColorScheme = darkColorScheme(
    primary = WidgetPrimary,
    onPrimary = WidgetOnPrimary,
    primaryContainer = WidgetPrimaryContainer,
    onPrimaryContainer = WidgetOnPrimary,
    secondary = WidgetPrimary,
    onSecondary = WidgetOnPrimary,
    secondaryContainer = WidgetPrimaryContainer,
    onSecondaryContainer = WidgetOnPrimary,
    tertiary = WidgetPrimary,
    onTertiary = WidgetOnPrimary,
    tertiaryContainer = WidgetPrimaryContainer,
    onTertiaryContainer = WidgetOnPrimary,
    error = WidgetError,
    errorContainer = WidgetSurfaceContainerHigh,
    onError = WidgetOnPrimary,
    onErrorContainer = WidgetOnSurface,
    background = WidgetSurfaceContainer,
    onBackground = WidgetOnSurface,
    surface = WidgetSurfaceContainer,
    onSurface = WidgetOnSurface,
    surfaceVariant = WidgetTrack,
    onSurfaceVariant = WidgetOnSurfaceVariant,
    outline = WidgetOutlineVariant,
    inverseSurface = WidgetOnSurface,
    inverseOnSurface = WidgetSurfaceContainer,
    inversePrimary = WidgetPrimary,
)

/**
 * The widget's [ColorProviders], mapped from `ui-style-guide.md` §2/§8 -- NOT from the app's
 * `RollaMusicPlayerTheme`/`LocalRollaDarkTheme` semantic tokens (:core:designsystem), which only
 * resolve inside Compose UI. Glance composes in its own tree (backed by `RemoteViews`) and reads
 * colors through [androidx.glance.GlanceTheme.colors]; there is no bridge that lets a Glance
 * composable read `MaterialTheme.colorScheme`. Built via the public
 * [androidx.glance.material3.ColorProviders] factory; see [WidgetColorScheme] for the role
 * mapping and the day==night rationale.
 */
val RollaWidgetColors: ColorProviders = ColorProviders(
    light = WidgetColorScheme,
    dark = WidgetColorScheme,
)
