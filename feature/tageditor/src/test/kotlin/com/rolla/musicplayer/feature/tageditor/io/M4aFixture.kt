package com.rolla.musicplayer.feature.tageditor.io

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Hand-built, minimal-but-structurally-valid MP4/M4A container: ftyp + moov (mvhd, trak with
 * mdia/minf/stbl including an mp4a sample entry with an esds/AAC decoder config) + mdat.
 *
 * Box sizes are never computed by hand: every box's 4-byte size prefix is derived from the actual
 * assembled payload length via [box], so a structural change here cannot silently produce a wrong
 * length the way manual offset arithmetic could. The one value that genuinely depends on the rest
 * of the file -- stco's chunk offset, which must point at mdat's payload -- is resolved by
 * building moov twice: once with a placeholder offset to measure its own encoded length, then
 * again with the real offset now that ftyp+moov's total length is known. Both builds are
 * byte-identical in length; only that one field's value differs, so this is safe.
 */
internal object M4aFixture {

    private val US_ASCII = StandardCharsets.US_ASCII

    private const val SAMPLE_RATE = 44_100
    private const val DUMMY_SAMPLE_BYTE_COUNT = 100
    private const val AAC_OBJECT_TYPE_INDICATION = 0x40
    private const val AUDIO_STREAM_TYPE_BYTE = 0x15
    private const val AAC_BITRATE_BPS = 128_000L
    private val AUDIO_SPECIFIC_CONFIG = byteArrayOf(0x12, 0x10)
    private const val TRACK_ENABLED_IN_MOVIE_IN_PREVIEW_FLAGS = 7L
    private const val FULL_VOLUME_8_8_FIXED = 0x0100
    private const val UNITY_RATE_16_16_FIXED = 0x00010000L
    private const val LANGUAGE_UND = 0x55C4
    private const val SAMPLES_PER_AAC_FRAME = 1024L
    private const val NEXT_TRACK_ID = 2L
    private const val TRACK_ID = 1L
    private const val ES_ID = 0
    private const val SL_CONFIG_PREDEFINED_MP4 = 0x02

    fun bytes(): ByteArray {
        val ftyp = ftypBox()
        val moovAtPlaceholderOffset = moovBox(chunkOffset = 0)
        val mdatHeaderSize = 8
        val mdatOffset = ftyp.size + moovAtPlaceholderOffset.size + mdatHeaderSize
        val moov = moovBox(chunkOffset = mdatOffset)
        val mdat = box("mdat", ByteArray(DUMMY_SAMPLE_BYTE_COUNT))
        return ftyp + moov + mdat
    }

    private fun ftypBox(): ByteArray {
        val majorBrand = "M4A ".toByteArray(US_ASCII)
        val minorVersion = uint32(0)
        val compatibleBrands = "M4A ".toByteArray(US_ASCII) +
            "mp42".toByteArray(US_ASCII) +
            "isom".toByteArray(US_ASCII)
        return box("ftyp", majorBrand + minorVersion + compatibleBrands)
    }

    private fun moovBox(chunkOffset: Int): ByteArray {
        val mvhd = mvhdBox()
        val trak = trakBox(chunkOffset)
        return box("moov", mvhd + trak)
    }

    private fun mvhdBox(): ByteArray {
        val payload = uint32(0) +
            uint32(0) +
            uint32(0) +
            uint32(SAMPLE_RATE.toLong()) +
            uint32(SAMPLE_RATE.toLong()) +
            uint32(UNITY_RATE_16_16_FIXED) +
            uint16(FULL_VOLUME_8_8_FIXED) +
            uint16(0) +
            ByteArray(8) +
            identityMatrix() +
            ByteArray(24) +
            uint32(NEXT_TRACK_ID)
        return box("mvhd", payload)
    }

    private fun trakBox(chunkOffset: Int): ByteArray {
        val tkhd = tkhdBox()
        val mdia = mdiaBox(chunkOffset)
        return box("trak", tkhd + mdia)
    }

    private fun tkhdBox(): ByteArray {
        val payload = uint32(TRACK_ENABLED_IN_MOVIE_IN_PREVIEW_FLAGS) +
            uint32(0) +
            uint32(0) +
            uint32(TRACK_ID) +
            uint32(0) +
            uint32(SAMPLE_RATE.toLong()) +
            ByteArray(8) +
            uint16(0) +
            uint16(0) +
            uint16(FULL_VOLUME_8_8_FIXED) +
            uint16(0) +
            identityMatrix() +
            uint32(0) +
            uint32(0)
        return box("tkhd", payload)
    }

    private fun mdiaBox(chunkOffset: Int): ByteArray {
        val mdhd = mdhdBox()
        val hdlr = hdlrBox()
        val minf = minfBox(chunkOffset)
        return box("mdia", mdhd + hdlr + minf)
    }

    private fun mdhdBox(): ByteArray {
        val payload = uint32(0) +
            uint32(0) +
            uint32(0) +
            uint32(SAMPLE_RATE.toLong()) +
            uint32(SAMPLE_RATE.toLong()) +
            uint16(LANGUAGE_UND) +
            uint16(0)
        return box("mdhd", payload)
    }

    private fun hdlrBox(): ByteArray {
        val payload = uint32(0) +
            uint32(0) +
            "soun".toByteArray(US_ASCII) +
            ByteArray(12) +
            byteArrayOf(0)
        return box("hdlr", payload)
    }

    private fun minfBox(chunkOffset: Int): ByteArray {
        val smhd = box("smhd", uint32(0) + uint16(0) + uint16(0))
        val dinf = dinfBox()
        val stbl = stblBox(chunkOffset)
        return box("minf", smhd + dinf + stbl)
    }

    private fun dinfBox(): ByteArray {
        val url = box("url ", uint32(1))
        val dref = box("dref", uint32(0) + uint32(1) + url)
        return box("dinf", dref)
    }

    private fun stblBox(chunkOffset: Int): ByteArray {
        val stsd = stsdBox()
        val stts = box(
            "stts",
            uint32(0) + uint32(1) + uint32(1) + uint32(SAMPLES_PER_AAC_FRAME),
        )
        val stsc = box(
            "stsc",
            uint32(0) + uint32(1) + uint32(1) + uint32(1) + uint32(1),
        )
        val stsz = box(
            "stsz",
            uint32(0) + uint32(DUMMY_SAMPLE_BYTE_COUNT.toLong()) + uint32(1),
        )
        val stco = box("stco", uint32(0) + uint32(1) + uint32(chunkOffset.toLong()))
        return box("stbl", stsd + stts + stsc + stsz + stco)
    }

    private fun stsdBox(): ByteArray {
        val mp4a = mp4aBox()
        return box("stsd", uint32(0) + uint32(1) + mp4a)
    }

    private fun mp4aBox(): ByteArray {
        val fixedAudioSampleEntryFields = ByteArray(6) +
            uint16(1) +
            ByteArray(8) +
            uint16(2) +
            uint16(16) +
            uint16(0) +
            uint16(0) +
            uint32(SAMPLE_RATE.toLong() shl 16)
        return box("mp4a", fixedAudioSampleEntryFields + esdsBox())
    }

    private fun esdsBox(): ByteArray {
        val decoderSpecificInfo = descriptor(0x05, AUDIO_SPECIFIC_CONFIG)
        val decoderConfigContent = byteArrayOf(AAC_OBJECT_TYPE_INDICATION.toByte()) +
            byteArrayOf(AUDIO_STREAM_TYPE_BYTE.toByte()) +
            uint24(0) +
            uint32(AAC_BITRATE_BPS) +
            uint32(AAC_BITRATE_BPS) +
            decoderSpecificInfo
        val decoderConfigDescriptor = descriptor(0x04, decoderConfigContent)
        val slConfigDescriptor = descriptor(0x06, byteArrayOf(SL_CONFIG_PREDEFINED_MP4.toByte()))
        val esDescriptorContent = uint16(ES_ID) +
            byteArrayOf(0) +
            decoderConfigDescriptor +
            slConfigDescriptor
        val esDescriptor = descriptor(0x03, esDescriptorContent)
        return box("esds", uint32(0) + esDescriptor)
    }

    private fun descriptor(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte(), content.size.toByte()) + content

    private fun identityMatrix(): ByteArray {
        val unity = UNITY_RATE_16_16_FIXED
        val fixedPointOne = 0x40000000L
        return uint32(unity) + uint32(0) + uint32(0) +
            uint32(0) + uint32(unity) + uint32(0) +
            uint32(0) + uint32(0) + uint32(fixedPointOne)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        require(type.length == 4) { "Box type must be exactly 4 characters" }
        val out = ByteArrayOutputStream()
        out.write(uint32((payload.size + 8).toLong()))
        out.write(type.toByteArray(US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    private const val BYTE_MASK = 0xFFL
    private const val BITS_PER_BYTE = 8

    private fun uint16(value: Int): ByteArray = byteArrayOf(
        ((value shr BITS_PER_BYTE) and BYTE_MASK.toInt()).toByte(),
        (value and BYTE_MASK.toInt()).toByte(),
    )

    private fun uint24(value: Int): ByteArray = byteArrayOf(
        ((value shr (2 * BITS_PER_BYTE)) and BYTE_MASK.toInt()).toByte(),
        ((value shr BITS_PER_BYTE) and BYTE_MASK.toInt()).toByte(),
        (value and BYTE_MASK.toInt()).toByte(),
    )

    private fun uint32(value: Long): ByteArray {
        val bytes = ByteArray(4)
        for (i in 0 until 4) {
            bytes[i] = ((value shr ((3 - i) * BITS_PER_BYTE)) and BYTE_MASK).toByte()
        }
        return bytes
    }
}
