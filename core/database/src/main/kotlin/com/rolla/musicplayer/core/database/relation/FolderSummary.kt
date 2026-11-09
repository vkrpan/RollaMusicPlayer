package com.rolla.musicplayer.core.database.relation

import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.folderName

/**
 * One row of FolderDao.observeFolders: a folder path (relative to its volume; "" is the root) and how many songs sit
 * directly in it.
 */
data class FolderSummary(
    val path: String,
    val songCount: Int,
)

fun FolderSummary.toDomain(): Folder = Folder(
    path = path,
    name = folderName(path),
    songCount = songCount,
)
