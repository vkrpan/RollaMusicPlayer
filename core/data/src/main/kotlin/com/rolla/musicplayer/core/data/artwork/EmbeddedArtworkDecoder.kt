package com.rolla.musicplayer.core.data.artwork

/** What a track's own file holds as its picture: a downscaled JPEG, or nothing. */
sealed interface DecodedArtwork {

    /** The embedded picture, downscaled and JPEG-compressed, ready to persist as the cache entry. */
    class Picture(val jpeg: ByteArray) : DecodedArtwork

    /** The file has no embedded picture; the cache remembers this with a negative marker. */
    data object None : DecodedArtwork
}

/**
 * Reads the picture embedded in the audio file of the song with [decode]'s `songId` (the MediaStore row id).
 *
 * Throws on any read or decode failure (an unreadable file, a picture `BitmapFactory` rejects): a failure is not "no
 * picture", so [SongArtworkCacheImpl] caches nothing for it and a later load tries again (Review Focus 3).
 *
 * The testability seam: [SongArtworkCacheImpl]'s bookkeeping is plain Kotlin that JVM tests drive with a fake;
 * [RetrieverArtworkDecoder], the Android-only implementation, is bound in DI.
 */
fun interface EmbeddedArtworkDecoder {
    suspend fun decode(songId: Long): DecodedArtwork
}
