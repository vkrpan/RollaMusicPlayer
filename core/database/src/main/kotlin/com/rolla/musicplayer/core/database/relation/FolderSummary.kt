package com.rolla.musicplayer.core.database.relation

import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.folderName

/**
 * One row of FolderDao.observeFolders: a folder path (relative to its volume; "" is the root), how many songs sit
 * directly in it, and the cover of its alphabetically first song.
 */
data class FolderSummary(
    val path: String,
    val songCount: Int,
    val artworkUri: String,
)

fun FolderSummary.toDomain(): Folder = Folder(
    path = path,
    name = folderName(path),
    songCount = songCount,
    artworkUri = artworkUri,
)
