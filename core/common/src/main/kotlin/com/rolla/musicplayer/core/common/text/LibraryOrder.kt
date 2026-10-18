package com.rolla.musicplayer.core.common.text

import java.text.CollationKey
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The A–Z rail's section for names that do not start with a letter A–Z. */
const val NON_LETTER_SECTION: Char = '#'

/**
 * The rail section a library name belongs to (spec §10). It is the name's first character after leading whitespace,
 * with diacritics stripped (NFD, base character kept) and uppercased. 'A'–'Z' map to themselves, and anything else
 * maps to [NON_LETTER_SECTION]: "Émile" → 'E', "ñu" → 'N', "Øl" → '#' (Ø has no decomposition), "1999" → '#', "" → '#'.
 */
fun indexLetter(name: String): Char {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return NON_LETTER_SECTION
    val first = trimmed[0]
    val letter = if (first.code < ASCII_LIMIT) {
        // ASCII fast path, exactly equivalent to the general one: NFD leaves ASCII unchanged, and the root-locale
        // uppercase of an ASCII character is its uppercaseChar. It skips the substring, normalisation and uppercase.
        first.uppercaseChar()
    } else {
        val firstCharacter = trimmed.substring(0, trimmed.offsetByCodePoints(0, 1))
        Normalizer.normalize(firstCharacter, Normalizer.Form.NFD).uppercase(Locale.ROOT).first()
    }
    return if (letter in 'A'..'Z') letter else NON_LETTER_SECTION
}

/**
 * This list in the library order every Home list uses (Phase 5 ruling 1). It sorts by [indexLetter]'s section first,
 * '#' and then A–Z, so each rail section is one contiguous run. Then it sorts by root-locale collation at primary
 * strength, which ignores case and accents ("Émile" sorts between "Emil" and "Emma"). Then [tieBreak] decides.
 * Collation keys are computed once a name, so a large library sorts in a few milliseconds. Call it off the main thread.
 */
fun <T> List<T>.sortedByLibraryName(tieBreak: Comparator<in T>, name: (T) -> String): List<T> {
    if (size < 2) return toList()
    val collator = libraryCollator()
    return map { item ->
        val itemName = name(item)
        LibrarySortEntry(librarySectionRank(indexLetter(itemName)), collator.getCollationKey(itemName), item)
    }
        .sortedWith { a, b ->
            val bySection = a.sectionRank.compareTo(b.sectionRank)
            val byName = if (bySection != 0) bySection else a.key.compareTo(b.key)
            if (byName != 0) byName else tieBreak.compare(a.item, b.item)
        }
        .map { it.item }
}

/**
 * The same order as [sortedByLibraryName], without cached keys. It is for tie-breaks (the Folders path) and short
 * lists. Each call makes its own collator, because a `Collator` must not be shared between threads.
 */
fun <T> libraryNameComparator(name: (T) -> String): Comparator<T> {
    val collator = libraryCollator()
    return Comparator { a, b ->
        val nameA = name(a)
        val nameB = name(b)
        val bySection = librarySectionRank(indexLetter(nameA)).compareTo(librarySectionRank(indexLetter(nameB)))
        if (bySection != 0) bySection else collator.compare(nameA, nameB)
    }
}

/**
 * The library order's section rank: the first sort key of [sortedByLibraryName] and [libraryNameComparator]. [letter]
 * is an [indexLetter] result: [NON_LETTER_SECTION] ranks 0 and 'A'..'Z' rank 1..26, so '#' sorts first. The A–Z
 * rail's section index binary-searches a library-ordered list by this rank, so the two must agree; both use this one.
 */
fun librarySectionRank(letter: Char): Int = if (letter == NON_LETTER_SECTION) 0 else letter - 'A' + 1

private fun libraryCollator(): Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

private const val ASCII_LIMIT = 0x80

private class LibrarySortEntry<T>(val sectionRank: Int, val key: CollationKey, val item: T)
