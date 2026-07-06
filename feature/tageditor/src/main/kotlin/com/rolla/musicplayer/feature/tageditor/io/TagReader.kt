package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.feature.tageditor.SongTags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import javax.inject.Inject

/**
 * Reads the editable [SongTags] fields from a local audio file with jaudiotagger.
 *
 * Deliberately pure JVM/[File]-based -- no Android framework types anywhere in this class -- so
 * it is exercised directly by JVM unit tests across MP3/FLAC/M4A without Robolectric. Resolving a
 * writable local [File] from a `Song`/`content://` uri is [SongFileResolver]'s job, not this
 * class's; callers wire the two together.
 *
 * [AudioFileIO.read] picks the right format reader from the file's extension, so the [File]
 * handed in must keep the original extension (see [SongFileResolver.createEditableCopy]).
 */
class TagReader @Inject constructor(
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Reads [file]'s tag, creating an in-memory default tag (never written to disk) via
     * [org.jaudiotagger.audio.AudioFile.getTagOrCreateAndSetDefault] when the file has none --
     * that path yields an all-blank [SongTags], not an error.
     */
    suspend fun read(file: File): SongTags = withContext(ioDispatcher) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        SongTags(
            title = tag.firstOrBlank(FieldKey.TITLE),
            artist = tag.firstOrBlank(FieldKey.ARTIST),
            album = tag.firstOrBlank(FieldKey.ALBUM),
            albumArtist = tag.firstOrBlank(FieldKey.ALBUM_ARTIST),
            genre = tag.firstOrBlank(FieldKey.GENRE),
            year = tag.firstOrBlank(FieldKey.YEAR),
            trackNumber = tag.firstOrBlank(FieldKey.TRACK),
            composer = tag.firstOrBlank(FieldKey.COMPOSER),
        )
    }
}

/** [Tag.getFirst] returns `""` for an absent field, never null, but this guards it either way. */
private fun Tag.firstOrBlank(key: FieldKey): String = getFirst(key) ?: ""
