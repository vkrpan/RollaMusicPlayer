package com.rolla.musicplayer.core.media

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackPlayerBuilderTest {

    // ExoPlayer.Builder has no getter for this flag, so the test reads Media3's package-private field
    // (ExoPlayer.java:299 in 1.11.1). A rename in a later Media3 fails this test loudly
    // (NoSuchFieldException) instead of passing silently.
    // Control: a fresh ExoPlayer.Builder reads true, so the default is on and our call is what turns it off.
    // Mutation: drop `.setUsePlatformDiagnostics(false)` from playbackPlayerBuilder -> RED (the default is true).
    @Test
    fun platformDiagnosticsAreOff() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val builder = playbackPlayerBuilder(context)

        val field = ExoPlayer.Builder::class.java.getDeclaredField("usePlatformDiagnostics")
        field.isAccessible = true

        assertTrue(field.getBoolean(ExoPlayer.Builder(context)))
        assertFalse(field.getBoolean(builder))
    }
}
