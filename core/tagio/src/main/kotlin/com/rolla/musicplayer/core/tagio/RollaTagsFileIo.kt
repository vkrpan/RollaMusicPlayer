package com.rolla.musicplayer.core.tagio

import com.rolla.musicplayer.core.model.CustomTagIds
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Frames
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jcodec.containers.mp4.MP4Util
import org.jcodec.containers.mp4.boxes.NodeBox
import org.jcodec.containers.mp4.boxes.UdtaBox
import java.io.File
import java.io.RandomAccessFile
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/** What a song's file says about it beyond MediaStore: its custom tag IDs and its genre (null when blank). */
data class FileTags(val customTagIds: CustomTagIds, val genre: String?)

/**
 * Whether files with this extension carry the `ROLLA_TAGS` field: MP3, FLAC and M4A, case-insensitively. Lets a
 * caller decide from a name alone, without opening the file.
 */
fun isCustomTagExtension(extension: String): Boolean = extension.lowercase() in CUSTOM_TAG_EXTENSIONS

/**
 * Whether [RollaTagsFileIo.writeCustomTagIds] writes files with this extension: MP3 and FLAC, case-insensitively. M4A
 * is read but not written: the jaudiotagger fork's `Mp4TagCreator` writes only the freeform atoms `Mp4FieldKey` lists,
 * and `----:com.apple.iTunes:ROLLA_TAGS` is not one of them (decision pending before phase 2).
 */
fun canWriteCustomTags(extension: String): Boolean = extension.lowercase() in WRITABLE_EXTENSIONS

/** Whether this file's format carries the `ROLLA_TAGS` field ([isCustomTagExtension] of its extension). */
fun File.supportsCustomTags(): Boolean = isCustomTagExtension(extension)

/**
 * Reads and writes the [RollaTagsJson.FIELD_NAME] field in a song's own file, where other programs can read it too:
 * - MP3: an ID3v2 user-defined text frame (`TXXX`, `TXX` in ID3v2.2) whose description is `ROLLA_TAGS`, in ISO-8859-1
 *   (the value is ASCII);
 * - FLAC: the Vorbis comment `ROLLA_TAGS`;
 * - M4A: the freeform atom `----:com.apple.iTunes:ROLLA_TAGS`, read only ([canWriteCustomTags]). jaudiotagger's
 *   `Mp4TagReader` reads only the freeform atoms `Mp4FieldKey` lists, so the atom is read with the jcodec MP4 parser
 *   the fork bundles.
 *
 * Plain blocking functions: callers choose the dispatcher. Only MP3, FLAC and M4A files ([supportsCustomTags]) are
 * handed in; another format reads as having no field and is never written.
 *
 * The first instance sets jaudiotagger's `java.util.logging` logger to [Level.WARNING] for the whole process:
 * jaudiotagger logs INFO lines for every file it opens, which would flood logcat during a scan's first pass over a
 * large library.
 */
class RollaTagsFileIo @Inject constructor() {

    private companion object {
        /**
         * jaudiotagger's root logger, quieted once when this class is first used. Held strongly here because
         * `java.util.logging` keeps loggers weakly: a collected logger would come back without its level.
         */
        val jaudiotaggerLogger: Logger = Logger.getLogger("org.jaudiotagger").apply { level = Level.WARNING }
    }

    /**
     * The file's custom tag IDs and genre, read through [editableTag]: an untagged file reads as the in-memory default
     * tag rather than null, and [read] and [writeCustomTagIds] see the same tag. An absent or undecodable field reads
     * as [CustomTagIds.EMPTY]; a blank genre as null. Never writes the file: the tag [editableTag] may build lives in
     * memory only and is never committed.
     *
     * @throws Exception any jaudiotagger or IO exception when the file cannot be opened or parsed
     *   (`CannotReadException`, `IOException`, `TagException`, `ReadOnlyFileException`, `InvalidAudioFrameException`,
     *   among others).
     */
    fun read(file: File): FileTags {
        val tag = AudioFileIO.read(file).editableTag()
        val value = if (tag is Mp4Tag) file.mp4RollaTagsValue() else tag.rollaTagsValue()
        return FileTags(
            customTagIds = value?.let(RollaTagsJson::decode) ?: CustomTagIds.EMPTY,
            genre = tag.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Writes [ids] into the file's field, replacing any value already there rather than adding a second one, and
     * commits in place. When [ids] is empty the field is deleted instead. Every other field (the comment, other
     * user-defined frames, artwork, and an ID3v1-only MP3's text, through [editableTag]) is kept.
     *
     * @throws UnsupportedOperationException before touching the file, for a format [canWriteCustomTags] rejects: M4A,
     *   and any format other than MP3 and FLAC.
     * @throws Exception any jaudiotagger or IO exception when the file cannot be read or the commit fails
     *   (`CannotReadException`, `CannotWriteException`, `IOException`, `TagException`, among others).
     */
    fun writeCustomTagIds(file: File, ids: CustomTagIds) {
        if (!canWriteCustomTags(file.extension)) throw UnsupportedOperationException(refusalMessage(file.extension))
        // Commit by rewriting the file in place, not by jaudiotagger's rename-aside default (see TagWriter in
        // :feature:tageditor); a process-wide option, so it is set on every write.
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.editableTag()
        tag.deleteRollaTags()
        if (!ids.isEmpty) tag.addRollaTags(RollaTagsJson.encode(ids))
        audioFile.commit()
    }
}

private val CUSTOM_TAG_EXTENSIONS = setOf("mp3", "flac", "m4a")
private val WRITABLE_EXTENSIONS = setOf("mp3", "flac")

private const val MP4_KEY = "----:com.apple.iTunes:${RollaTagsJson.FIELD_NAME}"

/** Why [RollaTagsFileIo.writeCustomTagIds] refuses [extension]: the fork's MP4 limit for M4A, otherwise unsupported. */
private fun refusalMessage(extension: String): String {
    val refused = "${RollaTagsJson.FIELD_NAME} is written to MP3 and FLAC files only, not to .$extension"
    return if (extension.equals("m4a", ignoreCase = true)) {
        "$refused: the jaudiotagger fork's Mp4TagCreator writes only the freeform atoms Mp4FieldKey lists"
    } else {
        "$refused: unsupported format"
    }
}

/** The field's raw value in an ID3v2 or FLAC tag, or null when this tag has none. */
private fun Tag.rollaTagsValue(): String? = when (this) {
    is AbstractID3v2Tag -> rollaTxxxFrames().firstOrNull()?.let { (it.body as FrameBodyTXXX).text }
    is FlacTag -> vorbisCommentTag.getFirst(RollaTagsJson.FIELD_NAME)
    else -> null
}

/** The text of this M4A's `----:com.apple.iTunes:ROLLA_TAGS` atom (udta > meta > ilst), or null when it has none. */
private fun File.mp4RollaTagsValue(): String? = RandomAccessFile(this, "r").use { raf ->
    val moov = MP4Util.parseFullMovieChannel(raf.channel)?.moov ?: return null
    val value = NodeBox.findFirst(moov, UdtaBox::class.java, UdtaBox.fourcc())?.meta()?.rdnsMeta?.get(MP4_KEY)
    value?.takeIf { it.isString }?.string
}

private fun Tag.deleteRollaTags() {
    when (this) {
        is AbstractID3v2Tag -> {
            val kept = userTextFrames().filterNot { it.isRollaTags() }
            if (kept.isEmpty()) removeFrame(txxxId()) else setFrame(txxxId(), kept)
        }
        is FlacTag -> vorbisCommentTag.deleteField(RollaTagsJson.FIELD_NAME)
    }
}

/** Adds the field holding [value]; called after [deleteRollaTags], so the file ends up with exactly one. */
private fun Tag.addRollaTags(value: String) {
    when (this) {
        is AbstractID3v2Tag -> {
            val frame = createFrame(txxxId()).apply {
                body = FrameBodyTXXX(TextEncoding.ISO_8859_1, RollaTagsJson.FIELD_NAME, value)
            }
            setFrame(txxxId(), userTextFrames() + frame)
        }
        is FlacTag -> vorbisCommentTag.setField(RollaTagsJson.FIELD_NAME, value)
    }
}

/** The user-defined text frame ID of this tag's ID3v2 version. */
private fun AbstractID3v2Tag.txxxId(): String =
    if (this is ID3v22Tag) ID3v22Frames.FRAME_ID_V2_USER_DEFINED_INFO else ID3v24Frames.FRAME_ID_USER_DEFINED_INFO

private fun AbstractID3v2Tag.userTextFrames(): List<AbstractID3v2Frame> =
    getFields(txxxId()).filterIsInstance<AbstractID3v2Frame>()

private fun AbstractID3v2Tag.rollaTxxxFrames(): List<AbstractID3v2Frame> = userTextFrames().filter { it.isRollaTags() }

private fun AbstractID3v2Frame.isRollaTags(): Boolean =
    (body as? FrameBodyTXXX)?.description == RollaTagsJson.FIELD_NAME
