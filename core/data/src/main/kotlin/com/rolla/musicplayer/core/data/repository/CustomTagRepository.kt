package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.coroutines.flow.Flow

/** The outcome of [CustomTagRepository.create] and [CustomTagRepository.rename]. */
sealed interface CustomTagNameResult {
    /** The entry as stored, with its trimmed name. */
    data class Saved(val tag: CustomTag) : CustomTagNameResult

    /** The name is empty after trimming. */
    data object Blank : CustomTagNameResult

    /** The trimmed name is longer than [CUSTOM_TAG_NAME_MAX_LENGTH] code points. */
    data object TooLong : CustomTagNameResult

    /** Another entry in the same list has this name, ignoring case and surrounding spaces. */
    data object Duplicate : CustomTagNameResult

    /**
     * [CustomTagRepository.rename] found no entry with that ID. The name is checked first, so an invalid name for a
     * missing ID reads as [Blank] or [TooLong], not [NotFound].
     */
    data object NotFound : CustomTagNameResult

    /**
     * [CustomTagRepository.create] saved nothing: the list has issued [MAX_CUSTOM_TAG_ID], or a file carried it, and
     * IDs are never reused, so the list can take no new entry.
     */
    data object LimitReached : CustomTagNameResult
}

/**
 * The custom tag lists (one per [CustomTagKind]) and the custom tags each song carries. A name is trimmed before it is
 * stored, and two entries of one list cannot share a name ignoring case; the two lists are independent.
 */
interface CustomTagRepository {

    /** One list's entries in library order. */
    fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTag>>

    /**
     * Adds an entry to [kind]'s list under the next ID, which is above every ID the list or a file has used. The name
     * is checked first ([CustomTagNameResult.Blank], [CustomTagNameResult.TooLong], [CustomTagNameResult.Duplicate]);
     * a list whose next ID would pass [MAX_CUSTOM_TAG_ID] returns [CustomTagNameResult.LimitReached].
     */
    suspend fun create(kind: CustomTagKind, name: String): CustomTagNameResult

    /**
     * Renames an entry; renaming an entry to its own name in another case is allowed. The name is validated before
     * the ID is looked up, so a blank or too-long name is rejected as such even when no entry has [id].
     */
    suspend fun rename(kind: CustomTagKind, id: Long, name: String): CustomTagNameResult

    /** The IDs of the custom tags a song carries, per kind, including IDs with no entry in their list. */
    fun observeSongCustomTagIds(songId: String): Flow<CustomTagIds>

    /**
     * Deletes [kind]'s entry [id] and every song's link to it. The ID is never issued again. The songs' files are not
     * touched here; the caller rewrites them first ([songIdsCarrying] names them).
     */
    suspend fun delete(kind: CustomTagKind, id: Long)

    /** The IDs of the songs that carry [kind]'s [id], whether or not the list has an entry for it. */
    suspend fun songIdsCarrying(kind: CustomTagKind, id: Long): List<String>

    /**
     * For each of [kind]'s IDs that songs carry, known to the list or not, the number of songs carrying it; an ID no
     * song carries is absent. Drives Manage tags' row subtitles and the delete confirmation's count.
     */
    fun observeLinkCounts(kind: CustomTagKind): Flow<Map<Long, Int>>

    /** Both lists as they stand, each in ascending ID order: what an export writes. */
    suspend fun exportLists(): CustomTagListFile

    /**
     * What importing [file] would do to the tags songs carry: one [ImportClash] for each ID that at least one song
     * carries, that has an entry here, and whose name in [file] differs (an exact comparison) or is absent from it
     * ([ImportClash.importedName] null). Tags first, then subtags, each by ID. Writes nothing.
     */
    suspend fun previewImport(file: CustomTagListFile): List<ImportClash>

    /**
     * Replaces both lists with [file]'s (names trimmed). Song links are kept, so a carried ID [file] lacks becomes
     * unknown and a carried unknown ID [file] names becomes known. Each list's counter rises to [file]'s highest ID of
     * that kind and never falls.
     *
     * [file] is checked whole before anything is written, whatever built it (the list file's codec applies the same
     * rules, but a file made in code is not trusted): every name by [create]'s rules (trimmed, 1 to
     * [CUSTOM_TAG_NAME_MAX_LENGTH] code points), every ID within 1..[MAX_CUSTOM_TAG_ID], and per kind no two entries
     * sharing an ID or a name ignoring case (the two lists may share both).
     *
     * @throws IllegalArgumentException writing nothing, when [file] breaks any of those rules.
     */
    suspend fun applyImport(file: CustomTagListFile)

    /**
     * Raises each kind's counter to the highest of [ids] of that kind, so [create] issues above them: an editor that
     * opens a file the scan has not read yet calls this with the file's IDs. IDs outside 1..[MAX_CUSTOM_TAG_ID] are
     * ignored. Never lowers a counter.
     */
    suspend fun reserveIds(ids: CustomTagIds)
}

/**
 * One line of the import warning: songs carry [kind]'s [id], here named [currentName], and the imported file renames it
 * to [importedName] or, when [importedName] is null, lacks it. [songCount] is the number of songs carrying it.
 */
data class ImportClash(
    val kind: CustomTagKind,
    val id: Long,
    val currentName: String,
    val importedName: String?,
    val songCount: Int,
)
