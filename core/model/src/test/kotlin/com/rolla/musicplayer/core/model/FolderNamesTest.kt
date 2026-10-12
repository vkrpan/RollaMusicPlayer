package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {

    // Mutations: substringBefore('/') → "Music" for "Music/Chill" → RED; substringAfter('/') → "Chill/Live" for
    // "Music/Chill/Live" → RED.
    @Test
    fun nameIsTheLastPathSegment() {
        assertEquals("Chill", folderName("Music/Chill"))
        assertEquals("Live", folderName("Music/Chill/Live"))
        assertEquals("Music", folderName("Music"))
    }

    // Review Focus 1. Mutation: drop the root branch → "" → RED.
    @Test
    fun theRootIsInternalStorage() {
        assertEquals("Internal storage", folderName(""))
    }

    // Review Focus 1. Mutation: drop the root branch in folderDisplayPath → "/Internal storage/" → RED.
    @Test
    fun displayPathIsRootedAtInternalStorage() {
        assertEquals("/Internal storage", folderDisplayPath(""))
        assertEquals("/Internal storage/Music/Chill", folderDisplayPath("Music/Chill"))
    }
}
