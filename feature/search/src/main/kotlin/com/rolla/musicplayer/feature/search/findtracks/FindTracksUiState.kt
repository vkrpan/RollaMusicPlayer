package com.rolla.musicplayer.feature.search.findtracks

import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerState

/**
 * What the Find tracks screen shows (custom tags phase 4).
 *
 * [title], [artist], [album], [genre] and [year] are the fields as typed; [filter] is derived from them and the
 * filter's tag and subtag IDs ([SongFilter.year] is [year] as a number, null when blank). [tags] and [subTags] are both
 * custom tag lists in library order, for the chips' names; they are empty until the repository answers, and empty
 * when the lists cannot be read. [results] are the songs that match [filter] in library order, null until the first
 * answer, so a cold start never flashes the empty message. [filtersExpanded] shows the fields card. [picker] is the
 * custom tag picker while its sheet is open, null otherwise; its ticks reach [filter] only on Done.
 *
 * [listsUnavailable] is true once the lists cannot be read: [tags] and [subTags] then stay empty, while the filter
 * keeps its IDs. [activeCustomTagFilters] has one entry per tag and subtag ID in [filter], so the active-filter strip
 * can show and remove each one even when its name is unknown.
 */
@Immutable
data class FindTracksUiState(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val tags: List<CustomTag>,
    val subTags: List<CustomTag>,
    val filter: SongFilter,
    val results: List<Song>?,
    val filtersExpanded: Boolean,
    val picker: CustomTagPickerState?,
    val listsUnavailable: Boolean,
    val activeCustomTagFilters: List<ActiveCustomTagFilter>,
)

/**
 * One tag or subtag ID in Find tracks' filter, for the active-filter strip: [name] is the list's name, or null when the
 * lists do not know [id] (before they load, or after they fail). Its ✕ calls `onRemoveCustomTag(kind, id)`.
 */
@Immutable
data class ActiveCustomTagFilter(val kind: CustomTagKind, val id: Long, val name: String?)

/** The text fields of Find tracks' filter, for removing one from the active-filter strip. */
enum class FindTracksField { TITLE, ARTIST, ALBUM, GENRE, YEAR }

/**
 * The strip entries for a filter's [tagIds] and [subTagIds]: tags first, then subtags. Within each kind, the IDs the
 * list ([tags] or [subTags], in library order) knows come first in that order with their names, then the IDs it does
 * not know, ascending, with no name.
 */
internal fun activeCustomTagFilters(
    tagIds: Set<Long>,
    subTagIds: Set<Long>,
    tags: List<CustomTag>,
    subTags: List<CustomTag>,
): List<ActiveCustomTagFilter> =
    activeOfKind(CustomTagKind.TAG, tagIds, tags) + activeOfKind(CustomTagKind.SUBTAG, subTagIds, subTags)

/** [ids] of [kind] as strip entries: those [list] knows in its order and named, then the rest ascending, unnamed. */
private fun activeOfKind(kind: CustomTagKind, ids: Set<Long>, list: List<CustomTag>): List<ActiveCustomTagFilter> {
    val known = list.filter { it.id in ids }.map { ActiveCustomTagFilter(kind, it.id, it.name) }
    val knownIds = known.mapTo(HashSet()) { it.id }
    val unknown = ids.filter { it !in knownIds }.sorted().map { ActiveCustomTagFilter(kind, it, name = null) }
    return known + unknown
}
