package com.rolla.musicplayer.core.data.artwork

import com.rolla.musicplayer.core.model.SongArtworkRef
import java.io.File

/**
 * A bounded, offline, app-local disk cache of the picture embedded in each track's own audio file, keyed by the
 * MediaStore song id and the file's version (spec §5.1).
 *
 * A [SongArtworkRef] comes from parsing `Song.artworkUri` (`parseSongArtworkUri`). Its version is the row's
 * `date_modified`, so a file rewritten by any app gets a new ref and [get] decodes it afresh, dropping the song's older
 * entries. A track without a picture is remembered by a negative entry, so binds never re-open the file to learn the
 * same answer again. A failed read or decode caches nothing, so a later [get] tries again.
 *
 * Consumers: the Coil interceptor (`:app`), the widget's artwork loader, the Media3 bitmap loader (`:core:media`),
 * `TagSaveFinalizer` (`:feature:tageditor`, [invalidate]) and Settings' manual rescan ([clear]).
 */
interface SongArtworkCache {

    /**
     * The cached, downscaled JPEG of [ref]'s embedded picture, decoding it once on a miss. Null when the file has no
     * picture (remembered) or the read failed (not remembered); the caller shows its placeholder. Runs on the IO
     * dispatcher.
     */
    suspend fun get(ref: SongArtworkRef): File?

    /**
     * [ref]'s cached picture if a complete one is already on disk, else null (also for a remembered "no picture").
     * One stat; never decodes and never suspends, for the image loader's fast path. Files appear only by an atomic
     * rename, so a non-null result is never half written.
     */
    fun peek(ref: SongArtworkRef): File?

    /** Drops every cached version of [songId], picture and negative marker, so the next [get] reads the file again. */
    suspend fun invalidate(songId: Long)

    /** Wipes the whole cache directory (used on a full manual rescan). */
    suspend fun clear()
}
