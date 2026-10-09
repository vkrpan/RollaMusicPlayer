package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * One UI typography (spec §5.2). The family is the system default, which renders as One UI's own font on Samsung
 * devices. Sizes are PROVISIONAL until the Phase 7 device calibration. Color is always applied at the call site.
 * Owned by m3-design-system-agent.
 */
private val RollaFontFamily = FontFamily.Default

private fun rollaStyle(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = RollaFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // M3's own default: text sits centered in its declared line height inside fixed-height rows.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

internal val AppTitleStyle = rollaStyle(22, FontWeight.Bold, 28)
internal val ScreenTitleStyle = rollaStyle(22, FontWeight.Bold, 28)
internal val TabSelectedStyle = rollaStyle(23, FontWeight.Normal, 30)
internal val TabUnselectedStyle = rollaStyle(14, FontWeight.Normal, 20)
internal val SortLabelStyle = rollaStyle(14, FontWeight.Medium, 20)
internal val SongTitleStyle = rollaStyle(17, FontWeight.Normal, 22)
internal val ArtistNameStyle = rollaStyle(13, FontWeight.Normal, 18)
internal val RowTrailingStyle = rollaStyle(13, FontWeight.Normal, 18)
internal val MiniPlayerTitleStyle = rollaStyle(16, FontWeight.SemiBold, 21)
internal val MiniPlayerSubtitleStyle = rollaStyle(12, FontWeight.Normal, 16)
internal val NowPlayingTitleStyle = rollaStyle(21, FontWeight.Normal, 27)
internal val NowPlayingArtistStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val MetadataStyle = rollaStyle(11, FontWeight.Normal, 14)
internal val SectionHeaderStyle = rollaStyle(13, FontWeight.Medium, 18)
internal val SettingTitleStyle = rollaStyle(17, FontWeight.Normal, 22)
internal val SettingValueStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val SettingSubtitleStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val ChipLabelStyle = rollaStyle(17, FontWeight.Bold, 22)
internal val CaptionStyle = rollaStyle(14, FontWeight.Normal, 20)
internal val EqValueStyle = rollaStyle(14, FontWeight.Normal, 18)
internal val EqFrequencyStyle = rollaStyle(12, FontWeight.Normal, 16)
internal val FeatureCardLabelStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val FeatureCardCountStyle = rollaStyle(12, FontWeight.Normal, 16)
internal val FastScrollLetterStyle = rollaStyle(11, FontWeight.Normal, 13)
internal val EmptyStateStyle = rollaStyle(17, FontWeight.Normal, 22)

/** M3 roles reset to One UI sizes so stock components (dialogs, menus, buttons) match without overrides. */
val RollaTypography: Typography = Typography(
    headlineMedium = ScreenTitleStyle,
    headlineSmall = NowPlayingTitleStyle,
    titleLarge = TabSelectedStyle,
    titleMedium = SongTitleStyle,
    bodyLarge = rollaStyle(17, FontWeight.Normal, 22),
    bodyMedium = rollaStyle(14, FontWeight.Normal, 19),
    bodySmall = rollaStyle(13, FontWeight.Normal, 18),
    labelLarge = rollaStyle(14, FontWeight.Medium, 20),
    labelMedium = SectionHeaderStyle,
    labelSmall = MetadataStyle,
)

// ---- Semantic typography (spec §5.2). Read via MaterialTheme.typography.<name>; color comes from the caller. ----

val Typography.appTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = AppTitleStyle
val Typography.screenTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = ScreenTitleStyle
val Typography.tabSelected: TextStyle
    @Composable @ReadOnlyComposable
    get() = TabSelectedStyle
val Typography.tabUnselected: TextStyle
    @Composable @ReadOnlyComposable
    get() = TabUnselectedStyle
val Typography.sortLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = SortLabelStyle
val Typography.songTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = SongTitleStyle
val Typography.artistName: TextStyle
    @Composable @ReadOnlyComposable
    get() = ArtistNameStyle
val Typography.rowTrailing: TextStyle
    @Composable @ReadOnlyComposable
    get() = RowTrailingStyle
val Typography.miniPlayerTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MiniPlayerTitleStyle
val Typography.miniPlayerSubtitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MiniPlayerSubtitleStyle
val Typography.nowPlayingTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = NowPlayingTitleStyle
val Typography.nowPlayingArtist: TextStyle
    @Composable @ReadOnlyComposable
    get() = NowPlayingArtistStyle
val Typography.metadata: TextStyle
    @Composable @ReadOnlyComposable
    get() = MetadataStyle
val Typography.sectionHeader: TextStyle
    @Composable @ReadOnlyComposable
    get() = SectionHeaderStyle
val Typography.settingTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = SettingTitleStyle
val Typography.settingValue: TextStyle
    @Composable @ReadOnlyComposable
    get() = SettingValueStyle
val Typography.settingSubtitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = SettingSubtitleStyle
val Typography.chipLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = ChipLabelStyle
val Typography.caption: TextStyle
    @Composable @ReadOnlyComposable
    get() = CaptionStyle
val Typography.eqValue: TextStyle
    @Composable @ReadOnlyComposable
    get() = EqValueStyle
val Typography.eqFrequency: TextStyle
    @Composable @ReadOnlyComposable
    get() = EqFrequencyStyle
val Typography.featureCardLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = FeatureCardLabelStyle
val Typography.featureCardCount: TextStyle
    @Composable @ReadOnlyComposable
    get() = FeatureCardCountStyle
val Typography.fastScrollLetter: TextStyle
    @Composable @ReadOnlyComposable
    get() = FastScrollLetterStyle
val Typography.emptyState: TextStyle
    @Composable @ReadOnlyComposable
    get() = EmptyStateStyle

// Convenience accessor kept for existing callers. It is a top-level property, not a MaterialTheme member: import
// com.rolla.musicplayer.core.designsystem.theme.typographyTokens and write typographyTokens.songTitle, which is the
// same as MaterialTheme.typography.songTitle (the semantic styles above are extensions on Typography).
val typographyTokens: Typography
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography
