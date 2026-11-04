package com.rolla.musicplayer.feature.equalizer

import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [eqColumnPitchFor] (Phase 7 ruling P12): the first and last centres eqColumnEdgeInset from the edges, the pitch never
 * below 28.7 dp.
 */
class EqColumnPitchTest {

    // 406.7 dp (the Motorola): (406.7 − 130) / 8 = 34.59 dp. Trivial stub (a constant eqColumnPitch) → 28.7 → RED.
    @Test
    fun wideCardSpreadsTheColumnsToTheEdgeInset() {
        assertEquals("pitch at 406.7 dp", 34.5875f, eqColumnPitchFor(406.7.dp, columnCount = 9).value, 0.001f)
    }

    // 300 dp would give (300 − 130) / 8 = 21.25 dp; the pitch stops at the spec §5.4 floor. Control: the floor is the
    // token. Mutation: drop coerceAtLeast(eqColumnPitch) → 21.25 → RED.
    @Test
    fun narrowCardNeverGoesBelowTheMinimumPitch() {
        assertEquals("control: the floor token", 28.7f, RollaDimens.eqColumnPitch.value, 0.001f)
        assertEquals("pitch at 300 dp", 28.7f, eqColumnPitchFor(300.dp, columnCount = 9).value, 0.001f)
    }
}
