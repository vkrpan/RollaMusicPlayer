package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import com.rolla.musicplayer.core.database.entity.CustomTagEntity
import com.rolla.musicplayer.core.database.entity.SongCustomTagCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.coroutines.flow.Flow

/**
 * The custom tag lists, their ID counters and the song-to-tag links (v6, custom tags phase 1), and the file pass's
 * writes guarded by each song row's [SongEntity.fileTagsGeneration] (v7, custom tags phase 2).
 *
 * IDs are issued per kind from `custom_tag_counters` and never reused: [insertNew] issues counter + 1, and
 * [applyFileTags] and [replaceAll] raise the counter to the highest ID a file or an imported list carries. The counter
 * only ever rises; [deleteCustomTag] leaves it as it is.
 *
 * The primitives ([highestId], [insertTag], [raiseCounter], [deleteSongLinks], [deleteTagRow], [deleteLinksTo],
 * [deleteAllTags], [insertLinks], [setGenreAndRead], [setFileTagsRead], [fileTagsGeneration]) are interface members so
 * the `@Transaction` default bodies can compose them inside one transaction (the same reason as [PlaylistDao]);
 * callers use the other members, except that the repository calls [raiseCounter] to reserve the IDs a file carries
 * before the scan has read it (custom tags phase 3).
 *
 * The file pass's two writes, [applyFileTags] and [markFileTagsRead], are conditional on the row's
 * [SongEntity.fileTagsGeneration] (v7, custom tags phase 2): a read of a file the scanner reset meanwhile is dropped.
 */
@Suppress("TooManyFunctions")
@Dao
interface CustomTagDao {

    /** One list's entries, by ID. The repository puts them in library order. */
    @Query("SELECT * FROM custom_tags WHERE kind = :kind ORDER BY id ASC")
    fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTagEntity>>

    @Query("SELECT * FROM custom_tags WHERE kind = :kind AND name_key = :nameKey")
    suspend fun findByNameKey(kind: CustomTagKind, nameKey: String): CustomTagEntity?

    @Query("SELECT * FROM custom_tags WHERE kind = :kind AND id = :id")
    suspend fun findById(kind: CustomTagKind, id: Long): CustomTagEntity?

    /**
     * Adds an entry to [kind]'s list with the next ID (counter + 1), raises the counter to it and returns it. A second
     * entry with the same [nameKey] in the list throws `SQLiteConstraintException` and leaves the counter as it was.
     *
     * Returns null, inserting nothing, when the counter has reached [MAX_CUSTOM_TAG_ID]: the next ID would pass the
     * ceiling (or, for a counter at `Long.MAX_VALUE`, wrap to a negative ID). The check runs inside the transaction,
     * so a writer that takes the last ID between a caller's own look at the counter and this call cannot make it throw
     * (custom tags phase 3; phase 1 threw `IllegalStateException`).
     */
    @Transaction
    suspend fun insertNew(kind: CustomTagKind, name: String, nameKey: String): Long? {
        val highest = highestId(kind) ?: 0L
        if (highest >= MAX_CUSTOM_TAG_ID) return null
        val id = highest + 1
        insertTag(CustomTagEntity(kind = kind, id = id, name = name, nameKey = nameKey))
        raiseCounter(kind, id)
        return id
    }

    /**
     * Deletes [kind]'s entry [id] and every song link to it, in one transaction. The counter is left as it is, so the
     * ID is never issued again. Deleting an ID with no entry still removes its links.
     */
    @Transaction
    suspend fun deleteCustomTag(kind: CustomTagKind, id: Long) {
        deleteTagRow(kind, id)
        deleteLinksTo(kind, id)
    }

    /** The songs that carry [kind]'s [id], whether or not the list has an entry for it, by song ID. */
    @Query("SELECT song_id FROM song_custom_tags WHERE kind = :kind AND tag_id = :id ORDER BY song_id ASC")
    suspend fun songIdsCarrying(kind: CustomTagKind, id: Long): List<String>

    /** For each of [kind]'s IDs that songs carry, known to the list or not, the number of songs carrying it. */
    @Query("SELECT tag_id, COUNT(*) AS song_count FROM song_custom_tags WHERE kind = :kind GROUP BY tag_id")
    suspend fun linkCounts(
        kind: CustomTagKind,
    ): Map<
        @MapColumn(columnName = "tag_id")
        Long,
        @MapColumn(columnName = "song_count")
        Int,
        >

    /** [linkCounts] as a stream that emits again whenever the links change. */
    @Query("SELECT tag_id, COUNT(*) AS song_count FROM song_custom_tags WHERE kind = :kind GROUP BY tag_id")
    fun observeLinkCounts(
        kind: CustomTagKind,
    ): Flow<
        Map<
            @MapColumn(columnName = "tag_id")
            Long,
            @MapColumn(columnName = "song_count")
            Int,
            >,
        >

    /**
     * Replaces both lists' entries with [tags] and [subTags] (an import), in one transaction. Song links are kept as
     * they are: an ID the new lists lack becomes unknown, and an unknown ID they name becomes known. Each kind's
     * counter is raised to the highest ID its new list holds, never lowered, so no ID a file or the old list used is
     * reissued. The entries must be of their list's kind and unique per kind by ID and name key; a clash throws
     * `SQLiteConstraintException` and rolls the whole import back.
     */
    @Transaction
    suspend fun replaceAll(tags: List<CustomTagEntity>, subTags: List<CustomTagEntity>) {
        require(tags.all { it.kind == CustomTagKind.TAG }) { "tags must all be TAG entries" }
        require(subTags.all { it.kind == CustomTagKind.SUBTAG }) { "subTags must all be SUBTAG entries" }
        deleteAllTags()
        (tags + subTags).forEach { insertTag(it) }
        tags.maxOfOrNull { it.id }?.let { raiseCounter(CustomTagKind.TAG, it) }
        subTags.maxOfOrNull { it.id }?.let { raiseCounter(CustomTagKind.SUBTAG, it) }
    }

    /** Renames an entry; returns the number of rows changed (0 when the entry does not exist). */
    @Query("UPDATE custom_tags SET name = :name, name_key = :nameKey WHERE kind = :kind AND id = :id")
    suspend fun rename(kind: CustomTagKind, id: Long, name: String, nameKey: String): Int

    /**
     * Stores what the file pass read from a song's file: replaces the song's links with [ids], raises each kind's
     * counter to the highest ID the file carries, and sets the song's [genre] and its read flag.
     *
     * Only while the row still holds [expectedGeneration], the [SongEntity.fileTagsGeneration] the pass queried it at:
     * the scanner bumps it when it resets the row for a changed or saved file, and the pass's read is then of the old
     * file. Returns true when it applied; false, writing nothing, when the generation changed or the row is gone.
     *
     * The `@Transaction` is load-bearing: the generation check and the writes must not interleave with the scanner's
     * upsert of the same row, or a reset that lands between them would be overwritten by the stale read.
     */
    @Transaction
    suspend fun applyFileTags(songId: String, ids: CustomTagIds, genre: String?, expectedGeneration: Long): Boolean {
        if (fileTagsGeneration(songId) != expectedGeneration) return false
        deleteSongLinks(songId)
        insertLinks(
            ids.tagIds.map { SongCustomTagCrossRef(songId, CustomTagKind.TAG, it) } +
                ids.subTagIds.map { SongCustomTagCrossRef(songId, CustomTagKind.SUBTAG, it) },
        )
        ids.tagIds.maxOrNull()?.let { raiseCounter(CustomTagKind.TAG, it) }
        ids.subTagIds.maxOrNull()?.let { raiseCounter(CustomTagKind.SUBTAG, it) }
        setGenreAndRead(songId, genre)
        return true
    }

    /**
     * Sets only the song's read flag, keeping its links and genre, under the same generation check as
     * [applyFileTags]. Returns true when it set the flag; false, writing nothing, when the generation changed or the
     * row is gone.
     *
     * The `@Transaction` is load-bearing, as for [applyFileTags]: the generation check and the write must not
     * interleave with the scanner's upsert of the same row.
     */
    @Transaction
    suspend fun markFileTagsRead(songId: String, expectedGeneration: Long): Boolean {
        if (fileTagsGeneration(songId) != expectedGeneration) return false
        setFileTagsRead(songId)
        return true
    }

    /** Up to [limit] songs whose file the pass has not read yet, newest file first. */
    @Query("SELECT * FROM songs WHERE file_tags_read = 0 ORDER BY date_modified DESC LIMIT :limit")
    suspend fun songsNeedingFileTagRead(limit: Int): List<SongEntity>

    /** The custom tags a song carries, of both kinds. */
    @Query("SELECT * FROM song_custom_tags WHERE song_id = :songId ORDER BY kind ASC, tag_id ASC")
    fun observeSongCustomTagIds(songId: String): Flow<List<SongCustomTagCrossRef>>

    /**
     * Every song's custom tag links, of both kinds, including IDs with no entry in their list (no join to the lists).
     * Find tracks and the chips read the whole library's links from it (custom tags phase 4).
     */
    @Query("SELECT * FROM song_custom_tags")
    fun observeAllSongCustomTagLinks(): Flow<List<SongCustomTagCrossRef>>

    /** The kind's counter, or null when it has never issued or seen an ID. */
    @Query("SELECT highest_id FROM custom_tag_counters WHERE kind = :kind")
    suspend fun highestId(kind: CustomTagKind): Long?

    @Insert
    suspend fun insertTag(tag: CustomTagEntity)

    /**
     * Raises the kind's counter to [candidate], never lowering it; an absent counter reads as 0. One statement
     * rather than `INSERT ... ON CONFLICT DO UPDATE`, which needs SQLite 3.24 (API 30) and minSdk is 24.
     */
    @Query(
        """
        INSERT OR REPLACE INTO custom_tag_counters (kind, highest_id)
        VALUES (:kind, MAX(:candidate, COALESCE((SELECT highest_id FROM custom_tag_counters WHERE kind = :kind), 0)))
        """,
    )
    suspend fun raiseCounter(kind: CustomTagKind, candidate: Long)

    @Query("DELETE FROM song_custom_tags WHERE song_id = :songId")
    suspend fun deleteSongLinks(songId: String)

    @Query("DELETE FROM custom_tags WHERE kind = :kind AND id = :id")
    suspend fun deleteTagRow(kind: CustomTagKind, id: Long)

    @Query("DELETE FROM song_custom_tags WHERE kind = :kind AND tag_id = :id")
    suspend fun deleteLinksTo(kind: CustomTagKind, id: Long)

    @Query("DELETE FROM custom_tags")
    suspend fun deleteAllTags()

    @Insert
    suspend fun insertLinks(links: List<SongCustomTagCrossRef>)

    @Query("UPDATE songs SET genre = :genre, file_tags_read = 1 WHERE id = :songId")
    suspend fun setGenreAndRead(songId: String, genre: String?)

    @Query("UPDATE songs SET file_tags_read = 1 WHERE id = :songId")
    suspend fun setFileTagsRead(songId: String)

    /** The song's [SongEntity.fileTagsGeneration], or null when the row does not exist. */
    @Query("SELECT file_tags_generation FROM songs WHERE id = :songId")
    suspend fun fileTagsGeneration(songId: String): Long?
}
