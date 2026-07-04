package com.rolla.musicplayer.core.media.equalizer

/**
 * Test double for [DeviceEqualizer]. Records every call so tests can assert the exact arguments
 * [EqualizerController] passes through (the Hz -> milliHz conversion, the resolved band, clamping)
 * without ever touching the real `android.media.audiofx.Equalizer`.
 */
class FakeDeviceEqualizer(
    override val numberOfBands: Short,
    range: ShortArray,
    private val bandForMilliHz: (Int) -> Short,
) : DeviceEqualizer {

    override val bandLevelRange: ShortArray = range

    override var enabled: Boolean = false

    val getBandCalls = mutableListOf<Int>()
    val setBandLevelCalls = mutableListOf<Pair<Short, Short>>()

    var released = false
        private set

    private val bandLevels = mutableMapOf<Short, Short>()

    override fun getBand(frequencyMilliHz: Int): Short {
        getBandCalls += frequencyMilliHz
        return bandForMilliHz(frequencyMilliHz)
    }

    override fun getBandLevel(band: Short): Short = bandLevels[band] ?: 0

    override fun setBandLevel(band: Short, levelMillibel: Short) {
        setBandLevelCalls += band to levelMillibel
        bandLevels[band] = levelMillibel
    }

    override fun release() {
        released = true
    }
}

/** Test double for [DeviceEqualizer.Factory]. [onCreate] may throw to simulate device failure. */
class FakeDeviceEqualizerFactory(
    private val onCreate: (priority: Int, audioSessionId: Int) -> DeviceEqualizer,
) : DeviceEqualizer.Factory {

    val createCalls = mutableListOf<Pair<Int, Int>>()

    override fun create(priority: Int, audioSessionId: Int): DeviceEqualizer {
        createCalls += priority to audioSessionId
        return onCreate(priority, audioSessionId)
    }
}
