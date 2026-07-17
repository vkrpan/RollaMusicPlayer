package com.rolla.musicplayer.baselineprofile

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException

/**
 * Baseline Profile generation (and benchmarking) runs on managed devices -- and often on fresh
 * emulators -- with an empty MediaStore. Without at least a few songs, the library-scroll and
 * now-playing legs of the critical journey in [BaselineProfileGenerator] / [ScrollBenchmark] have
 * nothing to exercise and silently no-op.
 *
 * This seeds the on-device MediaStore with a handful of tiny, valid, silent WAV files -- purely
 * local file generation, no network -- only when the MediaStore is empty. A physical device that
 * already has a real music library is left untouched.
 */
internal object MediaStoreSeeder {

    private const val SAMPLE_RATE_HZ = 8_000
    private const val DURATION_SECONDS = 1
    private const val WAV_HEADER_SIZE = 44
    private const val PCM_SILENCE_8_BIT = 128
    private const val DEFAULT_SEED_COUNT = 15

    fun seedIfEmpty(count: Int = DEFAULT_SEED_COUNT) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val uiAutomation = instrumentation.uiAutomation
        uiAutomation.adoptShellPermissionIdentity()
        try {
            if (existingSongCount(context) > 0) return
            // RELATIVE_PATH / IS_PENDING require API 29+. Below that, either the device already
            // has real music (caught by the count check above) or it's a bare device that simply
            // runs the startup-only leg of the journey -- no crash either way.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                repeat(count) { index -> insertSilentWav(context, index) }
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun existingSongCount(context: Context): Int {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val cursor =
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null,
            )
        return cursor?.use { it.count } ?: 0
    }

    private fun insertSilentWav(context: Context, index: Int) {
        val ordinal = index + 1
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "Baseline Song %02d.wav".format(ordinal))
                put(MediaStore.Audio.Media.TITLE, "Baseline Song %02d".format(ordinal))
                put(MediaStore.Audio.Media.ARTIST, "Baseline Artist")
                put(MediaStore.Audio.Media.ALBUM, "Baseline Album")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return
        try {
            resolver.openOutputStream(uri)?.use { out -> out.write(wavBytes()) }
            val donePending = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, donePending, null, null)
        } catch (io: IOException) {
            resolver.delete(uri, null, null)
        }
    }

    /** A minimal, valid 1-second 8kHz 8-bit mono PCM WAV file: 44-byte header + silence. */
    private fun wavBytes(): ByteArray {
        val sampleCount = SAMPLE_RATE_HZ * DURATION_SECONDS
        val dataSize = sampleCount // 8-bit mono -> 1 byte per sample
        val byteRate = SAMPLE_RATE_HZ // (bitsPerSample / 8) * channels * sampleRate = 1 * 1 * rate
        val bytes = ByteArray(WAV_HEADER_SIZE + dataSize)

        fun writeAscii(offset: Int, text: String) {
            text.forEachIndexed { i, c -> bytes[offset + i] = c.code.toByte() }
        }
        fun writeLeInt(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun writeLeShort(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeLeInt(4, 36 + dataSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeLeInt(16, 16) // fmt chunk size
        writeLeShort(20, 1) // PCM
        writeLeShort(22, 1) // mono
        writeLeInt(24, SAMPLE_RATE_HZ)
        writeLeInt(28, byteRate)
        writeLeShort(32, 1) // block align: 1 channel * 8 bits / 8
        writeLeShort(34, 8) // bits per sample
        writeAscii(36, "data")
        writeLeInt(40, dataSize)
        // 8-bit unsigned PCM silence is mid-scale (128), not 0.
        for (i in WAV_HEADER_SIZE until bytes.size) {
            bytes[i] = PCM_SILENCE_8_BIT.toByte()
        }
        return bytes
    }
}
