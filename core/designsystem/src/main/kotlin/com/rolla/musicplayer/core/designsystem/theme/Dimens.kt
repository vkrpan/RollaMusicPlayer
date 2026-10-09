// The token files are named by role (Color, Type, Shape, Dimens), not by their single declaration.
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Measured One UI dimensions (spec §5.4, docs/design/oneui-measurements.md). They are PROVISIONAL at 3.0 px/dp
 * until Phase 7 recalibrates them against the reference device's real density and font scale.
 * Screens read these; they never declare private dp literals for anything listed here.
 */
object RollaDimens {
    // Shell
    val headerHeight = 64.dp
    val headerTitleStart = 24.dp
    val screenEdge = 24.dp
    val topBarEdge = 4.dp
    val detailTitleGap = 6.dp
    val iconSize = 24.dp
    val minTouchTarget = 48.dp

    // Tabs and panel
    val tabRowHeight = 56.dp
    val tabSpacing = 28.dp
    val tabLabelVerticalPadding = 8.dp
    val panelTopGap = 12.dp
    val panelRadius = 26.dp

    // Sort header and circle buttons. The visual values are measured; the layout values derive from them,
    // because each 34 dp circle sits centered in a 48 dp touch box.
    val sortHeaderHeight = 56.dp

    // 24 dp icon-box start = measured 22 dp Sort-glyph start - the glyph's 3 dp inset inside RollaIcons.Sort.
    val sortHeaderStart = 19.dp
    val sortLabelGap = 8.dp
    val circleButtonSize = 34.dp
    val circleButtonGlyph = 18.dp
    val circleButtonEnd = 15.dp
    val circleButtonGap = 14.dp
    val sortHeaderEnd: Dp get() = (circleButtonEnd - (minTouchTarget - circleButtonSize) / 2).coerceAtLeast(0.dp)
    val circleButtonLayoutGap: Dp get() = (circleButtonGap - (minTouchTarget - circleButtonSize)).coerceAtLeast(0.dp)

    // Lists
    val listThumb = 48.dp
    val listThumbStart = 18.dp
    val listTextStart = 82.dp
    val listRowHeight = 70.dp
    val singleLineRowHeight = 56.dp

    // Derived: the thumbnail-to-text gap that puts row text at listTextStart.
    val listThumbTextGap: Dp get() = listTextStart - listThumbStart - listThumb

    // Minimum gap between a row's title and its trailing text; not measured on the reference, Phase 7 calibrates.
    val listTrailingGap = 16.dp

    val dividerThickness = 1.dp
    val placeholderGlyph = 24.dp
    val overflowGlyph = 20.dp // the gray ⋮ glyph on list rows
    val fastScrollWidth = 22.dp
    val fastScrollEnd = 8.dp

    // Row trailing geometry, measured from the panel's end edge on the reference (Tracks/Playlists).
    // The A–Z rail (Phase 5) occupies the last listDividerEnd; rows never move when it lands. Trailing text
    // and the divider stay clear of it, but the ⋮ 48 dp touch box reaches 4 dp into the rail strip:
    // Phase 5 decides hit priority.
    val listOverflowEnd = 26.dp // end of the ⋮ 48 dp touch box (glyph centre ≈ x 310 of 360)
    val listTrailingEnd = 43.dp // end of trailing text such as "0 tracks"
    val listDividerEnd: Dp get() = fastScrollWidth + fastScrollEnd

    // Mini-player
    val miniPlayerHeight = 60.dp
    val miniPlayerSideMargin = 4.dp
    val miniPlayerArt = 38.dp
    val miniPlayerArtStart = 11.dp

    // Now Playing
    val npArtSize = 168.dp
    val npArtTop = 130.dp
    val npSeekThickness = 3.dp
    val npSeekThumb = 16.dp
    val npSeekInset = 29.dp
    val npPlayGlyph = 40.dp
    val npPlayTouchTarget = 64.dp
    val npTransportGlyph = 26.dp
    val npActionGlyph = 26.dp

    // Equaliser
    val eqCardTop = 16.dp
    val eqSliderTrack = 3.dp
    val eqSliderLength = 237.dp
    val eqThumbDiameter = 13.dp
    val eqColumnPitch = 28.7.dp
    val eqGridPitch = 24.dp
    val sliderThumbRing = 2.dp

    // Chips
    val chipHeight = 40.dp
    val chipGap = 17.dp
    val chipGridStart = 24.dp
    val chipHorizontalPadding = 16.dp

    // Vertical row gap that renders the measured chipGap; horizontal stays chipGap (chips are wider than 48 dp).
    val chipRowLayoutGap: Dp get() = (chipGap - (minTouchTarget - chipHeight)).coerceAtLeast(0.dp)

    // Settings
    val settingsCardInset = 10.dp
    val settingsRowPadding = 19.dp
    val settingsDividerInset = 16.dp
    val settingsSliderTrack = 14.dp
    val settingsSliderThumb = 20.dp

    // Switch (thumb scaled to fit the 17 dp track with a 1 dp inset)
    val switchTrackWidth = 35.dp
    val switchTrackHeight = 17.dp
    val switchThumb = 15.dp
    val switchThumbInset = 1.dp

    // Feature cards
    val featureCardSize = 143.dp
    val featureCardGap = 17.dp
    val featureCardGlyph = 44.dp

    // Not measured on the reference (no px for the card text gap or the card row's x); Phase 7 calibrates.
    val featureCardLabelGap = 8.dp
    val featureCardRowStart: Dp get() = listThumbStart // first card aligns with the list thumbnails
}
