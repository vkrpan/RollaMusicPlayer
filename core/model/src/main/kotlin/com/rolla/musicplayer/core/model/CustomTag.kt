package com.rolla.musicplayer.core.model

/**
 * The two lists a custom tag belongs to. A custom tag is a user-defined label, separate from the ID3 fields the tag
 * editor writes; each kind issues its own IDs, so a [TAG] and a [SUBTAG] can share an ID.
 */
enum class CustomTagKind { TAG, SUBTAG }

/**
 * The highest custom tag ID a list issues or accepts from a file. An ID read from a file above it is dropped, so a
 * hand-edited value cannot push a list's counter towards `Long` overflow; it also keeps IDs short and readable in the
 * `ROLLA_TAGS` field.
 */
const val MAX_CUSTOM_TAG_ID: Long = 999_999_999L

/**
 * The longest custom tag name, in code points (an emoji counts once), after trimming. The one limit for every path a
 * name enters by: create, rename and import (the list file's codec and the repository's import check).
 */
const val CUSTOM_TAG_NAME_MAX_LENGTH = 40

/** One user-defined label: its list ([kind]), its ID within that list, and its display name. */
data class CustomTag(
    val kind: CustomTagKind,
    val id: Long,
    val name: String,
)

/**
 * Both custom tag lists as a whole, as an export writes them and an import replaces them (custom tags phase 3):
 * [tags] of [CustomTagKind.TAG], [subTags] of [CustomTagKind.SUBTAG].
 */
data class CustomTagListFile(
    val tags: List<CustomTag>,
    val subTags: List<CustomTag>,
)

/**
 * The custom tags a song carries, as IDs per kind: [tagIds] for [CustomTagKind.TAG], [subTagIds] for
 * [CustomTagKind.SUBTAG]. An ID with no matching [CustomTag] is kept as it is.
 */
data class CustomTagIds(
    val tagIds: Set<Long>,
    val subTagIds: Set<Long>,
) {
    /** True when the song carries no custom tag of either kind. */
    val isEmpty: Boolean
        get() = tagIds.isEmpty() && subTagIds.isEmpty()

    companion object {
        /** No custom tags. */
        val EMPTY = CustomTagIds(emptySet(), emptySet())
    }
}
