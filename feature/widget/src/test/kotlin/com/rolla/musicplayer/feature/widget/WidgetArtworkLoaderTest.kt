package com.rolla.musicplayer.feature.widget

import android.content.ContentResolver
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [BitmapFactory]-backed decode paths (the happy path and malformed-bytes path) require a real
 * Android runtime -- `BitmapFactory` is an unimplemented JVM stub under plain JUnit -- so those
 * are verified on-device (see the widget-agent report). This suite covers everything reachable
 * without decoding: the null/blank short-circuit (and that it never touches the resolver) and the
 * exception-to-null failure contract for a resolver that throws.
 */
class WidgetArtworkLoaderTest {

    private val context: Context = mockk(relaxed = true)
    private val resolver: ContentResolver = mockk(relaxed = true)
    private val loader = WidgetArtworkLoader(context, Dispatchers.Unconfined)

    @Test
    fun `load returns null for a null path without touching the resolver`() = runTest {
        val bitmap = loader.load(null)

        assertNull(bitmap)
        verify(exactly = 0) { context.contentResolver }
    }

    @Test
    fun `load returns null for a blank path without touching the resolver`() = runTest {
        val bitmap = loader.load("   ")

        assertNull(bitmap)
        verify(exactly = 0) { context.contentResolver }
    }

    @Test
    fun `load returns null when the resolver throws SecurityException`() = runTest {
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } throws SecurityException("no grant")

        val bitmap = loader.load("content://media/local/art/1")

        assertNull(bitmap)
    }
}
