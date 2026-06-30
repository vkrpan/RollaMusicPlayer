package com.rolla.musicplayer.core.data.scanner

import com.rolla.musicplayer.core.database.dao.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryIndexer @Inject constructor(
    private val scanner: MediaScanner,
    private val songDao: SongDao,
) {
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val scanned = scanner.scan()
        val scannedById = scanned.associateBy { it.mediaStoreId }
        val existing = songDao.getAllSongs().associateBy { it.mediaStoreId }

        val toUpsert = scanned
            .filter { song ->
                val existingEntity = existing[song.mediaStoreId]
                existingEntity == null || existingEntity.dateModified != song.dateModified
            }
            .map { it.toEntity() }

        val toDeleteIds = (existing.keys - scannedById.keys).toList()

        if (toUpsert.isNotEmpty()) songDao.upsertSongs(toUpsert)
        if (toDeleteIds.isNotEmpty()) songDao.deleteByMediaStoreIds(toDeleteIds)

        SyncResult(added = toUpsert.size, removed = toDeleteIds.size)
    }
}
