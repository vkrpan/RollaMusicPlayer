package com.rolla.musicplayer.core.data.artwork

/**
 * Decodes a local `content://` album-art uri into a downscaled, JPEG-compressed byte array ready
 * to persist as [AlbumArtworkCache]'s on-disk cache entry, or `null` if [sourceUri] has no
 * decodable image.
 *
 * ### Testability seam
 * [AlbumArtworkCacheImpl]'s cache bookkeeping (key derivation, hit/miss, invalidation, LRU
 * eviction) is plain-Kotlin logic that JVM unit tests must be able to drive without Robolectric.
 * The one genuinely Android-only piece -- decoding bytes through [android.graphics.BitmapFactory]
 * -- sits entirely behind this fun interface so tests can substitute a fake that returns fixed
 * bytes. [BitmapArtworkDecoder] is the real implementation, bound in DI; nothing else in
 * `:core:data` should call into `android.graphics` directly for this purpose.
 */
fun interface ArtworkDecoder {
    suspend fun decode(sourceUri: String): ByteArray?
}
