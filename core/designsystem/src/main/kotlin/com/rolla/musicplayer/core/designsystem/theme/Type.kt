package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography for RollaMusicPlayer.
 *
 * Implements `.claude/rules/ui-style-guide.md` §3. Base scale below; the semantic
 * extensions (screenTitle, tabSelected/Unselected, songTitle, artistName, sectionHeader,
 * metadata) are the names UI code should use. Color is applied at the call site
 * (e.g. `color = MaterialTheme.colorScheme.onSurfaceVariant`), never baked into the style.
 *
 * Owned by m3-design-system-agent. To adopt a custom One UI–like family, set [RollaFontFamily]
 * and thread it through the styles below.
 */
private val RollaFontFamily = FontFamily.Default

val RollaTypography: Typography = Typography(
    // Large One UI screen title
    headlineMedium = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    // Now-playing title
    headlineSmall = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    // Selected tab
    titleLarge = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // Unselected tab / list item title
    titleMedium = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    // List item subtitle (artist / path / count)
    bodyMedium = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Captions / EQ description
    bodySmall = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Settings section header
    labelMedium = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    // Frequency / count micro-labels
    labelSmall = TextStyle(
        fontFamily = RollaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

// ---- Semantic typography (ui-style-guide §3) ----
// Read via `MaterialTheme.typography.songTitle`, etc. Color is applied by the caller.

val Typography.screenTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = headlineMedium
val Typography.nowPlayingTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = headlineSmall
val Typography.tabSelected: TextStyle
    @Composable @ReadOnlyComposable
    get() = titleLarge
val Typography.tabUnselected: TextStyle
    @Composable @ReadOnlyComposable
    get() = titleMedium
val Typography.songTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = titleMedium
val Typography.artistName: TextStyle
    @Composable @ReadOnlyComposable
    get() = bodyMedium
val Typography.sectionHeader: TextStyle
    @Composable @ReadOnlyComposable
    get() = labelMedium
val Typography.metadata: TextStyle
    @Composable @ReadOnlyComposable
    get() = labelSmall

// Convenience accessor so callers can write `MaterialTheme.typographyTokens.songTitle` if preferred.
val typographyTokens: Typography
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography
