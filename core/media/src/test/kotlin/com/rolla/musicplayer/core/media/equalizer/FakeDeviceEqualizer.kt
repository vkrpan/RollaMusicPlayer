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
        set(value) {
            failIfDead()
            field = value
        }

    val getBandCalls = mutableListOf<Int>()
    val setBandLevelCalls = mutableListOf<Pair<Short, Short>>()

    var released = false
        private set

    /** Number of times [release] has actually been invoked on this instance. */
    var releaseCallCount = 0
        private set

    /**
     * When true, every effect operation throws [IllegalStateException], mirroring a native effect
     * the system has released/revoked underneath the controller. [release] stays functional so
     * tests can assert the controller cleans the dead effect up.
     */
    var deadEffect = false

    /** When true, [release] itself throws, mirroring releasing an already-invalidated effect. */
    var throwFromRelease = false

    private val bandLevels = mutableMapOf<Short, Short>()

    override fun getBand(frequencyMilliHz: Int): Short {
        failIfDead()
        getBandCalls += frequencyMilliHz
        return bandForMilliHz(frequencyMilliHz)
    }

    override fun getBandLevel(band: Short): Short {
        failIfDead()
        return bandLevels[band] ?: 0
    }

    override fun setBandLevel(band: Short, levelMillibel: Short) {
        failIfDead()
        setBandLevelCalls += band to levelMillibel
        bandLevels[band] = levelMillibel
    }

    override fun release() {
        if (throwFromRelease) error("effect already invalidated by the system")
        released = true
        releaseCallCount += 1
    }

    private fun failIfDead() {
        if (deadEffect) error("effect released/revoked by the system")
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
