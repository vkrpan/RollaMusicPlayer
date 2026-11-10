package com.rolla.musicplayer.core.testing

import android.database.sqlite.SQLiteConstraintException
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.entity.CustomTagEntity
import com.rolla.musicplayer.core.database.entity.SongCustomTagCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTagKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [CustomTagDao] backed by [MutableStateFlow]s.
 *
 * Call [seedSongs] to give it the `songs` rows that [CustomTagDao.songsNeedingFileTagRead],
 * [CustomTagDao.applyFileTags] and [CustomTagDao.markFileTagsRead] work on, and read the results back with [song] (the
 * row with its genre and read flag) and [linksOf] (its links).
 *
 * It behaves like the Room DAO where callers can tell: per-kind counters that only rise, a second entry with the same
 * (kind, name key) throwing [SQLiteConstraintException], and a link to a song it does not hold throwing the same
 * (the foreign key). Constructing that exception needs the Android framework, so a JVM test that reaches either throw
 * runs under Robolectric. Unlike Room, a throw inside a default body does not roll back the steps before it.
 */
// Mirrors CustomTagDao's shape one-to-one (which carries the same suppression): the fake implements every primitive so
// the interface's @Transaction default bodies (insertNew, applyFileTags) run unchanged.
@Suppress("TooManyFunctions")
class FakeCustomTagDao : CustomTagDao {

    private val tagsFlow = MutableStateFlow<List<CustomTagEntity>>(emptyList())
    private val countersFlow = MutableStateFlow<Map<CustomTagKind, Long>>(emptyMap())
    private val linksFlow = MutableStateFlow<List<SongCustomTagCrossRef>>(emptyList())
    private val songsFlow = MutableStateFlow<List<SongEntity>>(emptyList())

    /** Adds or replaces `songs` rows by id. */
    fun seedSongs(songs: List<SongEntity>) {
        songsFlow.update { current -> current.filterNot { row -> songs.any { it.id == row.id } } + songs }
    }

    /** The song row as it stands, with its genre and read flag, or null when it was never seeded. */
    fun song(songId: String): SongEntity? = songsFlow.value.firstOrNull { it.id == songId }

    /** The song's links, as a set. */
    fun linksOf(songId: String): Set<SongCustomTagCrossRef> = linksFlow.value.filter { it.songId == songId }.toSet()

    /** The kind's counter as it stands; 0 when it has never issued or seen an ID. */
    fun counter(kind: CustomTagKind): Long = countersFlow.value[kind] ?: 0L

    override fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTagEntity>> =
        tagsFlow.map { tags -> tags.filter { it.kind == kind }.sortedBy { it.id } }

    override suspend fun findByNameKey(kind: CustomTagKind, nameKey: String): CustomTagEntity? =
        tagsFlow.value.firstOrNull { it.kind == kind && it.nameKey == nameKey }

    override suspend fun findById(kind: CustomTagKind, id: Long): CustomTagEntity? =
        tagsFlow.value.firstOrNull { it.kind == kind && it.id == id }

    // insertNew and applyFileTags are NOT overridden: their @Transaction default bodies in CustomTagDao compose the
    // primitives below, so the fake issues and raises IDs exactly as the real DAO does.

    override suspend fun rename(kind: CustomTagKind, id: Long, name: String, nameKey: String): Int {
        val target = findById(kind, id) ?: return 0
        if (tagsFlow.value.any { it.kind == kind && it.nameKey == nameKey && it.id != id }) {
            throw SQLiteConstraintException("UNIQUE constraint failed: custom_tags.kind, custom_tags.name_key")
        }
        tagsFlow.update { current -> current.map { if (it == target) it.copy(name = name, nameKey = nameKey) else it } }
        return 1
    }

    override suspend fun markFileTagsRead(songId: String): Int = updateSong(songId) { it.copy(fileTagsRead = true) }

    override suspend fun songsNeedingFileTagRead(limit: Int): List<SongEntity> =
        songsFlow.value.filterNot { it.fileTagsRead }.sortedByDescending { it.dateModified }.take(limit)

    override fun observeSongCustomTagIds(songId: String): Flow<List<SongCustomTagCrossRef>> =
        // Room orders by the stored kind name ("SUBTAG" before "TAG"), not by the enum's ordinal.
        linksFlow.map { links ->
            links.filter { it.songId == songId }.sortedWith(compareBy({ it.kind.name }, { it.tagId }))
        }

    override suspend fun highestId(kind: CustomTagKind): Long? = countersFlow.value[kind]

    override suspend fun insertTag(tag: CustomTagEntity) {
        if (tagsFlow.value.any { it.kind == tag.kind && (it.id == tag.id || it.nameKey == tag.nameKey) }) {
            throw SQLiteConstraintException("UNIQUE constraint failed: custom_tags")
        }
        tagsFlow.update { it + tag }
    }

    override suspend fun raiseCounter(kind: CustomTagKind, candidate: Long) {
        countersFlow.update { current -> current + (kind to maxOf(current[kind] ?: 0L, candidate)) }
    }

    override suspend fun deleteSongLinks(songId: String) {
        linksFlow.update { current -> current.filterNot { it.songId == songId } }
    }

    override suspend fun insertLinks(links: List<SongCustomTagCrossRef>) {
        if (links.any { link -> song(link.songId) == null }) {
            throw SQLiteConstraintException("FOREIGN KEY constraint failed")
        }
        if (links.any { it in linksFlow.value } || links.toSet().size != links.size) {
            throw SQLiteConstraintException("UNIQUE constraint failed: song_custom_tags")
        }
        linksFlow.update { it + links }
    }

    override suspend fun setGenreAndRead(songId: String, genre: String?) {
        updateSong(songId) { it.copy(genre = genre, fileTagsRead = true) }
    }

    private fun updateSong(songId: String, change: (SongEntity) -> SongEntity): Int {
        if (song(songId) == null) return 0
        songsFlow.update { current -> current.map { if (it.id == songId) change(it) else it } }
        return 1
    }
}
