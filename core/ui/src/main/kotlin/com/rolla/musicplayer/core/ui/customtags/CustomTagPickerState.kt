package com.rolla.musicplayer.core.ui.customtags

import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import java.text.Normalizer
import java.util.Locale

/**
 * What the custom tag picker shows: both lists, the ticked IDs, the open page and each page's search text. [tags] and
 * [subTags] arrive in library order from the repository. [selected] holds known IDs only; the owner keeps any unknown
 * IDs from the file aside, so they never appear in the picker.
 */
@Immutable
data class CustomTagPickerState(
    val tags: List<CustomTag>,
    val subTags: List<CustomTag>,
    val selected: CustomTagIds,
    val page: CustomTagKind = CustomTagKind.TAG,
    val tagQuery: String = "",
    val subTagQuery: String = "",
)

/**
 * The entries of [all] whose name contains [query], ignoring case and accents ("emile" finds "Émile"), in [all]'s
 * order. A blank query, after trimming, returns [all].
 */
fun filterCustomTags(all: List<CustomTag>, query: String): List<CustomTag> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return all
    val needle = foldForSearch(trimmed)
    return all.filter { foldForSearch(it.name).contains(needle) }
}

/** These IDs with [id] added to the [kind] list, or removed from it when it is already there. The other list stays. */
fun CustomTagIds.toggled(kind: CustomTagKind, id: Long): CustomTagIds = when (kind) {
    CustomTagKind.TAG -> copy(tagIds = if (id in tagIds) tagIds - id else tagIds + id)
    CustomTagKind.SUBTAG -> copy(subTagIds = if (id in subTagIds) subTagIds - id else subTagIds + id)
}

/** The names of the entries of [all] whose ID is in [ids], in [all]'s (library) order. IDs not in [all] are skipped. */
fun selectedNames(all: List<CustomTag>, ids: Set<Long>): List<String> = all.filter { it.id in ids }.map { it.name }

// Case and accent folding for the search match: NFD splits "É" into "E" and a combining mark, the marks are dropped,
// and the root-locale lowercase removes case. It approximates the library collator's primary strength for `contains`.
private fun foldForSearch(text: String): String =
    COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
