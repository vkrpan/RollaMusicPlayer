package com.rolla.musicplayer.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderFractionTest {

    @Test
    fun fractionIsLinearInsideTheRange() {
        assertEquals(0.25f, sliderFraction(0.25f, 0f..1f), 0.0001f)
        assertEquals(0.5f, sliderFraction(0f, -1500f..1500f), 0.0001f)
    }

    @Test
    fun fractionClampsValuesOutsideTheRange() {
        assertEquals(0f, sliderFraction(-3f, 0f..1f), 0.0001f)
        assertEquals(1f, sliderFraction(7f, 0f..1f), 0.0001f)
    }

    @Test
    fun degenerateRangeYieldsZeroInsteadOfNaN() {
        assertEquals(0f, sliderFraction(5f, 5f..5f), 0.0001f)
    }
}
