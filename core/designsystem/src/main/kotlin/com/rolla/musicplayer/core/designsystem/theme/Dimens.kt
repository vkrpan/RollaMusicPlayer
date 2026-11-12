// The token files are named by role (Color, Type, Shape, Dimens), not by their single declaration.
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Measured One UI dimensions (spec §5.4, docs/design/oneui-measurements.md). The reference phone is 3.0 px/dp at font
 * scale 1.0 (Phase 7 Task 0). Values marked "calibrated 2026-10-31" were corrected against the reference by the
 * Phase 7 device comparison ("Phase 7 calibration" in the measurements doc); the rest passed it or have no reference.
 * Screens read these; they never declare private dp literals for anything listed here.
 */
object RollaDimens {
    // Shell
    val headerHeight = 64.dp
    val headerTitleStart = 24.dp
    val screenEdge = 24.dp

    // Calibrated 2026-10-31: puts the Home ⋮ centre 35 dp and the detail-bar chevron 30.3 dp from the edge.
    val topBarEdge = 11.dp

    // Calibrated 2026-10-31: with topBarEdge 11 dp the detail title stays at 60.7 dp, as on the reference.
    val detailTitleGap = 0.dp
    val iconSize = 24.dp
    val minTouchTarget = 48.dp

    /** The Playlists header "+" icon box (calibrated 2026-10-31): draws ≈ 18 dp, measured 18.3 dp. */
    val headerActionIconLarge = 28.dp

    // Tabs and panel. Calibrated 2026-10-31: tab centre 88 dp (ref 86.3) below the status bar; the 48 dp row is still
    // the tab's touch height. panelTopGap re-calibrated on the device re-measure (ruling P22): 10 dp puts the panel top
    // ≈ 122 dp below the status bar (ref 120.3) and the tab → panel gap ≈ 26 dp (ref 26.3; 8 dp left it at 24).
    val tabRowHeight = 48.dp
    val tabSpacing = 22.dp
    val tabLabelVerticalPadding = 8.dp
    val panelTopGap = 10.dp
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

    /** Shadow under a playlist row lifted for drag-reorder; pre-restyle value, Phase 7 calibrates. */
    val dragLiftElevation = 6.dp

    val dividerThickness = 1.dp

    // Calibrated 2026-10-31: MusicNote draws 0.717 of its box, so 20 dp draws ≈ 14.4 dp (measured 14.7 dp).
    val placeholderGlyph = 20.dp
    val overflowGlyph = 20.dp // the gray ⋮ glyph on list rows
    val fastScrollWidth = 22.dp
    val fastScrollEnd = 8.dp

    /**
     * Rail height one A–Z label needs (spec §10: the 11 sp letter plus spacing). Below 27 slots the rail shows only
     * every k-th label.
     */
    val fastScrollLetterSlot = 16.dp

    /**
     * Six 16 dp slots: the shortest rail the container keeps when a top inset would squeeze it. At a large font scale
     * the slot grows to the letter's line height, so this holds fewer slots.
     */
    val fastScrollMinRailHeight = 96.dp

    // Row trailing geometry, measured from the panel's end edge on the reference (Tracks/Playlists).
    // The A–Z rail (Phase 5) occupies the last listDividerEnd; rows never move when it lands. Trailing text
    // and the divider stay clear of it, but the ⋮ 48 dp touch box reaches 4 dp into the rail strip:
    // Phase 5 decides hit priority.
    val listOverflowEnd = 26.dp // end of the ⋮ 48 dp touch box (glyph centre ≈ x 310 of 360)
    val listTrailingEnd = 43.dp // end of trailing text such as "0 tracks"
    val listDividerEnd: Dp get() = fastScrollWidth + fastScrollEnd

    // The folder mark on a FolderRow thumbnail (spec §8.1). Measured from the reference at 3.0 px/dp: a ~14 × 11 dp
    // glyph ~5 dp from the thumbnail's end and ~6 dp from its bottom. The 20 dp icon box puts the 24-grid glyph there
    // with a 2 dp inset. Provisional until Phase 7 calibration.
    val folderBadge = 20.dp
    val folderBadgeInset = 2.dp

    // Mini-player. Calibrated 2026-10-31: the reference pill runs edge to edge, so its art starts miniPlayerArtStart
    // (11 dp) from the screen edge.
    val miniPlayerHeight = 60.dp
    val miniPlayerSideMargin = 0.dp
    val miniPlayerArt = 38.dp
    val miniPlayerArtStart = 11.dp

    /**
     * The mini-player's Play and Queue icon boxes (calibrated 2026-10-31): Play draws ≈ 12.7 dp tall and Queue ≈ 15 dp
     * wide, as measured. Previous and next keep [iconSize].
     */
    val miniTransportCompactIcon = 19.dp

    /**
     * The note box on the mini-player's placeholder art (Phase 7 ruling P19): MusicNote draws 0.717 of its box, so
     * 16 dp draws ≈ 11.5 dp, against the measured 11.7 dp. It shows whenever the placeholder does, a failed load too.
     */
    val miniPlayerArtGlyph = 16.dp

    // Now Playing
    val npArtSize = 168.dp

    /**
     * Hero artwork on AlbumDetail/ArtistDetail (spec §8.7). No reference screenshot: borrows npArtSize until Phase 7.
     */
    val detailArtworkSize: Dp get() = npArtSize

    /**
     * Artwork top below the top of the window content, i.e. below the status bar (calibrated 2026-10-31: measured
     * 94.7 dp). It no longer depends on the status bar's height.
     */
    val npArtTopGap = 95.dp
    val npSeekThickness = 3.dp
    val npSeekThumb = 16.dp
    val npSeekInset = 29.dp
    val npPlayGlyph = 40.dp
    val npPlayTouchTarget = 64.dp

    // Calibrated 2026-10-31: Shuffle draws ≈ 15.3 × 17.3 dp and the play-order glyph ≈ 15.3 × 17 dp.
    val npTransportGlyph = 22.dp

    /**
     * The transport's previous and next glyph boxes (ruling P22: at npTransportGlyph they drew 15.3 dp wide against the
     * measured 17.7 dp; 24 dp draws ≈ 17.3 dp — it drew 18.7 dp, a pass, in the pre-calibration 26 dp box). Shuffle
     * and play-order keep [npTransportGlyph].
     */
    val npSkipGlyph = 24.dp

    // Calibrated 2026-10-31: the action-row Queue and Heart draw ≈ 17.7 dp and 16.7 × 18.7 dp.
    val npActionGlyph = 23.dp

    /**
     * The action row's "+" glyph box (ruling P22: at npActionGlyph it drew 15 dp against the measured 18.5 dp; like
     * the header + in [headerActionIconLarge], 28 dp draws ≈ 18.3 dp). Queue and the heart keep [npActionGlyph].
     */
    val npActionAddGlyph = 28.dp

    /**
     * Inset of the action row's and the transport's end 48 dp boxes from the screen edges (calibrated 2026-10-31):
     * their centres sit 43 dp from the nearer edge, as measured (43.2 and 42.7 dp).
     */
    val npControlEdgeInset = 19.dp

    /** Centre of the transport's previous and next controls from the nearer screen edge (calibrated 2026-10-31). */
    val npTransportInnerCentre = 111.dp

    // Equaliser
    val eqCardTop = 16.dp
    val eqSliderTrack = 3.dp
    val eqSliderLength = 237.dp
    val eqThumbDiameter = 13.dp

    /**
     * Minimum column pitch (spec §5.4 touch-width exception). Since Phase 7 ruling P12 the columns spread so the first
     * and last centres sit [eqColumnEdgeInset] from the screen edges, and the pitch never drops below this value.
     */
    val eqColumnPitch = 28.7.dp

    /** Centre of the first and last EQ columns from the nearer screen edge (Phase 7 ruling P12; measured 65.2 dp). */
    val eqColumnEdgeInset = 65.dp

    /** Start of the EQ preset caption from the screen edge (calibrated 2026-10-31: measured 52.3 dp). */
    val eqCaptionStart = 52.dp

    /**
     * From the frequency labels' baseline (the digits' bottom) to the bottom of the slider card (calibrated 2026-10-31:
     * measured 44 dp).
     */
    val eqCardBottomPadding = 44.dp

    /**
     * From the EQ value labels' baseline (the digits' bottom) to the top of the slider track (Phase 7 ruling P20:
     * measured 14.7 dp). The slider's half-thumb track inset counts inside this gap.
     */
    val eqValueLabelTrackGap = 14.7.dp
    val eqGridPitch = 24.dp
    val sliderThumbRing = 2.dp

    // Chips. chipGridStart calibrated 2026-10-31 (measured 28.3 dp from both edges).
    val chipHeight = 40.dp
    val chipGap = 17.dp
    val chipGridStart = 28.dp
    val chipHorizontalPadding = 16.dp

    /**
     * Vertical padding inside a LabelChip (custom tags fix round 1): keeps a wrapped label off the chip's edges, and
     * is half of chipHeight minus the chipLabel line height (40 − 22), so a one-line chip stays chipHeight tall.
     */
    val labelChipVerticalPadding = 9.dp

    /** Visual gap between chip rows (calibrated 2026-10-31: measured 14 dp). Columns stay [chipGap] apart. */
    val chipRowGap = 14.dp

    // Vertical row gap that renders the measured chipRowGap: each chip occupies a 48 dp touch height.
    val chipRowLayoutGap: Dp get() = (chipRowGap - (minTouchTarget - chipHeight)).coerceAtLeast(0.dp)

    // Custom tag chips (custom tags phase 4): the small display-only chips in track rows, on Now Playing and in the
    // Find tracks results (TagChip, TagChipLine). Unmeasured: there is no reference screenshot.
    /**
     * A tag chip's minimum height (no vertical padding): 4 dp taller than the tagChipLabel line (14 sp) at the default
     * font size, which sits centred; at larger font sizes the line fills the chip, which grows with it.
     */
    val tagChipHeight = 18.dp

    /** Start and end padding inside a tag chip. */
    val tagChipHorizontalPadding = 6.dp

    /** Between tag chips on a line and between the lines of a TagChipLine; RemovableChip's label-to-Close gap. */
    val tagChipGap = 4.dp

    /** From the line above (a row's artist, Now Playing's artist) to the top of its TagChipLine. */
    val tagChipLineTopGap = 2.dp

    /** The subtag chip's outline (TagChipStyle.Outline) and the outlined RemovableChip's. */
    val tagChipBorderWidth = 1.dp

    // Settings
    val settingsCardInset = 10.dp
    val settingsRowPadding = 19.dp
    val settingsDividerInset = 16.dp
    val settingsSliderTrack = 14.dp
    val settingsSliderThumb = 20.dp

    /**
     * End padding of a settings row's trailing control, such as a switch (calibrated 2026-10-31: the switch ends 32 dp
     * from the screen edge, 22 dp inside the card).
     */
    val settingsTrailingEnd = 22.dp

    /**
     * Start and end inset of the settings slider's track inside the card. The reference track starts 51 dp from the
     * screen edge and the card is inset [settingsCardInset] (10 dp), so 41 dp (ruling P22; 3a's 31 dp was an
     * arithmetic slip that measured the 51 dp from the card instead of the screen).
     */
    val settingsSliderInset = 41.dp

    /** Gap above each settings section header and below the last card (calibrated 2026-10-31: measured 13 dp). */
    val settingsSectionGap = 13.dp

    /** Gap between a settings section header and its card; pre-restyle value; Phase 7 calibrates. */
    val settingsHeaderGap = 8.dp

    /** Vertical padding inside a settings row or prose block; pre-restyle value; Phase 7 calibrates. */
    val settingsRowVerticalPadding = 16.dp

    /** Size of the in-row progress spinner (Rescan library); pre-restyle value; Phase 7 calibrates. */
    val settingsProgressSize = 20.dp

    /** Stroke width of the in-row progress spinner; pre-restyle value; Phase 7 calibrates. */
    val settingsProgressStroke = 2.dp

    /** Gap between a dialog option's radio and its label (theme picker); pre-restyle value; Phase 7 calibrates. */
    val dialogOptionLabelGap = 12.dp

    // Tag editors
    /**
     * Gap between the tag editors' fields and buttons: card items, the side-by-side Year and Track number, the Save and
     * Cancel pills (Phase 6b Task 4 review M3; it borrowed settingsHeaderGap until Phase 7). Unmeasured: no reference.
     */
    val editorFieldGap = 8.dp

    /**
     * The gap Material 3 leaves between a text field's container and its supporting text (M3 1.3's internal
     * SupportingTopPadding). OneUiTextField reads it to find the container's bottom under an error message, so a
     * trailing control stays centred on the container.
     */
    val fieldSupportingTextGap = 4.dp

    // Switch. Calibrated 2026-10-31: the reference track is 20 dp tall around a 16 dp thumb, a 2 dp inset all round.
    val switchTrackWidth = 35.dp
    val switchTrackHeight = 20.dp
    val switchThumb = 16.dp
    val switchThumbInset = 2.dp

    // Checkbox (Phase 7, 6b ruling P4): a round 20 dp box, a 2 dp ring when unchecked; 48 dp target when standalone.
    val checkboxSize = 20.dp
    val checkboxStroke = 2.dp
    val checkboxGlyph = 14.dp

    // Feature cards
    val featureCardSize = 143.dp
    val featureCardGap = 17.dp

    // Calibrated 2026-10-31: MusicNote draws 0.717 of its box, so 61 dp draws ≈ 43.7 dp, as measured.
    val featureCardGlyph = 61.dp

    // Not measured on the reference (no px for the card text gap or the card row's x); Phase 7 calibrates.
    val featureCardLabelGap = 8.dp
    val featureCardRowStart: Dp get() = listThumbStart // first card aligns with the list thumbnails

    // Mini-player. Calibrated 2026-10-31: the subtitle starts ≈ 5.7 dp after the art. miniPlayerEnd grew by the 4 dp
    // that miniPlayerSideMargin lost, so the transport keeps its distance from the screen edge.
    val miniPlayerArtTextGap = 5.dp
    val miniPlayerEnd = 8.dp

    // Now Playing vertical rhythm, derived from the measured glyph centres at 3.0 px/dp
    // (docs/design/oneui-measurements.md "Now Playing"); the Phase 7 comparison passed every one of them.
    // npArtGlyph calibrated 2026-10-31: 72 dp draws ≈ 51.6 dp (measured 51.3 dp).
    val npArtGlyph = 72.dp // placeholder note on the 168 dp artwork (spec §8.4)
    val npTitleTopGap = 25.dp // art bottom 298 → title line top ≈323
    val npArtistTopGap = 5.dp // title line bottom ≈351 → artist line top ≈356
    val npTransportTopGap = 10.dp // seek box bottom 613 → 64 dp transport box top 623
    val npBottomGap = 45.dp // transport box bottom 687 → navigation bar top 732

    // Queue sheet
    val queueCurrentGlyph = 16.dp // the small EqualizerBars glyph on the playing row; unmeasured

    // Search field (OneUiSearchField, Phase 6b ruling 1): a minTouchTarget pill. Not measured on the reference;
    // Phase 7 calibrates.
    val searchFieldStart = 16.dp // pill start to the leading Search glyph (and query end to pill end when empty)
    val searchFieldGlyphGap = 12.dp // Search glyph to the query text

    // Widget (spec §13). Insets and gaps are measured from the reference widget at 3.0 px/dp; the control cell
    // height reuses minTouchTarget.
    val widgetCardRadius = 24.dp // matches the launcher's system widget radius
    val widgetCardPaddingStart = 10.dp // reference artwork inset
    val widgetCardPaddingEnd = 10.dp // reference end inset of the last control
    val widgetArt = 69.dp
    val widgetArtRadius = 15.dp // calibrated 2026-10-31: measured 14.9 dp
    val widgetArtGap = 10.dp // artwork to the text column

    // Calibrated 2026-10-31 and re-calibrated on the device re-measure (ruling P22): at 40 dp the note drew 22.7 dp
    // wide against the measured 25.7 dp; 42 dp draws ≈ 30.1 × 23.8 dp, height (ref 28.3) and width (ref 25.7) within
    // ±2 dp.
    val widgetArtGlyph = 42.dp

    // Play spans 15.2 of 24 viewport units: 28 dp draws the measured 18 dp play glyph.
    val widgetControlGlyph = 28.dp
    val widgetProgressHeight = 3.dp
    val widgetTitleSize: TextUnit = 16.sp
    val widgetArtistSize: TextUnit = 13.sp
}
