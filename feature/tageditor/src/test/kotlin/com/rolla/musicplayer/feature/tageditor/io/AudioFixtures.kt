package com.rolla.musicplayer.feature.tageditor.io

import java.io.ByteArrayOutputStream

/**
 * Hand-built, structurally-valid minimal audio byte streams used to exercise [TagReader]/
 * [TagWriter] against real jaudiotagger format readers/writers, without shipping binary fixture
 * files. Every builder produces the smallest stream that a real decoder's/tag-library's format
 * sniffing accepts -- the audio payload itself is silence/dummy bytes, since only the *container
 * and tag structure* matters for these tests.
 */
internal object AudioFixtures {

    private const val MP3_FRAME_SYNC_BYTE_0 = 0xFF
    private const val MP3_FRAME_HEADER_BYTE_1 = 0xFB // MPEG1, Layer III, no CRC
    private const val MP3_FRAME_HEADER_BYTE_2 = 0x90 // 128kbps, 44100Hz, no padding, not private
    private const val MP3_FRAME_HEADER_BYTE_3 = 0xC0 // mono, no copyright, not original, no emphasis
    private const val MP3_FRAME_LENGTH_BYTES = 417 // floor(144 * 128_000 / 44_100), padding = 0
    private const val MP3_DEFAULT_FRAME_COUNT = 30

    /**
     * A minimal, valid MPEG1 Layer III stream: [frameCount] identical fixed-size frames (128kbps,
     * 44100Hz, mono, no CRC), no ID3 tag. jaudiotagger's mp3 reader locates frames by header sync
     * plus a following-frame sanity check, so a single frame is not reliably enough -- several
     * repeated frames make the stream unambiguous.
     */
    fun mp3Bytes(frameCount: Int = MP3_DEFAULT_FRAME_COUNT): ByteArray {
        val header = byteArrayOf(
            MP3_FRAME_SYNC_BYTE_0.toByte(),
            MP3_FRAME_HEADER_BYTE_1.toByte(),
            MP3_FRAME_HEADER_BYTE_2.toByte(),
            MP3_FRAME_HEADER_BYTE_3.toByte(),
        )
        val frame = header + ByteArray(MP3_FRAME_LENGTH_BYTES - header.size)
        val out = ByteArrayOutputStream()
        repeat(frameCount) { out.write(frame) }
        return out.toByteArray()
    }

    private const val ID3V1_LENGTH = 128
    private const val ID3V1_TITLE_OFFSET = 3
    private const val ID3V1_ARTIST_OFFSET = 33
    private const val ID3V1_ALBUM_OFFSET = 63
    private const val ID3V1_YEAR_OFFSET = 93
    private const val ID3V1_TEXT_LENGTH = 30
    private const val ID3V1_YEAR_LENGTH = 4
    private const val ID3V1_1_TRACK_INDEX = 126
    private const val ID3V1_GENRE_INDEX = 127
    private const val ID3V1_GENRE_NONE = 0xFF

    /**
     * [mp3Bytes] followed by a 128-byte ID3v1 tag and no ID3v2 tag at all: the old-file case where jaudiotagger's
     * `getTagOrCreateDefault` would start an empty ID3v2 tag. The tag is "TAG", then 30-byte title, artist and album, a
     * 4-byte year, and, when the track is not 0, the ID3v1.1 track byte (after a zero comment terminator), then the
     * genre index (255 = none).
     */
    fun mp3WithId3v1OnlyBytes(fields: Id3v1Fields): ByteArray {
        val v1 = ByteArray(ID3V1_LENGTH)
        "TAG".toByteArray(Charsets.ISO_8859_1).copyInto(v1)
        mapOf(
            ID3V1_TITLE_OFFSET to fields.title,
            ID3V1_ARTIST_OFFSET to fields.artist,
            ID3V1_ALBUM_OFFSET to fields.album,
        ).forEach { (offset, text) ->
            text.toByteArray(Charsets.ISO_8859_1).take(ID3V1_TEXT_LENGTH).toByteArray().copyInto(v1, offset)
        }
        fields.year.toByteArray(Charsets.ISO_8859_1).take(ID3V1_YEAR_LENGTH).toByteArray()
            .copyInto(v1, ID3V1_YEAR_OFFSET)
        v1[ID3V1_1_TRACK_INDEX] = fields.track.toByte()
        v1[ID3V1_GENRE_INDEX] = fields.genre.toByte()
        return mp3Bytes() + v1
    }

    /** The fields an ID3v1(.1) tag can hold; [genre] is an index into the ID3v1 genre table (255 = none). */
    data class Id3v1Fields(
        val title: String,
        val artist: String,
        val album: String,
        val year: String = "",
        val track: Int = 0,
        val genre: Int = ID3V1_GENRE_NONE,
    )

    private const val FLAC_SAMPLE_RATE = 44_100
    private const val FLAC_CHANNELS = 2
    private const val FLAC_BITS_PER_SAMPLE = 16
    private const val FLAC_TOTAL_SAMPLES = 44_100L
    private const val FLAC_BLOCK_SIZE = 4096
    private const val FLAC_STREAMINFO_LENGTH = 34
    private const val FLAC_MD5_LENGTH = 16
    private const val FLAC_DUMMY_AUDIO_BYTE_COUNT = 64
    private const val CHANNELS_BIT_OFFSET = 41
    private const val SAMPLE_RATE_BIT_OFFSET = 44
    private const val BITS_PER_SAMPLE_BIT_OFFSET = 36
    private const val TOTAL_SAMPLES_BIT_MASK = 0xFFFFFFFFFL // 36 bits

    /**
     * A minimal, valid FLAC stream: the "fLaC" magic followed by a single STREAMINFO metadata
     * block (marked as the last metadata block) and a run of dummy bytes standing in for audio
     * frame data. jaudiotagger's FLAC reader derives sample rate/channels/bit depth/duration
     * entirely from STREAMINFO, and reads/writes Vorbis comments as a separate metadata block, so
     * this fixture intentionally carries no VORBIS_COMMENT block -- the "file with no tag at all"
     * case -- and lets [TagWriter.write] insert one on first save.
     */
    fun flacBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(streamInfoBlock())
        out.write(ByteArray(FLAC_DUMMY_AUDIO_BYTE_COUNT)) // stand-in for one or more audio frames
        return out.toByteArray()
    }

    private fun streamInfoBlock(): ByteArray {
        val out = ByteArrayOutputStream()
        val isLastMetadataBlock = 0x80
        val streamInfoBlockType = 0x00
        out.write(isLastMetadataBlock or streamInfoBlockType)
        out.write(uint24(FLAC_STREAMINFO_LENGTH))
        out.write(uint16(FLAC_BLOCK_SIZE)) // min block size
        out.write(uint16(FLAC_BLOCK_SIZE)) // max block size
        out.write(uint24(0)) // min frame size (unknown)
        out.write(uint24(0)) // max frame size (unknown)
        val packed = (FLAC_SAMPLE_RATE.toLong() shl SAMPLE_RATE_BIT_OFFSET) or
            ((FLAC_CHANNELS - 1).toLong() shl CHANNELS_BIT_OFFSET) or
            ((FLAC_BITS_PER_SAMPLE - 1).toLong() shl BITS_PER_SAMPLE_BIT_OFFSET) or
            (FLAC_TOTAL_SAMPLES and TOTAL_SAMPLES_BIT_MASK)
        out.write(uint64(packed))
        out.write(ByteArray(FLAC_MD5_LENGTH)) // MD5 signature, unchecked by jaudiotagger
        return out.toByteArray()
    }

    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    private fun uint16(value: Int): ByteArray = byteArrayOf(
        ((value shr BITS_PER_BYTE) and BYTE_MASK).toByte(),
        (value and BYTE_MASK).toByte(),
    )

    private fun uint24(value: Int): ByteArray = byteArrayOf(
        ((value shr (2 * BITS_PER_BYTE)) and BYTE_MASK).toByte(),
        ((value shr BITS_PER_BYTE) and BYTE_MASK).toByte(),
        (value and BYTE_MASK).toByte(),
    )

    private fun uint64(value: Long): ByteArray {
        val bytes = ByteArray(8)
        for (i in 0 until 8) {
            bytes[i] = ((value shr ((7 - i) * BITS_PER_BYTE)) and BYTE_MASK.toLong()).toByte()
        }
        return bytes
    }
}
