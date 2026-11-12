package com.rolla.musicplayer.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.data.di.CoreDataIoDispatcher
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.entity.CustomTagEntity
import com.rolla.musicplayer.core.model.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
                else -> try {
                    // Null: the list has issued its last ID. insertNew checks inside its transaction, so a writer
                    // that takes the last ID meanwhile cannot make this throw.
                    customTagDao.insertNew(kind, checked.name, checked.key)
                        ?.let { id -> CustomTagNameResult.Saved(CustomTag(kind, id, checked.name)) }
                        ?: CustomTagNameResult.LimitReached
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

    override suspend fun delete(kind: CustomTagKind, id: Long): Unit = withContext(ioDispatcher) {
        customTagDao.deleteCustomTag(kind, id)
    }

    override suspend fun songIdsCarrying(kind: CustomTagKind, id: Long): List<String> = withContext(ioDispatcher) {
        customTagDao.songIdsCarrying(kind, id)
    }

    override fun observeLinkCounts(kind: CustomTagKind): Flow<Map<Long, Int>> =
        customTagDao.observeLinkCounts(kind).distinctUntilChanged()

    override suspend fun exportLists(): CustomTagListFile = withContext(ioDispatcher) {
        CustomTagListFile(tags = entriesById(CustomTagKind.TAG), subTags = entriesById(CustomTagKind.SUBTAG))
    }

    override suspend fun previewImport(file: CustomTagListFile): List<ImportClash> = withContext(ioDispatcher) {
        clashes(CustomTagKind.TAG, file.tags) + clashes(CustomTagKind.SUBTAG, file.subTags)
    }

    override suspend fun applyImport(file: CustomTagListFile): Unit = withContext(ioDispatcher) {
        // Both lists are checked before the DAO is touched, so a refused file changes nothing.
        val tags = importedEntities(CustomTagKind.TAG, file.tags)
        val subTags = importedEntities(CustomTagKind.SUBTAG, file.subTags)
        customTagDao.replaceAll(tags = tags, subTags = subTags)
    }

    override suspend fun reserveIds(ids: CustomTagIds): Unit = withContext(ioDispatcher) {
        ids.tagIds.highestValidId()?.let { customTagDao.raiseCounter(CustomTagKind.TAG, it) }
        ids.subTagIds.highestValidId()?.let { customTagDao.raiseCounter(CustomTagKind.SUBTAG, it) }
    }

    private suspend fun entriesById(kind: CustomTagKind): List<CustomTag> =
        customTagDao.observeCustomTags(kind).first().map { CustomTag(kind = it.kind, id = it.id, name = it.name) }

    /** [kind]'s carried, named IDs whose name [imported] changes or drops, by ID. */
    private suspend fun clashes(kind: CustomTagKind, imported: List<CustomTag>): List<ImportClash> {
        val counts = customTagDao.linkCounts(kind)
        val importedNames = imported.associate { it.id to it.name }
        return customTagDao.observeCustomTags(kind).first()
            .mapNotNull { entry ->
                val songCount = counts[entry.id] ?: 0
                val importedName = importedNames[entry.id]
                if (songCount > 0 && importedName != entry.name) {
                    ImportClash(kind, entry.id, entry.name, importedName, songCount)
                } else {
                    null
                }
            }
            .sortedBy { it.id }
    }
}

/** A name as the rules leave it: trimmed and keyed, or refused with the result [CustomTagRepository] reports. */
private sealed interface CheckedName {
    data class Valid(val name: String, val key: String) : CheckedName
    data class Rejected(val result: CustomTagNameResult) : CheckedName
}

/** The one set of name rules for create, rename and import: trimmed, 1 to [CUSTOM_TAG_NAME_MAX_LENGTH] code points. */
private fun checkName(name: String): CheckedName {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> CheckedName.Rejected(CustomTagNameResult.Blank)
        trimmed.codePointCount(0, trimmed.length) > CUSTOM_TAG_NAME_MAX_LENGTH ->
            CheckedName.Rejected(CustomTagNameResult.TooLong)
        else -> CheckedName.Valid(name = trimmed, key = trimmed.lowercase(Locale.ROOT))
    }
}

/**
 * [entries] as [kind] rows for an import, each name trimmed and keyed by [checkName].
 *
 * @throws IllegalArgumentException when a name breaks the name rules, an ID is outside 1..[MAX_CUSTOM_TAG_ID], or two
 *   entries share an ID or a name key.
 */
private fun importedEntities(kind: CustomTagKind, entries: List<CustomTag>): List<CustomTagEntity> {
    val rows = entries.map { entry ->
        require(entry.id in 1..MAX_CUSTOM_TAG_ID) { "$kind ${entry.id} is outside 1..$MAX_CUSTOM_TAG_ID" }
        val checked = checkName(entry.name)
        require(checked is CheckedName.Valid) { "$kind ${entry.id} has an invalid name: $checked" }
        CustomTagEntity(kind = kind, id = entry.id, name = checked.name, nameKey = checked.key)
    }
    require(rows.distinctBy { it.id }.size == rows.size) { "Two $kind entries share an ID" }
    require(rows.distinctBy { it.nameKey }.size == rows.size) { "Two $kind entries share a name ignoring case" }
    return rows
}

/** The highest ID a list can hold, ignoring any outside 1..[MAX_CUSTOM_TAG_ID]; null when none is left. */
private fun Set<Long>.highestValidId(): Long? = filter { it in 1..MAX_CUSTOM_TAG_ID }.maxOrNull()
