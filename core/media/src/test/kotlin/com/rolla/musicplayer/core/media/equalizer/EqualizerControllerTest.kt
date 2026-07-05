package com.rolla.musicplayer.core.media.equalizer

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

private const val SESSION_ID_A = 11
private const val SESSION_ID_B = 22
private const val EFFECT_PRIORITY = 0
private val DEVICE_RANGE = shortArrayOf(-1500, 1500)

// AOSP-typical 5-band center frequencies (Hz), used to model nearest-band resolution in tests.
private val FIVE_BAND_CENTERS_HZ = intArrayOf(60, 230, 910, 3600, 14000)

// Mirrors the nearest-center resolution behavior of Equalizer.getBand for a 5-band device.
private fun nearestBandFor(frequencyMilliHz: Int): Short {
    val frequencyHz = frequencyMilliHz / 1000
    var nearestIndex = 0
    var nearestDistance = Int.MAX_VALUE
    FIVE_BAND_CENTERS_HZ.forEachIndexed { index, center ->
        val distance = abs(center - frequencyHz)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
        }
    }
    return nearestIndex.toShort()
}

class EqualizerControllerTest {

    @Test
    fun `attach with unset session id stays detached`() {
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            fail("factory must not be called for an unset session id")
            error("unreachable")
        }
        val controller = EqualizerController(factory)

        controller.attach(C.AUDIO_SESSION_ID_UNSET)

        assertNull(controller.capabilities())
        controller.setEnabled(true)
        controller.setGainForFrequency(1000, 500)
        assertEquals(0, controller.currentGainForFrequency(1000).toInt())
    }

    @Test
    fun `attach creates the effect via the factory with the given session id`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)

        controller.attach(SESSION_ID_A)

        assertEquals(listOf(EFFECT_PRIORITY to SESSION_ID_A), factory.createCalls)
    }

    @Test
    fun `re-attach releases the previous effect before creating a new one`() {
        val first = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val second = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        var creations = 0
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            creations += 1
            if (creations == 1) first else second
        }
        val controller = EqualizerController(factory)

        controller.attach(SESSION_ID_A)
        controller.attach(SESSION_ID_B)

        assertTrue(first.released)
        assertFalse(second.released)
        assertEquals(
            listOf(EFFECT_PRIORITY to SESSION_ID_A, EFFECT_PRIORITY to SESSION_ID_B),
            factory.createCalls,
        )
    }

    @Test
    fun `re-attach releases the previous effect exactly once`() {
        val first = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val second = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        var creations = 0
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            creations += 1
            if (creations == 1) first else second
        }
        val controller = EqualizerController(factory)

        controller.attach(SESSION_ID_A)
        controller.attach(SESSION_ID_B)

        assertEquals(1, first.releaseCallCount)
        assertEquals(0, second.releaseCallCount)
    }

    @Test
    fun `factory throwing leaves the controller detached without propagating`() {
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            throw UnsupportedOperationException("no equalizer effect on this device")
        }
        val controller = EqualizerController(factory)

        controller.attach(SESSION_ID_A)

        assertNull(controller.capabilities())
    }

    @Test
    fun `factory throwing leaves every subsequent operation a safe no-op`() {
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            throw UnsupportedOperationException("no equalizer effect on this device")
        }
        val controller = EqualizerController(factory)

        controller.attach(SESSION_ID_A)

        // None of these should throw, and none should have any observable effect -- the
        // controller has nothing attached to forward them to.
        controller.setEnabled(true)
        controller.setGainForFrequency(1000, 500)

        assertNull(controller.capabilities())
        assertEquals(0, controller.currentGainForFrequency(1000).toInt())
    }

    @Test
    fun `capabilities reflects the device band count and level range`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        val capabilities = controller.capabilities()

        val expected = EqualizerCapabilities(
            minGainMillibel = DEVICE_RANGE[0],
            maxGainMillibel = DEVICE_RANGE[1],
            deviceBandCount = 5,
        )
        assertEquals(expected, capabilities)
    }

    @Test
    fun `setGainForFrequency converts Hz to milliHz before resolving the band`() {
        val targetBand: Short = 2
        val expectedMilliHz = 630_000
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { milliHz ->
            if (milliHz == expectedMilliHz) targetBand else -1
        }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.setGainForFrequency(630, 200)

        assertEquals(listOf(expectedMilliHz), fake.getBandCalls)
        assertEquals(listOf(targetBand to 200.toShort()), fake.setBandLevelCalls)
    }

    @Test
    fun `gain above device max clamps to max`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.setGainForFrequency(1000, 5000)

        assertEquals(DEVICE_RANGE[1], controller.currentGainForFrequency(1000))
    }

    @Test
    fun `gain below device min clamps to min`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.setGainForFrequency(1000, -5000)

        assertEquals(DEVICE_RANGE[0], controller.currentGainForFrequency(1000))
    }

    @Test
    fun `gain exactly at device max is applied unclamped`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.setGainForFrequency(1000, DEVICE_RANGE[1])

        assertEquals(DEVICE_RANGE[1], controller.currentGainForFrequency(1000))
    }

    @Test
    fun `gain exactly at device min is applied unclamped`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.setGainForFrequency(1000, DEVICE_RANGE[0])

        assertEquals(DEVICE_RANGE[0], controller.currentGainForFrequency(1000))
    }

    @Test
    fun `applying all target frequencies lands on the documented five-band pairing with last write wins`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { milliHz -> nearestBandFor(milliHz) }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        TARGET_FREQUENCIES_HZ.forEachIndexed { index, frequencyHz ->
            controller.setGainForFrequency(frequencyHz, (index + 1).toShort())
        }

        // All nine calls happen -- the controller does not deduplicate or batch them.
        assertEquals(TARGET_FREQUENCIES_HZ.size, fake.setBandLevelCalls.size)
        // ...but only five distinct physical bands actually exist on this fake device.
        assertEquals(5, fake.setBandLevelCalls.map { it.first }.distinct().size)

        // Documented pairing: 60Hz <- {40,80}, 230Hz <- {160,315}, 910Hz <- {630,1250},
        // 3600Hz <- {2500,5000}, 14000Hz <- {10000}. The second frequency of each pair
        // overwrites the first (last write wins) since both resolve to the same physical band.
        assertEquals(2.toShort(), controller.currentGainForFrequency(40))
        assertEquals(2.toShort(), controller.currentGainForFrequency(80))
        assertEquals(4.toShort(), controller.currentGainForFrequency(160))
        assertEquals(4.toShort(), controller.currentGainForFrequency(315))
        assertEquals(6.toShort(), controller.currentGainForFrequency(630))
        assertEquals(6.toShort(), controller.currentGainForFrequency(1250))
        assertEquals(8.toShort(), controller.currentGainForFrequency(2500))
        assertEquals(8.toShort(), controller.currentGainForFrequency(5000))
        assertEquals(9.toShort(), controller.currentGainForFrequency(10000))
    }

    @Test
    fun `setEnabled forwards to the attached device equalizer`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.setEnabled(true)

        assertTrue(fake.enabled)
    }

    @Test
    fun `release releases the device equalizer and is idempotent`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        controller.release()
        controller.release()

        assertTrue(fake.released)
        assertNull(controller.capabilities())
    }

    @Test
    fun `operations after release are no-ops`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)
        controller.release()

        controller.setEnabled(true)
        controller.setGainForFrequency(1000, 500)

        assertNull(controller.capabilities())
        assertEquals(0, controller.currentGainForFrequency(1000).toInt())
        assertFalse(fake.enabled)
    }

    @Test
    fun `effect dying mid-operation detaches the controller without propagating`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        // The system invalidates the native effect underneath the controller; the next operation
        // throws IllegalStateException inside the effect. The controller must swallow it, release
        // the dead effect, and detach so everything afterward is a safe no-op.
        fake.deadEffect = true
        controller.setGainForFrequency(1000, 500)

        assertEquals(1, fake.releaseCallCount)
        assertNull(controller.capabilities())
        controller.setEnabled(true)
        assertEquals(0, controller.currentGainForFrequency(1000).toInt())
    }

    @Test
    fun `release swallows an already-invalidated effect's failure and still detaches`() {
        val fake = FakeDeviceEqualizer(numberOfBands = 5, range = DEVICE_RANGE) { 0 }
        val factory = FakeDeviceEqualizerFactory { _, _ -> fake }
        val controller = EqualizerController(factory)
        controller.attach(SESSION_ID_A)

        fake.throwFromRelease = true
        controller.release()

        assertNull(controller.capabilities())
        // Detached for real: a second release must not reach the dead effect again.
        controller.release()
    }

    @Test
    fun `release before ever attaching does not throw`() {
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            fail("factory must not be called")
            error("unreachable")
        }
        val controller = EqualizerController(factory)

        controller.release()

        assertNull(controller.capabilities())
    }
}
