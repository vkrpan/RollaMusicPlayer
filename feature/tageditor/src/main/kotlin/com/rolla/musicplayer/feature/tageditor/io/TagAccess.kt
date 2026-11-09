package com.rolla.musicplayer.feature.tageditor.io

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentFieldKey
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag

/*
 * Tag-access helpers shared by [TagReader] and [TagWriter], covering the places jaudiotagger's generic [Tag] API does
 * not reach:
 *
 * - A FLAC picture can be a PICTURE metadata block (what [FlacTag] reports and deletes) or a Vorbis-comment field,
 *   `METADATA_BLOCK_PICTURE` or the legacy `COVERART` (what only [FlacTag.getVorbisCommentTag] reports and deletes).
 *   Android shows either, so both count and both are removed (ruling P17). The Vorbis form is detected by field
 *   presence, never by decoding: [VorbisCommentTag.getArtworkList] base64-decodes, and a malformed value throws
 *   `IllegalArgumentException` (or `BufferUnderflowException`), which would block the very Remove that clears it
 *   (ruling P19).
 * - An MP3 with only an ID3v1 tag: `getTagOrCreateDefault` starts an EMPTY ID3v2 tag, and Android reads ID3v1 only
 *   when there is no ID3v2 tag, so the editor would show blank fields and the first save, of text or of a picture,
 *   would hide the file's text (rulings P16 and P18; [editableTag]).
 */

/**
 * Whether this tag holds any picture: an ordinary artwork field or, for FLAC, a Vorbis-comment picture field. Never
 * throws on a malformed Vorbis picture (see the file header).
 */
internal fun Tag.hasAnyArtwork(): Boolean =
    firstArtwork != null || (this as? FlacTag)?.vorbisCommentTag?.hasPictureField() == true

/** Deletes every picture this tag holds, in every form [hasAnyArtwork] counts. Text fields are untouched. */
internal fun Tag.deleteEveryArtwork() {
    deleteArtworkField()
    (this as? FlacTag)?.vorbisCommentTag?.deleteArtworkField()
}

/**
 * The tag every read and write of this file works on ([TagReader.read], [TagWriter.writeFields],
 * [TagWriter.writeArtwork]; rulings P16 and P18). For an MP3 with an ID3v1 tag and no ID3v2 tag, first sets, in memory
 * only, an ID3v2.3 tag (the version jaudiotagger creates by default) built from the ID3v1 tag, so the editor shows the
 * file's title, artist, album, year, track and genre, and a save keeps every field it does not change. The ID3v1 tag
 * stays as it was. Every other file gets jaudiotagger's usual tag-or-new-default. Nothing is written here; only a
 * caller's `commit()` writes.
 *
 * The genre is set again from the ID3v1 tag after the conversion: jaudiotagger converts it to a TCON of `(17) Rock`,
 * which reads back as `Rock  Rock` until committed, while setting it gives the normal ID3v2.3 form, which reads `Rock`.
 */
internal fun AudioFile.editableTag(): Tag {
    if (this is MP3File && hasID3v1Tag() && !hasID3v2Tag()) {
        val v1 = iD3v1Tag
        val v2 = ID3v23Tag(v1)
        v2.setFieldIfNotBlank(FieldKey.GENRE, v1.getFirst(FieldKey.GENRE).orEmpty())
        setID3v2Tag(v2)
    }
    return tagOrCreateAndSetDefault
}

/** A picture field is present, in either Vorbis-comment form; its value is never decoded. */
private fun VorbisCommentTag.hasPictureField(): Boolean =
    hasField(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE) || hasField(VorbisCommentFieldKey.COVERART)
