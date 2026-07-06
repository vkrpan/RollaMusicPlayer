package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.feature.tageditor.SongTags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import javax.inject.Inject

/**
 * Writes the editable [SongTags] fields to a local audio file with jaudiotagger and commits the
 * change to disk.
 *
 * Blank-field handling is intentional, not an oversight: a blank field means "clear this tag"
 * ([Tag.deleteField]); a non-blank field overwrites it ([Tag.setField]). There is no third state
 * here for "leave untouched" -- a caller that wants a field left alone (e.g. the batch editor's
 * per-field overwrite selection) must simply not route that field into the [SongTags] passed to
 * [write] for that file in the first place. [write] itself cannot tell "user left this blank on
 * purpose" apart from "user never touched this field".
 *
 * Pure JVM/[File]-based, same as [TagReader] -- always operates on a local [File], never a
 * `content://` uri. [SongFileResolver] produces an editable local copy first; a later step
 * (outside this class) streams the edited copy back over the uri once write consent is granted.
 */
class TagWriter @Inject constructor(
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun write(file: File, tags: SongTags) = withContext(ioDispatcher) {
        // jaudiotagger's default commit() strategy renames the original file aside to "*.old",
        // writes the new content under the original name, then deletes the ".old" copy. That
        // rename can fail on Windows (observed for MP4/AudioSpecificConfig writes specifically,
        // likely an open file handle held by the mp4 writer) -- and is simply unnecessary risk
        // here regardless of platform, since [file] is always our own app-cache copy (see
        // SongFileResolver's copy-through-cache strategy), never a file we need to preserve the
        // identity/inode of. Opting into `isPreserveFileIdentity` makes commit() write the new
        // bytes into the existing File in place instead, avoiding the rename dance entirely. This
        // is a process-wide jaudiotagger setting (there is no per-call option), set defensively on
        // every write since it is idempotent and this is the only place in the app that writes tags.
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setFieldIfNotBlank(FieldKey.TITLE, tags.title)
        tag.setFieldIfNotBlank(FieldKey.ARTIST, tags.artist)
        tag.setFieldIfNotBlank(FieldKey.ALBUM, tags.album)
        tag.setFieldIfNotBlank(FieldKey.ALBUM_ARTIST, tags.albumArtist)
        tag.setFieldIfNotBlank(FieldKey.GENRE, tags.genre)
        tag.setFieldIfNotBlank(FieldKey.YEAR, tags.year)
        tag.setFieldIfNotBlank(FieldKey.TRACK, tags.trackNumber)
        tag.setFieldIfNotBlank(FieldKey.COMPOSER, tags.composer)
        audioFile.commit()
    }
}

/**
 * Sets [key] to [value] when non-blank, otherwise deletes it -- the blank-means-clear contract
 * documented on [TagWriter.write] and on [SongTags].
 */
private fun Tag.setFieldIfNotBlank(key: FieldKey, value: String) {
    if (value.isNotBlank()) setField(key, value) else deleteField(key)
}
