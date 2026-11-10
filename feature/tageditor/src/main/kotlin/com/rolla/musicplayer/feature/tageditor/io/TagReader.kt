package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.core.tagio.editableTag
import com.rolla.musicplayer.core.tagio.hasAnyArtwork
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
     * Reads [file]'s tag through [editableTag], the same tag the writer edits: an in-memory
     * default tag (never written to disk) when the file has none -- that path yields an all-blank
     * [SongTags], not an error -- and, for an MP3 with only an ID3v1 tag, an in-memory ID3v2 tag
     * built from it, so the editor shows the file's existing text (ruling P18). Never writes.
     */
    suspend fun read(file: File): SongTags = withContext(ioDispatcher) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.editableTag()
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

    /**
     * Whether [file]'s own tag embeds at least one picture, including a FLAC picture stored as a Vorbis-comment field
     * ([hasAnyArtwork], ruling P17). Reads the tag as it is on disk (no default tag is created), so an untagged file
     * answers false.
     */
    suspend fun hasArtwork(file: File): Boolean = withContext(ioDispatcher) {
        AudioFileIO.read(file).tag?.hasAnyArtwork() == true
    }
}

/** [Tag.getFirst] returns `""` for an absent field, never null, but this guards it either way. */
private fun Tag.firstOrBlank(key: FieldKey): String = getFirst(key) ?: ""
