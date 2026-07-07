package com.rolla.musicplayer.feature.tageditor.io

import android.os.Build
import com.rolla.musicplayer.feature.tageditor.SongTags
import com.rolla.musicplayer.feature.tageditor.TagField
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File
import javax.inject.Inject

/**
 * Writes editable [TagField] values to a local audio file with jaudiotagger and commits the
 * change to disk.
 *
 * Blank-field handling is intentional, not an oversight: a blank value means "clear this tag"
 * ([Tag.deleteField]); a non-blank value overwrites it ([Tag.setField]). There is no third state
 * for "leave untouched" within a single [writeFields] call for a field that IS in [writeFields]'s
 * `fields` map -- a caller that wants a field left alone entirely (e.g. the batch editor's
 * per-field overwrite selection) must simply omit that [TagField] key from the map passed in for
 * that file. [writeFields] itself cannot tell "user left this blank on purpose" apart from "user
 * never touched this field" for a key that IS present -- that distinction is the caller's job.
 *
 * Pure JVM/[File]-based, same as [TagReader] -- always operates on a local [File], never a
 * `content://` uri. [SongFileResolver] produces an editable local copy first; a later step
 * (outside this class) streams the edited copy back over the uri once write consent is granted.
 */
class TagWriter @Inject constructor(
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Writes every one of [SongTags]'s 8 fields, clearing any that are blank -- the single-song
     * editor's contract, where every field is always "in scope" for the write. Delegates to
     * [writeFields] with the full [TagField]-to-value map so both entry points share one FieldKey
     * mapping and one commit strategy.
     */
    suspend fun write(file: File, tags: SongTags) = writeFields(file, tags.toFieldMap())

    /**
     * Writes only the [TagField]s present as keys in [fields], each cleared ([Tag.deleteField])
     * when its value is blank or overwritten ([Tag.setField]) when non-blank; any [TagField] NOT
     * present in [fields] is left completely untouched on disk -- this is what lets the batch
     * editor honor its per-field overwrite selection (an unpicked field for a given song is simply
     * never included in that song's `fields` map).
     */
    suspend fun writeFields(file: File, fields: Map<TagField, String>) = withContext(ioDispatcher) {
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
        fields.forEach { (field, value) -> tag.setFieldIfNotBlank(field.toFieldKey(), value) }
        audioFile.commit()
    }

    /**
     * Replaces the file's embedded artwork (front cover) with [artwork]'s bytes and commits.
     * All existing artwork fields are deleted first so the file ends up with exactly one cover
     * -- some files in the wild carry several APIC frames, and "replace" leaving stale extras
     * behind would show a different image in players that read a different frame.
     *
     * The artwork object is populated field-by-field ([org.jaudiotagger.tag.images.Artwork.setBinaryData]
     * etc.) rather than via `ArtworkFactory.createArtworkFromFile` -- the picked image arrives as
     * bytes from a content uri, not a [File]. Same in-place commit strategy
     * (isPreserveFileIdentity) as [writeFields], for the same reason.
     *
     * FLAC/Vorbis-comment's own [Tag.setField] implementation for an [org.jaudiotagger.tag.images.Artwork]
     * unconditionally calls `Artwork.setImageFromData()` first, to (re)derive width/height before
     * building the picture block -- regardless of how the artwork's fields were populated above.
     * The `Artwork` implementation `ArtworkFactory.getNew()` returns depends on
     * `TagOptionSingleton.isAndroid`: left at its default `false`, that method decodes through
     * `java.awt.image.BufferedImage`/`javax.imageio` -- neither of which exists on Android, so
     * embedding artwork into a FLAC file would throw `NoClassDefFoundError` on a real device. With
     * `isAndroid` set, the same method decodes through `android.graphics.BitmapFactory` instead,
     * which is what real Android needs (MP3/MP4 never call `setImageFromData()`, so this has no
     * effect on them either way). `Build.VERSION.SDK_INT` reads 0 in a plain JVM unit test (there
     * is no Robolectric dependency in this module, and `BitmapFactory` has no real decoder
     * off-device -- see `TagReaderWriterContractTest`'s KDoc), so this only flips on when actually
     * running on a device, leaving the JVM test environment on the desktop-friendly default, where
     * `java.awt`/`javax.imageio` genuinely exist and this same FLAC round trip keeps passing for real.
     */
    suspend fun writeArtwork(file: File, artwork: PickedArtwork) = withContext(ioDispatcher) {
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        TagOptionSingleton.getInstance().isAndroid = Build.VERSION.SDK_INT != 0
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        val embedded = ArtworkFactory.getNew().apply {
            binaryData = artwork.bytes
            mimeType = artwork.mimeType
            pictureType = PictureTypes.DEFAULT_ID
            width = artwork.width
            height = artwork.height
            // FLAC's picture block serializes the description unconditionally -- null would NPE.
            description = ""
        }
        tag.deleteArtworkField()
        tag.setField(embedded)
        audioFile.commit()
    }
}

/**
 * Sets [key] to [value] when non-blank, otherwise deletes it -- the blank-means-clear contract
 * documented on [TagWriter.writeFields] and on [SongTags].
 */
private fun Tag.setFieldIfNotBlank(key: FieldKey, value: String) {
    if (value.isNotBlank()) setField(key, value) else deleteField(key)
}

/** Every [SongTags] field, keyed by its [TagField] -- the single-editor's "all fields in scope" map. */
private fun SongTags.toFieldMap(): Map<TagField, String> = mapOf(
    TagField.TITLE to title,
    TagField.ARTIST to artist,
    TagField.ALBUM to album,
    TagField.ALBUM_ARTIST to albumArtist,
    TagField.GENRE to genre,
    TagField.YEAR to year,
    TagField.TRACK_NUMBER to trackNumber,
    TagField.COMPOSER to composer,
)

private fun TagField.toFieldKey(): FieldKey = when (this) {
    TagField.TITLE -> FieldKey.TITLE
    TagField.ARTIST -> FieldKey.ARTIST
    TagField.ALBUM -> FieldKey.ALBUM
    TagField.ALBUM_ARTIST -> FieldKey.ALBUM_ARTIST
    TagField.GENRE -> FieldKey.GENRE
    TagField.YEAR -> FieldKey.YEAR
    TagField.TRACK_NUMBER -> FieldKey.TRACK
    TagField.COMPOSER -> FieldKey.COMPOSER
}
