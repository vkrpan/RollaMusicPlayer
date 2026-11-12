package com.rolla.musicplayer.core.tagio

import com.rolla.musicplayer.core.model.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The custom tag list file (export and import, custom tags phase 3): its exact shape and its strict reading. */
class CustomTagListJsonTest {

    private fun tag(id: Long, name: String) = CustomTag(CustomTagKind.TAG, id, name)

    private fun subTag(id: Long, name: String) = CustomTag(CustomTagKind.SUBTAG, id, name)

    private val technoAndHard = CustomTagListFile(tags = listOf(tag(2, "Techno")), subTags = listOf(subTag(1, "Hard")))

    /** A list file with [tags] and [subTags] as raw JSON array bodies, the rest of the shape correct. */
    private fun fileText(tags: String = "", subTags: String = "") =
        """{"format":"rolla-tags","version":1,"tags":[$tags],"subTags":[$subTags]}"""

    // Mutation: omit "format" → the parsed object has no format → RED.
    @Test
    fun encode_exactShape() {
        val parsed = Json.parseToJsonElement(CustomTagListJson.encode(technoAndHard)).jsonObject

        val expected = fileText(tags = """{"id":2,"name":"Techno"}""", subTags = """{"id":1,"name":"Hard"}""")
        assertEquals(Json.parseToJsonElement(expected), parsed)
        assertEquals(listOf("format", "version", "tags", "subTags"), parsed.keys.toList())
    }

    // Mutation: drop the sort by ID → TAG 5 is written before TAG 2 → RED.
    @Test
    fun encode_sortsEntriesByIdAndIndentsTwoSpaces() = assertEquals(
        """
        {
          "format": "rolla-tags",
          "version": 1,
          "tags": [
            {
              "id": 2,
              "name": "Techno"
            },
            {
              "id": 5,
              "name": "House"
            }
          ],
          "subTags": []
        }
        """.trimIndent(),
        CustomTagListJson.encode(
            CustomTagListFile(tags = listOf(tag(5, "House"), tag(2, "Techno")), subTags = emptyList()),
        ),
    )

    // Mutation: decode drops subTags → [] ≠ [Hard] → RED.
    @Test
    fun decode_roundTrips() =
        assertEquals(technoAndHard, CustomTagListJson.decode(CustomTagListJson.encode(technoAndHard)))

    // Mutation: skip the format check → a file → RED.
    @Test
    fun decode_wrongFormat_isNull() = assertNull(
        CustomTagListJson.decode("""{"format":"other","version":1,"tags":[],"subTags":[]}"""),
    )

    // Mutation: skip the version check → a file → RED.
    @Test
    fun decode_wrongVersion_isNull() = assertNull(
        CustomTagListJson.decode("""{"format":"rolla-tags","version":2,"tags":[],"subTags":[]}"""),
    )

    // Mutation: skip the duplicate-ID check → a file → RED.
    @Test
    fun decode_duplicateIdInAList_isNull() = assertNull(
        CustomTagListJson.decode(fileText(tags = """{"id":2,"name":"Techno"},{"id":2,"name":"House"}""")),
    )

    // Mutation: compare names case-sensitively → "Techno" ≠ "techno" → a file → RED.
    @Test
    fun decode_duplicateNameIgnoringCase_isNull() = assertNull(
        CustomTagListJson.decode(fileText(tags = """{"id":2,"name":"Techno"},{"id":3,"name":"techno"}""")),
    )

    // 41 emoji are 41 code points (82 UTF-16 units). Mutation: skip name validation → files → RED.
    @Test
    fun decode_blankOrTooLongName_isNull() {
        assertNull(CustomTagListJson.decode(fileText(tags = """{"id":2,"name":"  "}""")))
        assertNull(CustomTagListJson.decode(fileText(tags = """{"id":2,"name":"${"😀".repeat(41)}"}""")))
    }

    // The limits themselves are accepted: a name of exactly 40 code points (emoji, 80 UTF-16 units) and the ceiling ID.
    // Mutation: `>=` for `>` in the length check → null → RED.
    @Test
    fun decode_fortyCodePointName_isAccepted() {
        val forty = "😀".repeat(CUSTOM_TAG_NAME_MAX_LENGTH)

        assertEquals(
            CustomTagListFile(tags = listOf(tag(2, forty)), subTags = emptyList()),
            CustomTagListJson.decode(fileText(tags = """{"id":2,"name":"$forty"}""")),
        )
    }

    // Mutation: refuse the ceiling itself (`id >= MAX_CUSTOM_TAG_ID`) → null → RED.
    @Test
    fun decode_ceilingId_isAccepted() = assertEquals(
        CustomTagListFile(tags = emptyList(), subTags = listOf(subTag(MAX_CUSTOM_TAG_ID, "Hard"))),
        CustomTagListJson.decode(fileText(subTags = """{"id":$MAX_CUSTOM_TAG_ID,"name":"Hard"}""")),
    )

    // Mutation: accept any Long → files → RED.
    @Test
    fun decode_idAboveCeilingOrNotPositive_isNull() {
        assertNull(CustomTagListJson.decode(fileText(tags = """{"id":1000000000,"name":"Techno"}""")))
        assertNull(CustomTagListJson.decode(fileText(subTags = """{"id":0,"name":"Hard"}""")))
    }

    // Mutation: let the parse exception escape → throws instead of null → RED.
    @Test
    fun decode_garbage_isNull() {
        assertNull(CustomTagListJson.decode("""{"format":"rolla-tags","""))
        assertNull(CustomTagListJson.decode("not json"))
        assertNull(CustomTagListJson.decode("[]"))
    }

    // The two lists are independent. Mutation: check name uniqueness across both lists → null → RED.
    @Test
    fun decode_sameNameAcrossLists_isFine() {
        val file = CustomTagListJson.decode(
            fileText(tags = """{"id":2,"name":"Hard"}""", subTags = """{"id":1,"name":"Hard"}"""),
        )

        assertNotNull(file)
        assertEquals(CustomTagListFile(tags = listOf(tag(2, "Hard")), subTags = listOf(subTag(1, "Hard"))), file)
    }
}
