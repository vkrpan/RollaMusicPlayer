package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audits every foreground/background pairing the UI uses (spec §5.1) against WCAG 2.x AA:
 * 4.5:1 for text, 3:1 for non-text UI parts. Any token change must keep this green.
 */
class ContrastTest {

    @Test
    fun contrastRatioMatchesWcagReferenceValues() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.Black, Color.Black), 0.001f)
    }

    @Test
    fun darkPaletteMeetsWcagAa() = assertAllPairingsPass(DarkPalette)

    @Test
    fun lightPaletteMeetsWcagAa() = assertAllPairingsPass(LightPalette)

    // Phase 7 F2: a light placeholder or name field must stand apart from the #E8E8EA sheet and dialog. The boxes
    // are decorative and the field has a label, so this is a visible-edge floor, not WCAG's 3:1: white gives 1.22:1,
    // the old #E6E6E8 placeholder and #EBEBED field gave 1.02:1. Dark keeps its tokens (the dark field is the M3 one
    // step above). Control: the floor holds for the white tokens. Mutation: light sheetArtworkPlaceholder = #E6E6E8 or
    // light dialogFieldContainer = #EBEBED → RED.
    @Test
    fun lightSheetAndDialogContentsStandApartFromTheirSurface() {
        val surface = LightPalette.surfaceContainerHigh
        listOf(
            "sheetArtworkPlaceholder" to LightPalette.sheetArtworkPlaceholder,
            "dialogFieldContainer" to LightPalette.dialogFieldContainer,
        ).forEach { (name, color) ->
            val ratio = contrastRatio(color, surface)
            assertTrue("$name on surfaceContainerHigh: ${"%.2f".format(ratio)} < $VISIBLE_EDGE", ratio >= VISIBLE_EDGE)
        }
    }

    private fun assertAllPairingsPass(palette: RollaPalette) {
        val failures = pairingsOf(palette).filter { it.ratio < it.minimum }
        assertTrue("WCAG AA failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}

private const val TEXT = 4.5f
private const val NON_TEXT = 3.0f
private const val VISIBLE_EDGE = 1.2f
private const val LUMINANCE_OFFSET = 0.05f

private fun contrastRatio(foreground: Color, background: Color): Float {
    val fg = foreground.compositeOver(background).luminance()
    val bg = background.luminance()
    return (maxOf(fg, bg) + LUMINANCE_OFFSET) / (minOf(fg, bg) + LUMINANCE_OFFSET)
}

private class Pairing(val name: String, foreground: Color, background: Color, val minimum: Float) {
    val ratio: Float = contrastRatio(foreground, background)
    override fun toString(): String = "$name: ${"%.2f".format(ratio)} < $minimum"
}

// A flat table of audited pairings, not logic: its length grows with the pairings the UI uses.
@Suppress("LongMethod")
private fun pairingsOf(p: RollaPalette): List<Pairing> = listOf(
    Pairing("onSurface on background", p.onSurface, p.background, TEXT),
    Pairing("onSurface on surfaceContainer", p.onSurface, p.surfaceContainer, TEXT),
    Pairing("onSurface on surfaceContainerHigh", p.onSurface, p.surfaceContainerHigh, TEXT),
    Pairing("onSurfaceVariant on background", p.onSurfaceVariant, p.background, TEXT),
    Pairing("onSurfaceVariant on surfaceContainer", p.onSurfaceVariant, p.surfaceContainer, TEXT),
    // Also OneUiSearchField's placeholder and Search glyph (Phase 6b ruling 2).
    Pairing("onSurfaceVariant on surfaceContainerHigh", p.onSurfaceVariant, p.surfaceContainerHigh, TEXT),
    Pairing("tabUnselected on background", p.tabUnselected, p.background, TEXT),
    // The sort label and the EQ value and frequency labels (calibrated 2026-10-31; Phase 7 ruling P13).
    // Mutation: labelSecondary = surfaceContainerHighest (#333333, 1.42:1) → RED.
    Pairing("labelSecondary on surfaceContainer", p.labelSecondary, p.surfaceContainer, TEXT),
    // The EQ preset caption on the screen background (calibrated 2026-10-31; Phase 7 ruling P13).
    // Mutation: dark eqCaption = background (#000000, 1:1) → RED.
    Pairing("eqCaption on background", p.eqCaption, p.background, TEXT),
    Pairing("accentText on background", p.accentText, p.background, TEXT),
    Pairing("accentText on surfaceContainer", p.accentText, p.surfaceContainer, TEXT),
    Pairing("accentText on surfaceContainerHigh", p.accentText, p.surfaceContainerHigh, TEXT),
    // Also the tonal destructive OneUiPillButton's label on its surfaceContainerHigh container, the tag editor's
    // "Remove artwork" (per-track artwork Task 7): 4.64:1 dark (#FF6060 on #2D2D2F), 5.28:1 light (#BA1A1A on
    // #E8E8EA). Mutation: dark destructiveText = error (#FF5A5A, 4.49:1) → RED.
    Pairing("destructiveText on surfaceContainerHigh", p.destructiveText, p.surfaceContainerHigh, TEXT),
    // Also OneUiTextField's error supporting text on a tag-editor card (Phase 6b ruling 2).
    Pairing("destructiveText on surfaceContainer", p.destructiveText, p.surfaceContainer, TEXT),
    Pairing("onPrimary on primary", p.onPrimary, p.primary, TEXT),
    Pairing("onSurface on miniPlayerContainer", p.onSurface, p.miniPlayerContainer, TEXT),
    Pairing("fastScrollIndex on fastScrollTrack", p.onSurfaceVariant, p.fastScrollTrack, TEXT),
    Pairing("onSurface on nowPlaying middle", p.onSurface, p.nowPlayingGradient.middle, TEXT),
    Pairing("onSurface on nowPlaying washEnd", p.onSurface, p.nowPlayingGradient.washEnd, TEXT),
    // The Now Playing top bar's glyphs (chevron, volume, EQ bars, ⋮) sit on the gradient's top stop (#080808 dark since
    // 2026-10-31). Mutation: dark nowPlayingGradient.top = #E0E0E0 → RED.
    Pairing("onSurface glyphs on nowPlaying top", p.onSurface, p.nowPlayingGradient.top, NON_TEXT),
    Pairing("primary on surfaceContainer", p.primary, p.surfaceContainer, NON_TEXT),
    Pairing("primary on surfaceContainerHigh", p.primary, p.surfaceContainerHigh, NON_TEXT),
    Pairing("primary on background", p.primary, p.background, NON_TEXT),
    Pairing("sliderInactiveTrack on surfaceContainer", p.sliderInactiveTrack, p.surfaceContainer, NON_TEXT),
    Pairing("switchThumb on sliderInactiveTrack", p.switchThumb, p.sliderInactiveTrack, NON_TEXT),
    Pairing("switchThumb on primary", p.switchThumb, p.primary, NON_TEXT),
    Pairing("seekTrackActive on nowPlaying middle", p.seekTrackActive, p.nowPlayingGradient.middle, NON_TEXT),
    Pairing("seekTrackInactive on nowPlaying middle", p.seekTrackInactive, p.nowPlayingGradient.middle, NON_TEXT),
    Pairing("seekTrackInactive on nowPlaying washEnd", p.seekTrackInactive, p.nowPlayingGradient.washEnd, NON_TEXT),
    Pairing("placeholderGlyph on artworkPlaceholder", p.artworkPlaceholderGlyph, p.artworkPlaceholder, NON_TEXT),
    Pairing(
        "placeholderGlyph on artworkPlaceholderLarge",
        p.artworkPlaceholderGlyph,
        p.artworkPlaceholderLarge,
        NON_TEXT,
    ),
    // Dark miniPlayerArtPlaceholder is the opaque #504C5A since 2026-10-31 (8.12:1); light stays black @ 10 % over the
    // pill. Mutation: dark miniPlayerArtPlaceholder = #D0D0D0 → RED.
    Pairing(
        "miniPlayerArtGlyph on miniPlayerArtPlaceholder over miniPlayerContainer",
        p.miniPlayerArtGlyph,
        p.miniPlayerArtPlaceholder.compositeOver(p.miniPlayerContainer),
        NON_TEXT,
    ),
    Pairing("folderBadgeStroke on folderBadgeFill", p.folderBadgeStroke, p.folderBadgeFill, NON_TEXT),
    // OneUiCheckbox (Phase 7): the unchecked ring is onSurfaceVariant (outline is only 2.60:1 on the dark panel), the
    // checked disc primary on the selected row's surfaceContainerHigh highlight. Mutation: ring = outline → RED.
    Pairing("checkbox ring on surfaceContainer", p.onSurfaceVariant, p.surfaceContainer, NON_TEXT),
    Pairing("checkbox disc on the selected row", p.primary, p.surfaceContainerHigh, NON_TEXT),
    // Phase 7 F2: light 4.27:1. Mutation: light sheetArtworkPlaceholder = #D5D5D8 (2.92:1) → RED.
    Pairing(
        "placeholderGlyph on sheetArtworkPlaceholder",
        p.artworkPlaceholderGlyph,
        p.sheetArtworkPlaceholder,
        NON_TEXT,
    ),
) + tagChipPairingsOf(p)

// Custom tag chips (custom tags phase 4). A tag chip (Tonal) carries its own fill; a subtag chip (Outline) has none,
// so its label and outline are audited on every surface it sits on: track rows on the panel, the Find tracks strip on
// the background, Now Playing on each gradient stop. Mutation: dark subTagChipBorder = outlineVariant (#3A3A3C,
// 1.58:1 on surfaceContainer) → RED.
private fun tagChipPairingsOf(p: RollaPalette): List<Pairing> {
    val nowPlayingStops = with(p.nowPlayingGradient) {
        listOf("top" to top, "middle" to middle, "washStart" to washStart, "washEnd" to washEnd)
    }
    val subTagSurfaces = listOf("surfaceContainer" to p.surfaceContainer, "background" to p.background) +
        nowPlayingStops.map { (name, color) -> "nowPlaying $name" to color }
    return listOf(Pairing("tagChipLabel on tagChipContainer", p.tagChipLabel, p.tagChipContainer, TEXT)) +
        subTagSurfaces.map { (name, color) -> Pairing("subTagChipLabel on $name", p.subTagChipLabel, color, TEXT) } +
        subTagSurfaces.map { (name, color) ->
            Pairing("subTagChipBorder on $name", p.subTagChipBorder, color, NON_TEXT)
        }
}
