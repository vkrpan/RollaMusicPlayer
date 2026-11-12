package com.rolla.musicplayer.core.tagio

import com.rolla.musicplayer.core.model.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * The custom tag list file that Manage tags exports and imports (custom tags phase 3):
 * `{"format":"rolla-tags","version":1,"tags":[{"id":2,"name":"Techno"}],"subTags":[{"id":1,"name":"Hard"}]}`.
 *
 * Unlike [RollaTagsJson], which drops a bad entry and keeps the rest, the list file is read strictly: an import
 * replaces both lists, so a file that breaks any rule is refused whole rather than half-applied.
 */
object CustomTagListJson {

    /** The value of the file's `format` key. */
    const val FORMAT = "rolla-tags"

    /** The only `version` this codec writes and reads. */
    const val VERSION = 1

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_TAGS = "tags"
    private const val KEY_SUB_TAGS = "subTags"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"

    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * [file] as the list file's text: the keys in the order `format`, `version`, `tags`, `subTags`, each list's
     * entries in ascending ID order, pretty-printed with a two-space indent. Each entry's kind comes from the list it
     * is in, not from [CustomTag.kind].
     */
    fun encode(file: CustomTagListFile): String = prettyJson.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put(KEY_FORMAT, FORMAT)
            put(KEY_VERSION, VERSION)
            put(KEY_TAGS, file.tags.toJsonArray())
            put(KEY_SUB_TAGS, file.subTags.toJsonArray())
        },
    )

    /**
     * The lists [text] holds, each in ascending ID order with trimmed names, or null when [text] breaks any rule: not
     * a JSON object; `format` other than [FORMAT]; `version` other than the number [VERSION]; `tags` or `subTags`
     * missing or not an array; an entry that is not an object with a numeric `id` from 1 to [MAX_CUSTOM_TAG_ID] and a
     * string `name` that is non-blank and at most [CUSTOM_TAG_NAME_MAX_LENGTH] code points once trimmed; or two
     * entries of one list sharing an ID, or a name ignoring case. The two lists may share IDs and names. Other keys are
     * ignored.
     */
    fun decode(text: String): CustomTagListFile? {
        val json = (parseOrNull(text) as? JsonObject)?.takeIf { it.hasFormatAndVersion() } ?: return null
        val tags = json.listAt(KEY_TAGS, CustomTagKind.TAG)
        val subTags = json.listAt(KEY_SUB_TAGS, CustomTagKind.SUBTAG)
        return if (tags == null || subTags == null) null else CustomTagListFile(tags = tags, subTags = subTags)
    }

    private fun List<CustomTag>.toJsonArray(): JsonArray = buildJsonArray {
        sortedBy { it.id }.forEach { tag ->
            add(
                buildJsonObject {
                    put(KEY_ID, tag.id)
                    put(KEY_NAME, tag.name)
                },
            )
        }
    }

    private fun parseOrNull(text: String): JsonElement? = try {
        Json.parseToJsonElement(text)
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.hasFormatAndVersion(): Boolean {
        val format = get(KEY_FORMAT) as? JsonPrimitive
        val version = get(KEY_VERSION) as? JsonPrimitive
        return format?.isString == true && format.content == FORMAT &&
            version != null && !version.isString && version.longOrNull == VERSION.toLong()
    }

    /** The list at [key] as [kind] entries, or null when it is missing or an entry or a pair of them breaks a rule. */
    private fun JsonObject.listAt(key: String, kind: CustomTagKind): List<CustomTag>? {
        val read = (get(key) as? JsonArray)?.map { it.toEntryOrNull(kind) } ?: return null
        val entries = read.filterNotNull()
        val isUnique = entries.distinctBy { it.id }.size == read.size &&
            entries.distinctBy { it.name.lowercase(Locale.ROOT) }.size == read.size
        return if (entries.size == read.size && isUnique) entries.sortedBy { it.id } else null
    }

    private fun JsonElement.toEntryOrNull(kind: CustomTagKind): CustomTag? {
        val entry = this as? JsonObject ?: return null
        val id = (entry[KEY_ID] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        val name = (entry[KEY_NAME] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        return when {
            id == null || id !in 1..MAX_CUSTOM_TAG_ID -> null
            name.isNullOrEmpty() || name.codePointCount(0, name.length) > CUSTOM_TAG_NAME_MAX_LENGTH -> null
            else -> CustomTag(kind = kind, id = id, name = name)
        }
    }
}
