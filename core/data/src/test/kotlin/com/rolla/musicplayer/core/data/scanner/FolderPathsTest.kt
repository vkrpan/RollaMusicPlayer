package com.rolla.musicplayer.core.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPathsTest {

    // Mutation: drop the trim → "Music/Chill/" → RED.
    @Test
    fun relativePath_trimsTheTrailingSlash() {
        assertEquals("Music/Chill", FolderPaths.fromRelativePath("Music/Chill/"))
        assertEquals("Download", FolderPaths.fromRelativePath("Download/"))
    }

    // Review Focus 1. Mutation: `relativePath ?: "Unknown"` (or `!!`) → RED.
    @Test
    fun relativePath_nullOrEmptyIsTheRoot() {
        assertEquals("", FolderPaths.fromRelativePath(null))
        assertEquals("", FolderPaths.fromRelativePath(""))
        assertEquals("", FolderPaths.fromRelativePath("/"))
    }

    // Mutation: drop the "/+" collapse → "Music//Chill" → RED.
    @Test
    fun relativePath_collapsesRepeatedSlashes() {
        assertEquals("Music/Chill", FolderPaths.fromRelativePath("Music//Chill/"))
    }

    // Real AOSP and Samsung primary-user paths. Mutation: try the generic "/storage/<volume>/" prefix before the
    // emulated one → "0/Music/Chill" → RED.
    @Test
    fun dataPath_stripsThePrimaryVolumeAndFileName() {
        assertEquals("Music/Chill", FolderPaths.fromDataPath("/storage/emulated/0/Music/Chill/song.mp3"))
        assertEquals(
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
            FolderPaths.fromDataPath(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-2024.opus",
            ),
        )
    }

    // Mutation: `emulated/0/` instead of `emulated/\d+/` → the secondary user's path maps to "" → RED.
    @Test
    fun dataPath_handlesSecondaryUsersSdCardsAndLegacyMounts() {
        assertEquals("Download", FolderPaths.fromDataPath("/storage/emulated/10/Download/a.flac"))
        assertEquals("Music", FolderPaths.fromDataPath("/storage/1A2B-3C4D/Music/a.mp3"))
        assertEquals("Music", FolderPaths.fromDataPath("/storage/self/primary/Music/a.mp3"))
        assertEquals("Music", FolderPaths.fromDataPath("/sdcard/Music/a.mp3"))
        assertEquals("Music", FolderPaths.fromDataPath("/mnt/sdcard/Music/a.mp3"))
    }

    // Review Focus 1. Mutation: return the remainder without dropping the file name → "a.mp3" → RED.
    @Test
    fun dataPath_fileAtTheVolumeRootIsTheRoot() {
        assertEquals("", FolderPaths.fromDataPath("/storage/emulated/0/a.mp3"))
    }

    // Mutation: fall back to the raw path when no prefix matches → "data/media/0/Music" → RED.
    @Test
    fun dataPath_nullOrUnknownIsTheRoot() {
        assertEquals("", FolderPaths.fromDataPath(null))
        assertEquals("", FolderPaths.fromDataPath("/data/media/0/Music/a.mp3"))
        assertEquals("", FolderPaths.fromDataPath("a.mp3"))
    }

    // The API boundary is Q (29). Mutation: `sdkInt > Q` → API 29 parses "Music/" as a DATA path → "" → RED.
    @Test
    fun fromColumn_readsRelativePathFromApi29AndDataBelowIt() {
        assertEquals("Music", FolderPaths.fromColumn(29, "Music/"))
        assertEquals("Music", FolderPaths.fromColumn(28, "/storage/emulated/0/Music/a.mp3"))
    }
}
