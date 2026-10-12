package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders()
            .map { rows -> rows.map { it.toDomain() }.sortedWith(folderOrder) }
            .distinctUntilChanged()

    override fun observeFolderSongs(path: String): Flow<List<Song>> =
        folderDao.observeSongsInFolder(path)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    private companion object {
        // Ruling 2: the Folders tab sorts by what it shows, the name, like every other "Name" list, so Phase 5's A–Z
        // sections stay monotonic. Same-name folders fall back to the path, case-insensitively and then exactly.
        val folderOrder: Comparator<Folder> =
            compareBy<Folder, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }
                .thenBy { it.path }
    }
}
