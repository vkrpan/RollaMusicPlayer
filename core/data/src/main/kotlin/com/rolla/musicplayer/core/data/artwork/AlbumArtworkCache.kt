package com.rolla.musicplayer.core.data.artwork

import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * A bounded, offline, app-local disk cache of downscaled album-art JPEGs, keyed by `albumId`.
 *
 * ### Why this exists
 * Every UI passes `artworkUri = "content://media/external/audio/albumart/{albumId}"` -- a stable,
 * albumId-keyed local `content://` uri the scanner builds -- straight to its image loader today.
 * That is correct but slow: full-resolution art gets re-decoded from `content://` on every list
 * bind. It is also capable of going STALE: after the tag editor embeds a new cover, the bytes
 * behind that uri change but the uri STRING does not, so anything caching by that uri string (Coil
 * included) keeps serving the old cover until told otherwise. This cache solves both: [get] decodes
 * once and reuses the decoded file thereafter, and [invalidate] gives a byte-changing write (like a
 * cover embed) an explicit, albumId-keyed way to evict the stale copy and tell every consumer.
 *
 * ### Consumers (wave 2, not implemented here)
 * Coil (`:app`), `TagSaveFinalizer` (`:feature:tageditor`), and `WidgetArtworkLoader`
 * (`:feature:widget`) all build against this exact contract.
 *
 * ### Bounded
 * The cache directory is capped at a fixed byte budget; once a write pushes the directory over
 * that cap, the least-recently-used entries are evicted first. See `AlbumArtworkCacheImpl` for the
 * exact budget and eviction order.
 */
interface AlbumArtworkCache {

    /**
     * A cached, downscaled JPEG file for [albumId], decoding+downscaling [sourceUri] once on a
     * miss and returning the cached file thereafter. Returns null if [sourceUri] has no decodable
     * art (caller falls back to the existing placeholder). Runs entirely on the IO dispatcher --
     * never blocks the calling thread beyond the dispatch.
     */
    suspend fun get(albumId: Long, sourceUri: String): File?

    /** Drops [albumId]'s cached file and emits [albumId] on [invalidations] so Coil/widget evict their own copies. */
    suspend fun invalidate(albumId: Long)

    /** Wipes the whole cache directory (used on a full manual rescan). */
    suspend fun clear()

    /** Emits an albumId each time its cached art is invalidated. */
    val invalidations: SharedFlow<Long>
}
