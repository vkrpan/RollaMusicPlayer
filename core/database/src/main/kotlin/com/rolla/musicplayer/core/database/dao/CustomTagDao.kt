package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
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
 * The custom tag lists, their ID counters and the song-to-tag links (v6, custom tags phase 1).
 *
 * IDs are issued per kind from `custom_tag_counters` and never reused: [insertNew] issues counter + 1, and
 * [applyFileTags] raises the counter to the highest ID a file carries. The counter only ever rises.
 *
 * The primitives ([highestId], [insertTag], [raiseCounter], [deleteSongLinks], [insertLinks], [setGenreAndRead]) are
 * interface members so the `@Transaction` default bodies can compose them inside one transaction (the same reason as
 * [PlaylistDao]); callers use the other members, except that the repository reads [highestId] to tell a full list
 * (one that has reached [MAX_CUSTOM_TAG_ID]) before it calls [insertNew].
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
     * Adds an entry to [kind]'s list with the next ID (counter + 1) and raises the counter to it. A second entry with
     * the same [nameKey] in the list throws `SQLiteConstraintException` and leaves the counter as it was.
     *
     * @throws IllegalStateException inserting nothing, when the counter has reached [MAX_CUSTOM_TAG_ID]: the next ID
     *   would pass the ceiling (or, for a counter at `Long.MAX_VALUE`, wrap to a negative ID). The repository checks
     *   [highestId] first and reports the full list instead.
     */
    @Transaction
    suspend fun insertNew(kind: CustomTagKind, name: String, nameKey: String): Long {
        val highest = highestId(kind) ?: 0L
        check(highest < MAX_CUSTOM_TAG_ID) { "The $kind list has issued its last ID ($MAX_CUSTOM_TAG_ID)" }
        val id = highest + 1
        insertTag(CustomTagEntity(kind = kind, id = id, name = name, nameKey = nameKey))
        raiseCounter(kind, id)
        return id
    }

    /** Renames an entry; returns the number of rows changed (0 when the entry does not exist). */
    @Query("UPDATE custom_tags SET name = :name, name_key = :nameKey WHERE kind = :kind AND id = :id")
    suspend fun rename(kind: CustomTagKind, id: Long, name: String, nameKey: String): Int

    /**
     * Stores what the file pass read from a song's file: replaces the song's links with [ids], raises each kind's
     * counter to the highest ID the file carries, and sets the song's [genre] and its read flag.
     */
    @Transaction
    suspend fun applyFileTags(songId: String, ids: CustomTagIds, genre: String?) {
        deleteSongLinks(songId)
        insertLinks(
            ids.tagIds.map { SongCustomTagCrossRef(songId, CustomTagKind.TAG, it) } +
                ids.subTagIds.map { SongCustomTagCrossRef(songId, CustomTagKind.SUBTAG, it) },
        )
        ids.tagIds.maxOrNull()?.let { raiseCounter(CustomTagKind.TAG, it) }
        ids.subTagIds.maxOrNull()?.let { raiseCounter(CustomTagKind.SUBTAG, it) }
        setGenreAndRead(songId, genre)
    }

    /** Sets only the song's read flag, keeping its links and genre; returns the number of rows changed. */
    @Query("UPDATE songs SET file_tags_read = 1 WHERE id = :songId")
    suspend fun markFileTagsRead(songId: String): Int

    /** Up to [limit] songs whose file the pass has not read yet, newest file first. */
    @Query("SELECT * FROM songs WHERE file_tags_read = 0 ORDER BY date_modified DESC LIMIT :limit")
    suspend fun songsNeedingFileTagRead(limit: Int): List<SongEntity>

    /** The custom tags a song carries, of both kinds. */
    @Query("SELECT * FROM song_custom_tags WHERE song_id = :songId ORDER BY kind ASC, tag_id ASC")
    fun observeSongCustomTagIds(songId: String): Flow<List<SongCustomTagCrossRef>>

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

    @Insert
    suspend fun insertLinks(links: List<SongCustomTagCrossRef>)

    @Query("UPDATE songs SET genre = :genre, file_tags_read = 1 WHERE id = :songId")
    suspend fun setGenreAndRead(songId: String, genre: String?)
}
