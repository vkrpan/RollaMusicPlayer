package com.rolla.musicplayer.core.media

import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.SongArtworkRef
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [SongArtworkCache] whose [get] answers with [answer] and records every ref it was asked for. The bitmap loader
 * calls [get] on `Dispatchers.IO`, so the record is thread-safe. [peek], [invalidate] and [clear] are never used by the
 * loader and fail loudly if they are.
 */
internal class FakeSongArtworkCache(
    private val answer: (SongArtworkRef) -> File?,
) : SongArtworkCache {
    val getCalls: MutableList<SongArtworkRef> = CopyOnWriteArrayList()

    override suspend fun get(ref: SongArtworkRef): File? {
        getCalls += ref
        return answer(ref)
    }

    override fun peek(ref: SongArtworkRef): File? = error("peek is not used by the bitmap loader")

    override suspend fun invalidate(songId: Long) = error("invalidate is not used by the bitmap loader")

    override suspend fun clear() = error("clear is not used by the bitmap loader")
}
