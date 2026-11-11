package com.rolla.musicplayer.core.ui.customtags

import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTagPickerStateTest {

    // Mutation: startsWith instead of contains → "HiTech" dropped → [Techno].
    @Test
    fun filterCustomTags_matchesSubstringIgnoringCase() {
        val all = listOf(tag(1, "Techno"), tag(2, "HiTech"), tag(3, "House"))

        assertEquals(listOf(tag(1, "Techno"), tag(2, "HiTech")), filterCustomTags(all, "tech"))
    }

    // Mutation: no accent folding → "emile" is not in "émile" → [].
    @Test
    fun filterCustomTags_ignoresAccents() {
        val all = listOf(tag(1, "Émile"), tag(2, "House"))

        assertEquals(listOf(tag(1, "Émile")), filterCustomTags(all, "emile"))
    }

    // Mutation: no trim → "  " matches nothing → [].
    @Test
    fun filterCustomTags_blankQuery_returnsAll() {
        val all = listOf(tag(1, "Techno"), tag(2, "HiTech"), tag(3, "House"))

        assertEquals(all, filterCustomTags(all, "  "))
    }

    // Mutation: always add → {2} after two toggles, not EMPTY.
    @Test
    fun toggled_addsThenRemovesPerKind() {
        val added = CustomTagIds.EMPTY.toggled(CustomTagKind.TAG, 2)
        assertEquals(CustomTagIds(tagIds = setOf(2), subTagIds = emptySet()), added)

        val withSubTag = CustomTagIds(tagIds = emptySet(), subTagIds = setOf(2))
        assertEquals(
            CustomTagIds(tagIds = setOf(2), subTagIds = setOf(2)),
            withSubTag.toggled(CustomTagKind.TAG, 2),
        )

        assertEquals(CustomTagIds.EMPTY, added.toggled(CustomTagKind.TAG, 2))
    }

    // Mutation: iterate ids instead of all → order 3, 1 → ["Techno", "Acid"].
    @Test
    fun selectedNames_libraryOrderSkippingUnknown() {
        val all = listOf(tag(1, "Acid"), tag(3, "Techno"))

        assertEquals(listOf("Acid", "Techno"), selectedNames(all, linkedSetOf(3L, 99L, 1L)))
    }

    private fun tag(id: Long, name: String) = CustomTag(CustomTagKind.TAG, id, name)
}
