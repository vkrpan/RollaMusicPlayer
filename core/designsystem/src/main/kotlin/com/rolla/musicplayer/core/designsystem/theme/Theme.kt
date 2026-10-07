package com.rolla.musicplayer.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the *resolved* theme is dark -- i.e. the [RollaMusicPlayerTheme] `darkTheme` parameter
 * actually in effect, which the app root may have forced independently of the system setting
 * (Settings > Appearance > Theme: System/Light/Dark). Semantic color extensions below read this
 * instead of calling `isSystemInDarkTheme()` directly, so they stay correct whenever the resolved
 * theme diverges from the system's (e.g. Dark forced while the device itself is in light mode) --
 * calling `isSystemInDarkTheme()` from a token would silently re-couple it to the system setting
 * and break parity/contrast in exactly that case. Always provided by [RollaMusicPlayerTheme];
 * never read outside it.
 */
internal val LocalRollaDarkTheme = staticCompositionLocalOf { false }

/**
 * The RollaMusicPlayer theme. Implements `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md` §5.
 *
 * - Dark is the primary, fully-designed theme (true-black OLED).
 * - The brand accent (blue) is **stable**: dynamic color is OFF by default, and even when
 *   enabled it only re-tints neutrals — `primary`/`onPrimary` are kept on-brand.
 *
 * Owned by m3-design-system-agent. Wraps every screen; UI reads tokens from
 * `MaterialTheme.colorScheme/typography/shapes` only — no hardcoded values.
 */
@Composable
fun RollaMusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // brand-stable by default (ui-style-guide §2)
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Keep the brand accent on-brand even under Material You.
            val brand = if (darkTheme) DarkColorScheme else LightColorScheme
            dynamic.copy(
                primary = brand.primary,
                onPrimary = brand.onPrimary,
                primaryContainer = brand.primaryContainer,
                onPrimaryContainer = brand.onPrimaryContainer,
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalRollaDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RollaTypography,
            shapes = RollaShapes,
            content = content,
        )
    }
}
