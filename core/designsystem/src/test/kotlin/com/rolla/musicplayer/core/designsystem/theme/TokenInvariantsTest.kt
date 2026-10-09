package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenInvariantsTest {

    @Test
    fun chipRowsLeaveRoomForFullTouchTargets() {
        // Each chip's 48 dp touch target must not overlap the next row's. This also guards chipRowLayoutGap: if it
        // fails, that token's clamp to 0 dp silently renders the chip rows further apart than measured.
        assertTrue(RollaDimens.chipHeight + RollaDimens.chipGap >= RollaDimens.minTouchTarget)
    }

    @Test
    fun acceptedTouchTargetExceptionsStayAboveWcag22Minimum() {
        // Spec §5.4: EQ columns and the A–Z rail are accepted below 48 dp, but never below WCAG 2.2 AA's 24 dp.
        assertTrue(RollaDimens.eqColumnPitch >= 24.dp)
        assertTrue(RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd >= 24.dp)
    }

    @Test
    fun switchThumbFitsInsideTrack() {
        assertTrue(RollaDimens.switchThumb + RollaDimens.switchThumbInset * 2 <= RollaDimens.switchTrackHeight)
    }

    @Test
    fun listTextNeverOverlapsThumbnail() {
        assertTrue(RollaDimens.listTextStart >= RollaDimens.listThumbStart + RollaDimens.listThumb)
    }

    @Test
    fun circleButtonEndLeavesRoomForTouchSlack() {
        // Otherwise sortHeaderEnd's clamp to 0 dp silently pushes the circle off its measured position.
        assertTrue(RollaDimens.circleButtonEnd >= (RollaDimens.minTouchTarget - RollaDimens.circleButtonSize) / 2)
    }

    @Test
    fun circleButtonGapLeavesRoomForTouchSlack() {
        // Otherwise circleButtonLayoutGap's clamp to 0 dp silently renders the circles further apart than measured.
        assertTrue(RollaDimens.circleButtonGap >= RollaDimens.minTouchTarget - RollaDimens.circleButtonSize)
    }

    @Test
    fun rowTrailingContentStaysClearOfTheFastScrollRail() {
        // Rows end where the measured reference rows end; Phase 5's rail sits in the space after them.
        assertTrue(RollaDimens.listTrailingEnd >= RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd)
        assertEquals(RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd, RollaDimens.listDividerEnd)
    }

    @Test
    fun unselectedTabIsSmallerThanSelectedTab() {
        assertTrue(TabUnselectedStyle.fontSize.value < TabSelectedStyle.fontSize.value)
    }
}
