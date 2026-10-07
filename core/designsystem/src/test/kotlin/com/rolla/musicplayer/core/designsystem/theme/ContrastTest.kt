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

    private fun assertAllPairingsPass(palette: RollaPalette) {
        val failures = pairingsOf(palette).filter { it.ratio < it.minimum }
        assertTrue("WCAG AA failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}

private const val TEXT = 4.5f
private const val NON_TEXT = 3.0f
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
    Pairing("onSurfaceVariant on surfaceContainerHigh", p.onSurfaceVariant, p.surfaceContainerHigh, TEXT),
    Pairing("tabUnselected on background", p.tabUnselected, p.background, TEXT),
    Pairing("accentText on background", p.accentText, p.background, TEXT),
    Pairing("accentText on surfaceContainer", p.accentText, p.surfaceContainer, TEXT),
    Pairing("accentText on surfaceContainerHigh", p.accentText, p.surfaceContainerHigh, TEXT),
    Pairing("onPrimary on primary", p.onPrimary, p.primary, TEXT),
    Pairing("onSurface on miniPlayerContainer", p.onSurface, p.miniPlayerContainer, TEXT),
    Pairing("fastScrollIndex on fastScrollTrack", p.onSurfaceVariant, p.fastScrollTrack, TEXT),
    Pairing("onSurface on nowPlaying middle", p.onSurface, p.nowPlayingGradient.middle, TEXT),
    Pairing("onSurface on nowPlaying washEnd", p.onSurface, p.nowPlayingGradient.washEnd, TEXT),
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
    Pairing(
        "miniPlayerArtGlyph on miniPlayerArtPlaceholder over miniPlayerContainer",
        p.miniPlayerArtGlyph,
        p.miniPlayerArtPlaceholder.compositeOver(p.miniPlayerContainer),
        NON_TEXT,
    ),
)
