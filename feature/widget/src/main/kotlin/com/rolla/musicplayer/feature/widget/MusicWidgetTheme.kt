package com.rolla.musicplayer.feature.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders

// The widget palette (Phase 6c ruling 4, spec §13), duplicated from res/values/colors.xml: the public Glance factory
// (androidx.glance.material3.ColorProviders) takes Compose ColorSchemes, not @ColorRes providers, while the drawables
// read the resources. WidgetPaletteTest keeps the two equal.

/** The card: true black. */
internal val WidgetCard = Color(0xFF000000)

/** Title text and control glyph tint (20.49:1 on the card). */
internal val WidgetTitle = Color(0xFFFCFCFE)

/** Artist text (9.70:1 on the card). */
internal val WidgetArtist = Color(0xFFB0B0B2)

/** The one accent: the progress fill (4.65:1 on the card). */
internal val WidgetAccent = Color(0xFF2F6FF0)

/** The progress track (3.56:1 on the card). Ruling 1: the app's dark sliderInactiveTrack, not spec §13's #333333. */
internal val WidgetTrack = Color(0xFF646466)

/** The artwork placeholder box behind the white note (the note is 11.79:1 on it). */
internal val WidgetArtPlaceholder = Color(0xFF363636)

/** Unused by this layout; set so no Glance role falls back to a Material default. */
internal val WidgetError = Color(0xFFFF5A5A)

/**
 * The one Material 3 scheme this widget renders with. The widget stays dark whatever the system theme, so the same
 * scheme is used for both the `light` and `dark` sides of [RollaWidgetColors] (day == night, deliberately).
 *
 * Roles this layout never reads are still mapped (ruling 4's role column) rather than left as Material defaults,
 * which would bleed off-palette colour if a built-in Glance control read one: the accent family to [WidgetAccent],
 * text-on roles to [WidgetTitle], `*Container` roles except `surfaceContainer*` to [WidgetArtPlaceholder], surface
 * roles and `surfaceContainer*` to [WidgetCard], and `surfaceVariant` (the bar's track, which the layout reads) and
 * `outline` to [WidgetTrack].
 */
private val WidgetColorScheme = darkColorScheme(
    primary = WidgetAccent,
    onPrimary = WidgetTitle,
    primaryContainer = WidgetArtPlaceholder,
    onPrimaryContainer = WidgetTitle,
    inversePrimary = WidgetAccent,
    secondary = WidgetAccent,
    onSecondary = WidgetTitle,
    secondaryContainer = WidgetArtPlaceholder,
    onSecondaryContainer = WidgetTitle,
    tertiary = WidgetAccent,
    onTertiary = WidgetTitle,
    tertiaryContainer = WidgetArtPlaceholder,
    onTertiaryContainer = WidgetTitle,
    background = WidgetCard,
    onBackground = WidgetTitle,
    surface = WidgetCard,
    onSurface = WidgetTitle,
    surfaceVariant = WidgetTrack,
    onSurfaceVariant = WidgetArtist,
    inverseSurface = WidgetTitle,
    inverseOnSurface = WidgetCard,
    error = WidgetError,
    onError = WidgetTitle,
    errorContainer = WidgetArtPlaceholder,
    onErrorContainer = WidgetTitle,
    // Ruling P4: outline is M3's 3:1 boundary role, and only the track clears 3:1 on the card (3.56:1).
    outline = WidgetTrack,
    surfaceContainer = WidgetCard,
    surfaceContainerHigh = WidgetCard,
    surfaceContainerHighest = WidgetCard,
    surfaceContainerLow = WidgetCard,
    surfaceContainerLowest = WidgetCard,
)

/**
 * The widget's [ColorProviders], mapped from the widget palette above, not from the app's `RollaMusicPlayerTheme`
 * tokens (:core:designsystem), which only resolve inside Compose UI. Glance composes in its own tree (backed by
 * `RemoteViews`) and reads colours through [androidx.glance.GlanceTheme.colors]; there is no bridge that lets a Glance
 * composable read `MaterialTheme.colorScheme`. Built via the public [androidx.glance.material3.ColorProviders]
 * factory; see [WidgetColorScheme] for the role mapping and the day == night rationale.
 */
val RollaWidgetColors: ColorProviders = ColorProviders(
    light = WidgetColorScheme,
    dark = WidgetColorScheme,
)
