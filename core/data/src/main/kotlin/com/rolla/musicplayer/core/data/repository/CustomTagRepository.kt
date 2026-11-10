package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.coroutines.flow.Flow

/** The longest custom tag name, in code points (an emoji counts once). */
const val CUSTOM_TAG_NAME_MAX_LENGTH = 40

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
}
