package com.rolla.musicplayer.feature.tageditor

import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.feature.tageditor.io.MediaScanNotifier
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The one post-save step both tag editors run after every successful write, so the rest of the
 * app stops showing the pre-edit tags. In order:
 *
 * 1. **Notify MediaStore** -- [MediaScanNotifier.awaitScan] on each saved song's resolved file
 *    path (songs whose path can't be resolved are skipped -- see
 *    [SongFileResolver.resolveFilePath] -- their re-sync below still runs against whatever
 *    MediaStore holds). This must complete BEFORE step 2, which reads the tags back out of
 *    MediaStore, not out of the file.
 * 2. **Targeted re-sync** -- [LibraryIndexer.syncSongs] for just the saved songs' MediaStore ids,
 *    so the library reflects the new tags without a full rescan and without losing per-song user
 *    state (favourite/play count -- the indexer's merge guarantees that).
 * 3. **Invalidate cached artwork** -- [SongArtworkCache.invalidate] for every saved song's id
 *    (spec §5.3): each track has its own picture, so each drops its own cached entry, positive or
 *    "no picture". This runs BEFORE step 4 (refresh playback) on purpose: the refresh re-renders
 *    now-playing, the notification and the widget, and a save within the same second leaves the
 *    row's `dateModified` (and so the artwork value's version) unchanged (ruling R6), so the stale
 *    entry must already be gone for the next load to read the file again. It also runs when steps
 *    1-2 failed: the file changed regardless, so its cached picture is stale either way.
 * 4. **Refresh playback** -- for each saved song, [PlaybackController.updateSongMetadata] with the
 *    freshly re-synced row, so the now-playing UI, queue, media notification and widget re-render
 *    the new tags if that song is loaded. Songs the re-sync removed (file vanished between write
 *    and sync) are skipped. Skipped when steps 1-2 failed: there is no re-synced row to push.
 *
 * The whole pipeline is **best-effort by design**: the user's file write has already succeeded by
 * the time this runs, so a failure here (scanner hiccup, DB error, cache failure) must never be
 * reported as a failed save -- it is swallowed, and the next full library sync repairs the
 * staleness. Steps 1-2 share one guard; each song's invalidation has its own, and so does the
 * refresh, so one failing invalidation neither skips the other songs' nor the refresh (v1.0 review
 * follow-up #1). Callers should still `await` this before closing their screen: it runs in the
 * caller's scope, and the library list the user returns to should already show the new tags.
 *
 * Call from the main dispatcher (any `viewModelScope` launch): each collaborator hops to its own
 * IO dispatcher internally, and [PlaybackController] must be touched from main.
 */
class TagSaveFinalizer @Inject constructor(
    private val songFileResolver: SongFileResolver,
    private val mediaScanNotifier: MediaScanNotifier,
    private val libraryIndexer: LibraryIndexer,
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController,
    private val songArtworkCache: SongArtworkCache,
) {

    suspend fun onSongsSaved(songs: List<Song>) {
        if (songs.isEmpty()) return
        val songIds = songs.mapNotNull { it.id.toLongOrNull() }
        val reSynced = bestEffort {
            mediaScanNotifier.awaitScan(songs.mapNotNull { songFileResolver.resolveFilePath(it) })
            libraryIndexer.syncSongs(songIds)
        }
        songIds.forEach { songId -> bestEffort { songArtworkCache.invalidate(songId) } }
        if (reSynced) {
            bestEffort { refreshPlayback(songs) }
        }
    }

    private suspend fun refreshPlayback(saved: List<Song>) {
        val updatedById = songRepository.observeSongs().first().associateBy(Song::id)
        saved.forEach { song -> updatedById[song.id]?.let(playbackController::updateSongMetadata) }
    }

    /**
     * Runs one pipeline step, swallowing any failure but cancellation: the file write already succeeded, so a step's
     * failure is never surfaced as a save error. Returns whether the step completed.
     */
    private inline fun bestEffort(step: () -> Unit): Boolean = try {
        step()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
        false
    }
}
