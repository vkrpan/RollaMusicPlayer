package com.rolla.musicplayer.core.tagio

import com.rolla.musicplayer.core.model.CustomTagIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollaTagsJsonTest {

    @Test
    fun encode_sortsAscendingWithoutSpaces() = assertEquals(
        """{"tags":"2,3","subTags":"1,3,4"}""",
        RollaTagsJson.encode(CustomTagIds(setOf(3, 2), setOf(4, 1, 3))),
    )

    @Test
    fun encode_emptyListsAreEmptyStrings() = assertEquals(
        """{"tags":"","subTags":"5"}""",
        RollaTagsJson.encode(CustomTagIds(emptySet(), setOf(5))),
    )

    @Test
    fun decode_userFormat_roundTrips() = assertEquals(
        CustomTagIds(setOf(2, 3), setOf(1, 3, 4)),
        RollaTagsJson.decode("""{ "tags": "2, 3", "subTags":"1,3,4"}"""),
    )

    @Test
    fun decode_notJson_returnsNull() = assertNull(RollaTagsJson.decode("Techno"))

    // "Techno" parses as an unquoted JSON primitive; a truncated object is what makes the parser throw.
    @Test
    fun decode_malformedJson_returnsNull() = assertNull(RollaTagsJson.decode("""{"tags":"""))

    @Test
    fun decode_jsonArray_returnsNull() = assertNull(RollaTagsJson.decode("[1,2]"))

    @Test
    fun decode_badEntries_keepsOnlyPositiveIds() = assertEquals(
        CustomTagIds(setOf(2, 7), emptySet()),
        RollaTagsJson.decode("""{"tags":"a, 2,,-1,0,+5,99999999999999999999, 7 ","subTags":5}"""),
    )

    // The ceiling is MAX_CUSTOM_TAG_ID: kept at it, dropped one above it and at Long.MAX_VALUE.
    @Test
    fun decode_idAboveMax_isDropped() = assertEquals(
        CustomTagIds(setOf(999_999_999), emptySet()),
        RollaTagsJson.decode("""{"tags":"999999999,1000000000,9223372036854775807"}"""),
    )

    @Test
    fun decode_missingKey_readsAsEmpty() = assertEquals(
        CustomTagIds(emptySet(), setOf(9)),
        RollaTagsJson.decode("""{"subTags":"9"}"""),
    )

    @Test
    fun customTagIds_isEmpty() {
        assertTrue(CustomTagIds.EMPTY.isEmpty)
        assertFalse(CustomTagIds(setOf(1), emptySet()).isEmpty)
        assertFalse(CustomTagIds(emptySet(), setOf(1)).isEmpty)
    }
}
