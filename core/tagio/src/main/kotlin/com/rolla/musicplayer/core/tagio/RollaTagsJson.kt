package com.rolla.musicplayer.core.tagio

import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The value of the `ROLLA_TAGS` field a song's file carries: its custom tag IDs as a JSON object whose `tags` and
 * `subTags` are comma-separated ID lists, `{"tags":"2,3","subTags":"1,3,4"}`.
 */
object RollaTagsJson {

    /** The field's name in every tag format (ID3v2 TXXX description, Vorbis comment, MP4 freeform atom). */
    const val FIELD_NAME = "ROLLA_TAGS"

    private const val KEY_TAGS = "tags"
    private const val KEY_SUB_TAGS = "subTags"

    /**
     * [ids] as the field's value: each list in ascending order, no spaces, an empty list as `""`. When [ids] is empty
     * a caller deletes the field rather than writing `{"tags":"","subTags":""}`.
     */
    fun encode(ids: CustomTagIds): String = buildJsonObject {
        put(KEY_TAGS, ids.tagIds.toIdList())
        put(KEY_SUB_TAGS, ids.subTagIds.toIdList())
    }.toString()

    /**
     * The IDs a field value holds, or null when [value] is not a JSON object. A missing or non-string `tags` or
     * `subTags` reads as empty; each comma-separated entry is trimmed, and an entry that is not a positive decimal
     * `Long` at most [MAX_CUSTOM_TAG_ID] is dropped, so a hand-edited value cannot raise a list's counter past it.
     */
    fun decode(value: String): CustomTagIds? {
        val json = parseOrNull(value) as? JsonObject ?: return null
        return CustomTagIds(
            tagIds = json.idsAt(KEY_TAGS),
            subTagIds = json.idsAt(KEY_SUB_TAGS),
        )
    }

    private fun Set<Long>.toIdList(): String = sorted().joinToString(",")

    private fun parseOrNull(value: String): JsonElement? = try {
        Json.parseToJsonElement(value)
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.idsAt(key: String): Set<Long> {
        val list = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return emptySet()
        return list.split(',')
            .map { it.trim() }
            .filter { entry -> entry.isNotEmpty() && entry.all { it in '0'..'9' } }
            .mapNotNull { it.toLongOrNull() }
            .filter { it in 1..MAX_CUSTOM_TAG_ID }
            .toSet()
    }
}
