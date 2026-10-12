package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.FolderRepository
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [FolderRepository]. [emitFolders] drives [observeFolders]; [emitFolderSongs] drives
 * [observeFolderSongs] for whatever path is asked, and every path asked is recorded in [requestedFolderPaths], so a
 * test can assert which folder a ViewModel opened.
 */
class FakeFolderRepository : FolderRepository {

    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private val folderSongsFlow = MutableStateFlow<List<Song>>(emptyList())

    /** Every `path` passed to [observeFolderSongs], in call order. */
    val requestedFolderPaths = mutableListOf<String>()

    fun emitFolders(folders: List<Folder>) {
        foldersFlow.value = folders
    }

    fun emitFolderSongs(songs: List<Song>) {
        folderSongsFlow.value = songs
    }

    override fun observeFolders(): Flow<List<Folder>> = foldersFlow.asStateFlow()

    override fun observeFolderSongs(path: String): Flow<List<Song>> {
        requestedFolderPaths += path
        return folderSongsFlow.asStateFlow()
    }
}
