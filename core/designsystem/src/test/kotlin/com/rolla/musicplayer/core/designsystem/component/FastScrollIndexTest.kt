package com.rolla.musicplayer.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollIndexTest {

    private fun List<FastScrollSection>.of(label: String) = single { it.label == label }

    // Mutation: drop '#' from FastScrollLabels → size 26 → RED.
    @Test
    fun labelsAreHashThenAToZ() {
        assertEquals(27, FastScrollLabels.size)
        assertEquals("#", FastScrollLabels.first())
        assertEquals("A", FastScrollLabels[1])
        assertEquals("Z", FastScrollLabels.last())
    }

    // Keys in library order. Populated letters point at their first row. Spec §10: an empty letter targets the next
    // populated section. Mutations: (a) target the previous populated section → B gives 1 → RED; (b) record the last
    // index instead of the first (put, not putIfAbsent) → E gives 3 → RED.
    @Test
    fun populatedSectionsPointAtTheirFirstRowAndEmptyOnesAtTheNext() {
        val sections = buildFastScrollSections(listOf("1999", "Abba", "Echo", "Emma", "Zed"))
        assertEquals(27, sections.size)
        assertEquals(FastScrollSection("#", 0, populated = true), sections.of("#"))
        assertEquals(FastScrollSection("A", 1, populated = true), sections.of("A"))
        assertEquals(FastScrollSection("B", 2, populated = false), sections.of("B"))
        assertEquals(FastScrollSection("E", 2, populated = true), sections.of("E"))
        assertEquals(FastScrollSection("F", 4, populated = false), sections.of("F"))
        assertEquals(FastScrollSection("Z", 4, populated = true), sections.of("Z"))
    }

    // Spec §10: past the last populated section, an empty letter targets the previous one; before the first, the next.
    // Mutation: drop the "previous" fallback → no target for N…Z → crash/RED.
    @Test
    fun emptyLettersAtTheEndTargetThePreviousSection() {
        val sections = buildFastScrollSections(listOf("Abba", "Mud"))
        assertEquals(FastScrollSection("#", 0, populated = false), sections.of("#"))
        assertEquals(FastScrollSection("N", 1, populated = false), sections.of("N"))
        assertEquals(FastScrollSection("Z", 1, populated = false), sections.of("Z"))
    }

    // Review Focus 1: the section comes from indexLetter, so an accented initial counts under its letter. Mutation:
    // key on key.first().uppercaseChar() instead of indexLetter → 'É' → no E section → RED.
    @Test
    fun accentedInitialsPopulateTheirLetter() {
        val sections = buildFastScrollSections(listOf("Abba", "Émile", "Fox"))
        assertEquals(FastScrollSection("E", 1, populated = true), sections.of("E"))
    }

    // Final fix wave F1: the first rows come from a binary search, so the section boundary must be exact deep inside a
    // large list. 5 000 keys: "Alpha 0".."Alpha 2499" (0–2499), then "Mango 0".."Mango 2499" (2500–4999). Control: M
    // is populated at 2500. B, empty, targets the next populated section (M). Mutation: an off-by-one lower bound
    // (`high = mid - 1` instead of `high = mid`; `low = mid` instead of `low = mid + 1` never terminates here) → M's
    // search settles on 2499, an Alpha row → M unpopulated → RED.
    @Test
    fun aLargeListFindsEachSectionsFirstRow() {
        val keys = List(2_500) { "Alpha $it" } + List(2_500) { "Mango $it" }
        val sections = buildFastScrollSections(keys)
        assertEquals(FastScrollSection("A", 0, populated = true), sections.of("A"))
        assertEquals(FastScrollSection("M", 2_500, populated = true), sections.of("M"))
        assertEquals(FastScrollSection("B", 2_500, populated = false), sections.of("B"))
    }

    // Control: one key still yields all 27 sections. Mutation: always return emptyList() → the control fails → RED.
    @Test
    fun noKeysMeansNoSections() {
        assertEquals(27, buildFastScrollSections(listOf("Abba")).size)
        assertTrue(buildFastScrollSections(emptyList()).isEmpty())
    }

    // Spec §10: the smallest k for which every k-th label fits. Mutation: ceil → floor in the fit check → 26 slots
    // gives 1 → RED.
    @Test
    fun labelStepIsTheSmallestThatFits() {
        assertEquals(1, fastScrollLabelStep(slots = 27))
        assertEquals(1, fastScrollLabelStep(slots = 40))
        assertEquals(2, fastScrollLabelStep(slots = 26))
        assertEquals(2, fastScrollLabelStep(slots = 14))
        assertEquals(3, fastScrollLabelStep(slots = 13))
        assertEquals(4, fastScrollLabelStep(slots = 8))
        assertEquals(27, fastScrollLabelStep(slots = 1))
        assertEquals(27, fastScrollLabelStep(slots = 0))
    }

    // Every label owns an equal slot of the rail, dropped ones included (spec §10: drag still reaches them).
    // Mutation: divide by the visible-label count instead of sections.size → 0.759 maps elsewhere → RED.
    @Test
    fun sectionAtMapsAFractionOverAllLabels() {
        val sections = buildFastScrollSections(listOf("Abba"))
        assertEquals("#", fastScrollSectionAt(sections, 0f).label)
        assertEquals("M", fastScrollSectionAt(sections, 13.5f / 27f).label)
        assertEquals("T", fastScrollSectionAt(sections, 20.5f / 27f).label)
        assertEquals("Z", fastScrollSectionAt(sections, 1f).label)
        assertEquals("#", fastScrollSectionAt(sections, -0.5f).label)
        assertEquals("Z", fastScrollSectionAt(sections, 1.5f).label)
    }
}
