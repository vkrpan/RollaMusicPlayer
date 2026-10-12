package com.rolla.musicplayer.core.data.scanner

import android.os.Build

/**
 * Turns MediaStore's location columns into the `songs.folder_path` value (spec §9.4): the song's folder relative to
 * its storage volume, "/"-separated, with no leading or trailing slash. "" is the volume root.
 *
 * API 29+ reports RELATIVE_PATH ("Music/Chill/"); API 24–28 only reports the absolute DATA path
 * ("/storage/emulated/0/Music/Chill/song.mp3"). Volumes are not told apart (spec §3): the same relative path on two
 * volumes is one folder.
 */
internal object FolderPaths {

    // Volume roots as they start a DATA path, most specific first: "/storage/emulated/<user>/" and
    // "/storage/self/primary/" must be tried before the generic "/storage/<volume>/", which would otherwise take
    // "emulated" or "self" as the volume and leave "0/…" or "primary/…".
    private val volumePrefixes = listOf(
        Regex("^/storage/emulated/\\d+/"),
        Regex("^/storage/self/primary/"),
        Regex("^/storage/[^/]+/"),
        Regex("^(/mnt)?/sdcard/"),
    )

    private val repeatedSlashes = Regex("/+")

    /** RELATIVE_PATH ("Music/Chill/") to a folder path ("Music/Chill"). Null or blank is the volume root. */
    fun fromRelativePath(relativePath: String?): String = normalize(relativePath.orEmpty())

    /**
     * DATA ("/storage/emulated/0/Music/Chill/song.mp3") to a folder path ("Music/Chill"): strips the volume prefix and
     * the file name. Null, or a path with no known volume prefix, is the volume root.
     */
    @Suppress("ReturnCount") // Two guard clauses (null, no known volume) read clearer than a nested let chain.
    fun fromDataPath(dataPath: String?): String {
        if (dataPath == null) return ""
        val prefix = volumePrefixes.firstNotNullOfOrNull { it.find(dataPath) } ?: return ""
        val withinVolume = dataPath.substring(prefix.range.last + 1)
        return normalize(withinVolume.substringBeforeLast('/', missingDelimiterValue = ""))
    }

    /** The folder path of one MediaStore row. [value] is RELATIVE_PATH on API 29+ and DATA below it. */
    fun fromColumn(sdkInt: Int, value: String?): String =
        if (sdkInt >= Build.VERSION_CODES.Q) fromRelativePath(value) else fromDataPath(value)

    private fun normalize(path: String): String = path.replace(repeatedSlashes, "/").trim('/')
}
