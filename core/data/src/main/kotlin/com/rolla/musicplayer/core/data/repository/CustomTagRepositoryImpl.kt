package com.rolla.musicplayer.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.data.di.CoreDataIoDispatcher
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * [CustomTagRepository] over [CustomTagDao]. The duplicate check compares name keys (the trimmed name in root-locale
 * lower case) within one list. The DAO's unique index on (kind, name key) is the last line of defence: a write that
 * loses a race to another writer with the same name throws [SQLiteConstraintException], which reads as
 * [CustomTagNameResult.Duplicate]. The lists sort in the library order on [sortDispatcher], as the playlists do.
 */
class CustomTagRepositoryImpl @Inject constructor(
    private val customTagDao: CustomTagDao,
    @CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher,
    @CoreDataIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CustomTagRepository {

    override fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTag>> =
        customTagDao.observeCustomTags(kind)
            .map { entities -> entities.map { CustomTag(kind = it.kind, id = it.id, name = it.name) } }
            .distinctUntilChanged()
            .map { tags -> tags.sortedByLibraryName(compareBy<CustomTag> { it.name }.thenBy { it.id }) { it.name } }
            .flowOn(sortDispatcher)

    override suspend fun create(kind: CustomTagKind, name: String): CustomTagNameResult = withContext(ioDispatcher) {
        when (val checked = checkName(name)) {
            is CheckedName.Rejected -> checked.result
            is CheckedName.Valid -> when {
                customTagDao.findByNameKey(kind, checked.key) != null -> CustomTagNameResult.Duplicate
                // The next ID would pass the ceiling; insertNew's own guard would throw.
                (customTagDao.highestId(kind) ?: 0L) >= MAX_CUSTOM_TAG_ID -> CustomTagNameResult.LimitReached
                else -> try {
                    val id = customTagDao.insertNew(kind, checked.name, checked.key)
                    CustomTagNameResult.Saved(CustomTag(kind, id, checked.name))
                } catch (_: SQLiteConstraintException) {
                    CustomTagNameResult.Duplicate
                }
            }
        }
    }

    override suspend fun rename(kind: CustomTagKind, id: Long, name: String): CustomTagNameResult =
        withContext(ioDispatcher) {
            when (val checked = checkName(name)) {
                is CheckedName.Rejected -> checked.result
                is CheckedName.Valid -> {
                    // The entry itself may hold the key: renaming "techno" to "Techno" changes only the case.
                    val holder = customTagDao.findByNameKey(kind, checked.key)
                    if (holder != null && holder.id != id) {
                        CustomTagNameResult.Duplicate
                    } else {
                        try {
                            if (customTagDao.rename(kind, id, checked.name, checked.key) == 0) {
                                CustomTagNameResult.NotFound
                            } else {
                                CustomTagNameResult.Saved(CustomTag(kind, id, checked.name))
                            }
                        } catch (_: SQLiteConstraintException) {
                            CustomTagNameResult.Duplicate
                        }
                    }
                }
            }
        }

    override fun observeSongCustomTagIds(songId: String): Flow<CustomTagIds> =
        customTagDao.observeSongCustomTagIds(songId)
            .map { links ->
                CustomTagIds(
                    tagIds = links.filter { it.kind == CustomTagKind.TAG }.mapTo(mutableSetOf()) { it.tagId },
                    subTagIds = links.filter { it.kind == CustomTagKind.SUBTAG }.mapTo(mutableSetOf()) { it.tagId },
                )
            }
            .distinctUntilChanged()

    private fun checkName(name: String): CheckedName {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> CheckedName.Rejected(CustomTagNameResult.Blank)
            trimmed.codePointCount(0, trimmed.length) > CUSTOM_TAG_NAME_MAX_LENGTH ->
                CheckedName.Rejected(CustomTagNameResult.TooLong)
            else -> CheckedName.Valid(name = trimmed, key = trimmed.lowercase(Locale.ROOT))
        }
    }

    private sealed interface CheckedName {
        data class Valid(val name: String, val key: String) : CheckedName
        data class Rejected(val result: CustomTagNameResult) : CheckedName
    }
}
