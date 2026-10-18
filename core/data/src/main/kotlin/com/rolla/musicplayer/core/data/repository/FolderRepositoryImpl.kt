package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.common.text.libraryNameComparator
import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    @CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { folders -> folders.sortedByLibraryName(folderTieBreak()) { it.name } }
            .flowOn(sortDispatcher)

    override fun observeFolderSongs(path: String): Flow<List<Song>> =
        folderDao.observeSongsInFolder(path)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    private companion object {
        // Ruling 2 of Phase 4b, in the library order (Phase 5 ruling 4). The name decides. Same-name folders fall
        // back to the path in library order, then exactly. A fresh comparator per emission, because a Collator must
        // not be shared between threads.
        fun folderTieBreak(): Comparator<Folder> = libraryNameComparator<Folder> { it.path }.thenBy { it.path }
    }
}
