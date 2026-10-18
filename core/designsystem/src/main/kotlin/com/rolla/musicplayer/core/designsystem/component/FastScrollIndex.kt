// Spec §6 names this file for the rail's index logic, not for its one class: the section model plus its functions.
@file:Suppress("MatchingDeclarationName")

package com.rolla.musicplayer.core.designsystem.component

import com.rolla.musicplayer.core.common.text.NON_LETTER_SECTION
import com.rolla.musicplayer.core.common.text.indexLetter
import com.rolla.musicplayer.core.common.text.librarySectionRank

/**
 * One label of the A–Z rail (spec §10). [firstIndex] is the list index a jump scrolls to. For a [populated] section
 * that is its first row. For an empty one it is the next populated section's first row, or the previous one's at the
 * end of the alphabet.
 */
data class FastScrollSection(val label: String, val firstIndex: Int, val populated: Boolean)

/** The rail's labels: "#", then A–Z. Every one is shown when it fits, populated or not. */
val FastScrollLabels: List<String> = listOf(NON_LETTER_SECTION.toString()) + ('A'..'Z').map(Char::toString)

/**
 * The rail's sections for [keys], each item's display name in list order. Returns no sections for no keys, which
 * hides the rail.
 *
 * **Precondition, required for correctness:** [keys] are in the library order (`sortedByLibraryName`), whose first
 * sort key is `librarySectionRank(indexLetter(key))`. Each label's first row is then a lower bound, found by binary
 * search: about 27 × log₂N [indexLetter] calls instead of N, because this runs on the main thread inside the tabs'
 * `remember`. Keys out of that order give wrong first rows and populated flags, not only split sections.
 */
fun buildFastScrollSections(keys: List<String>): List<FastScrollSection> {
    if (keys.isEmpty()) return emptyList()
    val firstIndices = FastScrollLabels.map { label ->
        val rank = librarySectionRank(label.single())
        val index = firstIndexWithRankAtLeast(keys, rank)
        index.takeIf { it < keys.size && sectionRankOf(keys[it]) == rank }
    }
    return FastScrollLabels.mapIndexed { position, label ->
        val own = firstIndices[position]
        val target = own
            ?: firstIndices.subList(position + 1, firstIndices.size).firstNotNullOfOrNull { it }
            ?: firstIndices.subList(0, position).filterNotNull().last()
        FastScrollSection(label = label, firstIndex = target, populated = own != null)
    }
}

/** The first index of [keys] whose section rank is at least [rank], or `keys.size` if none (a lower bound). */
private fun firstIndexWithRankAtLeast(keys: List<String>, rank: Int): Int {
    var low = 0
    var high = keys.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (sectionRankOf(keys[mid]) < rank) low = mid + 1 else high = mid
    }
    return low
}

private fun sectionRankOf(key: String): Int = librarySectionRank(indexLetter(key))

/**
 * The smallest step k for which every k-th of [labelCount] labels fits in [slots] letter slots (spec §10). With fewer
 * than one slot only the first label shows.
 */
fun fastScrollLabelStep(slots: Int, labelCount: Int = FastScrollLabels.size): Int {
    if (slots < 1) return labelCount
    var step = 1
    while ((labelCount + step - 1) / step > slots) step++
    return step
}

/**
 * The section under a touch at [fraction] of the rail's height (0 is the top, 1 the bottom). Every label owns an equal
 * slot, shown or dropped, so a drag reaches all of them. Out-of-range fractions clamp to the ends.
 */
fun fastScrollSectionAt(sections: List<FastScrollSection>, fraction: Float): FastScrollSection =
    sections[(fraction * sections.size).toInt().coerceIn(0, sections.lastIndex)]
