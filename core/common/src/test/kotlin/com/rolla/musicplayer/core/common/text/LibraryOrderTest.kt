package com.rolla.musicplayer.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer
import java.util.Locale

class LibraryOrderTest {

    // Mutations: drop the uppercase → "abba" gives 'a' → '#' → RED; drop trimStart → "  Zed" → '#' → RED.
    @Test
    fun indexLetter_mapsPlainLettersCaseInsensitivelyAfterLeadingSpace() {
        assertEquals('A', indexLetter("abba"))
        assertEquals('M', indexLetter("Mango"))
        assertEquals('Z', indexLetter("  Zed"))
    }

    // Spec §10: the first character's diacritics are stripped. Mutation: skip the NFD normalisation → 'É' and 'ñ' are
    // outside A–Z → '#' → RED.
    @Test
    fun indexLetter_stripsDiacriticsFromTheFirstCharacter() {
        assertEquals('E', indexLetter("Émile"))
        assertEquals('N', indexLetter("ñu"))
        assertEquals('A', indexLetter("Åsa"))
    }

    // Everything outside A–Z after stripping is '#': digits, punctuation, other scripts, letters with no
    // decomposition (Ø), astral characters, and the empty name. Control: the near miss "Ölaf" → 'O' (Ö decomposes to
    // O + diaeresis, Ø does not), so a constant '#' fails here. Mutations: return the uppercased character itself
    // instead of '#' → '1', 'Ω', … → RED; make indexLetter always return '#' → the control fails → RED.
    @Test
    fun indexLetter_mapsEverythingElseToHash() {
        assertEquals('O', indexLetter("Ölaf"))
        listOf("1999", "(Intro)", "Ωmega", "日本", "Øl", "𝄞 Clef", "", "   ").forEach { name ->
            assertEquals("for \"$name\"", '#', indexLetter(name))
        }
    }

    // Final fix wave F1: the ASCII fast path equals the general path (NFD, then the root-locale uppercase) for every
    // printable non-space ASCII character, the reference built here from the slow path. The trailing "x" makes the
    // name longer than its initial. Control: the reference yields 26 distinct letters and '#' for the rest, so it
    // separates the two outcomes. Mutation: return `first` instead of `upper` in the fast path → the lower-case
    // letters give '#' → RED.
    @Test
    fun indexLetter_asciiFastPathMatchesTheGeneralPath() {
        val printable = (0x21..0x7E).map { it.toChar() }
        fun reference(c: Char): Char = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            .uppercase(Locale.ROOT)
            .first()
            .let { if (it in 'A'..'Z') it else '#' }
        assertEquals("control", ('A'..'Z').toSet() + '#', printable.map(::reference).toSet())
        printable.forEach { c ->
            assertEquals("for \"${c}x\"", reference(c), indexLetter("${c}x"))
        }
    }

    // Ruling 1. The input order is neither the expected order nor binary order. Expected: the '#' bucket first (digits
    // before Greek), then A ("apple" before "Apricot": case ignored), then E (accents ignored: Émile, Emma, Eva), F, Z.
    // Mutations: (a) drop the section rank → "Ωmega" sorts after Latin → RED; (b) replace the collator with
    // String.CASE_INSENSITIVE_ORDER → "Émile" ('é' > 'e') sorts after "Eva" → RED; (c) compare names exactly →
    // "Apricot" before "apple" → RED.
    @Test
    fun sortedByLibraryName_groupsBySectionThenIgnoresCaseAndAccents() {
        val input = listOf("Zebra", "Eva", "Ωmega", "Apricot", "Émile", "1999", "Fox", "apple", "Emma")
        val expected = listOf("1999", "Ωmega", "apple", "Apricot", "Émile", "Emma", "Eva", "Fox", "Zebra")
        assertEquals(expected, input.sortedByLibraryName(naturalOrder()) { it })
    }

    // Ruling 4: a primary tie goes to the caller's tie-break. Mutation: ignore tieBreak (the stable sort then keeps the
    // input order ["b", "B"]) → RED.
    @Test
    fun sortedByLibraryName_breaksPrimaryTiesWithTheTieBreak() {
        assertEquals(listOf("B", "b"), listOf("b", "B").sortedByLibraryName(naturalOrder()) { it })
    }

    // The uncached comparator gives the same order. Mutation: drop its section rank → "Ωmega" last → RED.
    @Test
    fun libraryNameComparator_matchesSortedByLibraryName() {
        val input = listOf("Zebra", "Eva", "Ωmega", "Apricot", "Émile", "1999", "Fox", "apple", "Emma")
        assertEquals(
            input.sortedByLibraryName(naturalOrder()) { it },
            input.sortedWith(libraryNameComparator<String> { it }.thenBy { it }),
        )
    }
}
