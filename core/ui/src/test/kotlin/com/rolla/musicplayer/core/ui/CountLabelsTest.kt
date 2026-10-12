package com.rolla.musicplayer.core.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CountLabelsTest {

    // Mutation: albumsCountLabel returns "$count albums" unconditionally → RED on 1.
    @Test
    fun `album counts pluralise`() {
        assertEquals("0 albums", albumsCountLabel(0))
        assertEquals("1 album", albumsCountLabel(1))
        assertEquals("2 albums", albumsCountLabel(2))
    }
}
