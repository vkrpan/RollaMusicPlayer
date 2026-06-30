package com.rolla.musicplayer.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The RollaMusicPlayer theme. Implements `.claude/rules/ui-style-guide.md`.
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RollaTypography,
        shapes = RollaShapes,
        content = content,
    )
}
