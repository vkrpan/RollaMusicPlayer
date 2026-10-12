package com.rolla.musicplayer.core.model

/**
 * A folder that directly holds songs (spec §9.4). [path] is relative to its storage volume ("Music/Chill"; "" is the
 * root) and is the folder's identity. [name] is its display name (see [folderName]), and [artworkUri] is the cover of
 * its alphabetically first song.
 */
data class Folder(
    val path: String,
    val name: String,
    val songCount: Int,
    val artworkUri: String,
)
