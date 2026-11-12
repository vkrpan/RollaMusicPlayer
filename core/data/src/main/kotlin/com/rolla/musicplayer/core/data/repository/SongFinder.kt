package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.common.text.sortedByLibraryName
import com.rolla.musicplayer.core.data.di.CoreDataDefaultDispatcher
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.SongCustomTagCrossRef
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.model.artistDisplayName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Find tracks' search (custom tags phase 4): the library filtered by a [SongFilter], in memory. */
interface SongFinder {

    /**
     * The songs that match each [SongFilter] [filter] emits, in library order (the Tracks tab's A–Z). Emits again
     * whenever the filter, the library or the song-to-tag links change. An empty filter matches the whole library.
     */
    fun observeMatches(filter: Flow<SongFilter>): Flow<List<Song>>
}

/**
 * [SongFinder] over the whole library ([SongDao.observeAllSongs], whose rows carry the genre) and every song's custom
 * tag links ([CustomTagDao.observeAllSongCustomTagLinks]). The library is sorted once per change of the songs, in the
 * same library order as [SongRepositoryImpl.observeSongs], and filtered with [matches] on [sortDispatcher].
 */
class SongFinderImpl @Inject constructor(
    private val songDao: SongDao,
    private val customTagDao: CustomTagDao,
    @CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher,
) : SongFinder {

    override fun observeMatches(filter: Flow<SongFilter>): Flow<List<Song>> {
        val library = songDao.observeAllSongs()
            .map { entities -> entities.map { FinderEntry(it.toDomain(), it.genre) } }
            .distinctUntilChanged()
            .map { entries -> entries.sortedByLibraryName(EntryTieBreak) { it.song.title } }
        val links = customTagDao.observeAllSongCustomTagLinks()
            .map { it.idsBySong() }
            .distinctUntilChanged()
        return combine(library, links, filter.distinctUntilChanged()) { entries, idsBySong, songFilter ->
            entries
                .filter { entry ->
                    val ids = idsBySong[entry.song.id] ?: CustomTagIds.EMPTY
                    songFilter.matches(entry.song, entry.genre, ids.tagIds, ids.subTagIds)
                }
                .map { it.song }
        }
            .distinctUntilChanged()
            .flowOn(sortDispatcher)
    }

    /** A song with the genre its row carries (the [Song] model has none). */
    private data class FinderEntry(val song: Song, val genre: String?)

    private companion object {
        // As SongRepositoryImpl (its ruling 4): an exact-title tie goes to the id, so equal titles never swap.
        val EntryTieBreak: Comparator<FinderEntry> = compareBy<FinderEntry> { it.song.title }.thenBy { it.song.id }
    }
}

/**
 * True when [song] passes every active field of this filter. Text fields are trimmed, and a blank one is inactive; an
 * active one matches any part of the value, ignoring case: [SongFilter.title] the title, [SongFilter.album] the album,
 * [SongFilter.artist] the raw artist or its [artistDisplayName] (so "unknown" finds a blank artist), and
 * [SongFilter.genre] the [genre], which never matches when null. [SongFilter.year] matches the song's year exactly.
 * Every [SongFilter.tagIds] must be among [tagIds] and every [SongFilter.subTagIds] among [subTagIds]; the kinds are
 * separate, so a subtag never satisfies a tag with the same ID.
 */
internal fun SongFilter.matches(song: Song, genre: String?, tagIds: Set<Long>, subTagIds: Set<Long>): Boolean =
    textMatches(title, song.title) &&
        textMatches(artist, song.artist, artistDisplayName(song.artist)) &&
        textMatches(album, song.album) &&
        textMatches(this.genre, genre) &&
        (year == null || song.year == year) &&
        tagIds.containsAll(this.tagIds) &&
        subTagIds.containsAll(this.subTagIds)

/** True when [filterText] is blank after trimming, or when any non-null [values] contains it, ignoring case. */
private fun textMatches(filterText: String, vararg values: String?): Boolean {
    val needle = filterText.trim()
    return needle.isEmpty() || values.any { it != null && it.contains(needle, ignoreCase = true) }
}

/** Each song's links as IDs per kind, keyed by song ID. */
private fun List<SongCustomTagCrossRef>.idsBySong(): Map<String, CustomTagIds> =
    groupBy { it.songId }.mapValues { (_, links) ->
        CustomTagIds(
            tagIds = links.filter { it.kind == CustomTagKind.TAG }.mapTo(mutableSetOf()) { it.tagId },
            subTagIds = links.filter { it.kind == CustomTagKind.SUBTAG }.mapTo(mutableSetOf()) { it.tagId },
        )
    }
