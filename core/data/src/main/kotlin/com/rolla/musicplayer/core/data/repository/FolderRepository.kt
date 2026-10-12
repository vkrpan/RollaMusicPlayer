package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/** Local, offline folder data (the Folders tab and FolderDetail), derived from the songs' scanned folder paths. */
interface FolderRepository {

    /** Every folder that directly holds songs, by display name case-insensitively, then by path (ruling 2). */
    fun observeFolders(): Flow<List<Folder>>

    /** The songs directly in [path] (not its subfolders), by title case-insensitively. */
    fun observeFolderSongs(path: String): Flow<List<Song>>
}
