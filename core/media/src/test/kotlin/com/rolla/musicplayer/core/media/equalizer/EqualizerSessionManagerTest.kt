package com.rolla.musicplayer.core.media.equalizer

import androidx.media3.common.C
import com.rolla.musicplayer.core.testing.FakeEqualizerPreferences
import com.rolla.musicplayer.core.testing.FakeEqualizerPresetDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val SESSION_ID_A = 11
private const val SESSION_ID_B = 22
private val DEVICE_RANGE = shortArrayOf(-1500, 1500)

// A distinctive, all-different gain pattern -- lets assertions tell the nine gains apart.
private val DISTINCTIVE_GAINS: List<Short> = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

/** Resolves each of the nine target frequencies to its own distinct device band (index-based). */
private fun distinctBandFor(frequencyMilliHz: Int): Short {
    val frequencyHz = frequencyMilliHz / 1000
    val index = TARGET_FREQUENCIES_HZ.indexOf(frequencyHz)
    check(index >= 0) { "unexpected frequency $frequencyHz" }
    return index.toShort()
}

private fun newFakeDeviceEqualizer(): FakeDeviceEqualizer =
    FakeDeviceEqualizer(numberOfBands = TARGET_FREQUENCIES_HZ.size.toShort(), range = DEVICE_RANGE) { milliHz ->
        distinctBandFor(milliHz)
    }

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerSessionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakePresetDao: FakeEqualizerPresetDao
    private lateinit var fakePreferences: FakeEqualizerPreferences
    private lateinit var repository: EqualizerRepository
    private lateinit var controller: EqualizerController

    private val createdDevices = mutableListOf<FakeDeviceEqualizer>()
    private lateinit var manager: EqualizerSessionManager

    @Before
    fun setUp() {
        fakePresetDao = FakeEqualizerPresetDao()
        fakePreferences = FakeEqualizerPreferences()
        repository = EqualizerRepository(fakePresetDao, fakePreferences)

        createdDevices.clear()
        val factory = FakeDeviceEqualizerFactory { _, _ ->
            val device = newFakeDeviceEqualizer()
            createdDevices += device
            device
        }
        controller = EqualizerController(factory)
        manager = EqualizerSessionManager(controller, repository)
    }

    @Test
    fun `attachAndApplyPersisted with a valid id attaches and pushes persisted enabled and gains onto the device`() =
        runTest {
            fakePreferences.setEnabled(true)
            fakePreferences.setGainsMillibel(DISTINCTIVE_GAINS)

            manager.attachAndApplyPersisted(SESSION_ID_A)

            assertEquals(1, createdDevices.size)
            val device = createdDevices.single()
            assertTrue(device.enabled)
            TARGET_FREQUENCIES_HZ.forEachIndexed { index, frequencyHz ->
                assertEquals(
                    "gain for ${frequencyHz}Hz",
                    DISTINCTIVE_GAINS[index],
                    controller.currentGainForFrequency(frequencyHz),
                )
            }
            assertEquals(TARGET_FREQUENCIES_HZ.size, device.setBandLevelCalls.size)
        }

    @Test
    fun `attachAndApplyPersisted with unset session id stays detached with no device calls and does not throw`() =
        runTest {
            fakePreferences.setEnabled(true)
            fakePreferences.setGainsMillibel(DISTINCTIVE_GAINS)

            manager.attachAndApplyPersisted(C.AUDIO_SESSION_ID_UNSET)

            assertEquals(0, createdDevices.size)
            assertEquals(null, controller.capabilities())
        }

    @Test
    fun `called twice with different ids releases the previous device and re-applies persisted gains to the new one`() =
        runTest {
            fakePreferences.setEnabled(true)
            fakePreferences.setGainsMillibel(DISTINCTIVE_GAINS)

            manager.attachAndApplyPersisted(SESSION_ID_A)
            val first = createdDevices[0]

            manager.attachAndApplyPersisted(SESSION_ID_B)
            val second = createdDevices[1]

            assertEquals(2, createdDevices.size)
            assertTrue("previous device must be released on session change", first.released)
            assertFalse(second.released)
            assertTrue(second.enabled)
            TARGET_FREQUENCIES_HZ.forEachIndexed { index, frequencyHz ->
                assertEquals(
                    "gain for ${frequencyHz}Hz on the new device",
                    DISTINCTIVE_GAINS[index],
                    controller.currentGainForFrequency(frequencyHz),
                )
            }
        }

    @Test
    fun `called twice with the same id still re-attaches -- same-id dedup is the caller's job via StateFlow`() =
        runTest {
            fakePreferences.setEnabled(true)
            fakePreferences.setGainsMillibel(DISTINCTIVE_GAINS)

            manager.attachAndApplyPersisted(SESSION_ID_A)
            val first = createdDevices[0]

            manager.attachAndApplyPersisted(SESSION_ID_A)
            val second = createdDevices[1]

            // This manager has no memory of "already attached to this id" -- it always
            // release()s and re-creates. It is the StateFlow at the call site (documented in
            // EqualizerSessionManager's KDoc) that must not re-invoke this function for an
            // unchanged session id; this test documents that this manager itself does not dedup.
            assertEquals(2, createdDevices.size)
            assertTrue(first.released)
            assertFalse(second.released)
            assertTrue(second.enabled)
        }

    @Test
    fun `persisted gains list shorter than nine applies only the available prefix without throwing`() = runTest {
        // EqualizerRepository.currentSettings() always normalizes a wrong-size stored list back to
        // nine zero gains (see EqualizerRepositoryTest), so a short list can never actually reach
        // this manager through the real repository -- this exercises applySettings directly (see
        // its KDoc) to prove the manager's own defensive minOf bound never crashes if that
        // repository invariant ever breaks.
        val shortGains: List<Short> = listOf(111, 222, 333)
        manager.attachAndApplyPersisted(SESSION_ID_A)
        val device = createdDevices.single()
        val callsBeforeShortApply = device.setBandLevelCalls.size

        manager.applySettings(EqualizerSettings(enabled = true, gainsMillibel = shortGains))

        // Only the 3 available gains were applied on top -- no crash despite the size mismatch.
        assertEquals(callsBeforeShortApply + 3, device.setBandLevelCalls.size)
        assertEquals(111.toShort(), controller.currentGainForFrequency(TARGET_FREQUENCIES_HZ[0]))
        assertEquals(222.toShort(), controller.currentGainForFrequency(TARGET_FREQUENCIES_HZ[1]))
        assertEquals(333.toShort(), controller.currentGainForFrequency(TARGET_FREQUENCIES_HZ[2]))
    }

    @Test
    fun `release releases the attached device`() = runTest {
        manager.attachAndApplyPersisted(SESSION_ID_A)
        val device = createdDevices.single()

        manager.release()

        assertTrue(device.released)
    }

    @Test
    fun `release is safe when never attached`() {
        manager.release()
    }
}
