package com.rolla.musicplayer.feature.tageditor

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
 * 3. **Refresh playback** -- for each saved song, [PlaybackController.updateSongMetadata] with the
 *    freshly re-synced row, so the now-playing UI, queue, and media notification (and the widget,
 *    once Phase 6 builds it on the same playback state) re-render the new tags if that song is
 *    loaded. Songs the re-sync removed (file vanished between write and sync) are skipped.
 *
 * The whole pipeline is **best-effort by design**: the user's file write has already succeeded by
 * the time this runs, so a failure here (scanner hiccup, DB error) must never be reported as a
 * failed save -- it is swallowed, and the next full library sync repairs the staleness. Callers
 * should still `await` this before closing their screen: it runs in the caller's scope, and the
 * library list the user returns to should already show the new tags.
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
) {

    suspend fun onSongsSaved(songs: List<Song>) {
        if (songs.isEmpty()) return
        try {
            val paths = songs.mapNotNull { songFileResolver.resolveFilePath(it) }
            mediaScanNotifier.awaitScan(paths)
            libraryIndexer.syncSongs(songs.mapNotNull { it.id.toLongOrNull() })
            refreshPlayback(songs)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Best-effort: the file write already succeeded; never surface this as a save error.
        }
    }

    private suspend fun refreshPlayback(saved: List<Song>) {
        val updatedById = songRepository.observeSongs().first().associateBy(Song::id)
        saved.forEach { song -> updatedById[song.id]?.let(playbackController::updateSongMetadata) }
    }
}
