@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui.customtags

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.TagChipData
import com.rolla.musicplayer.core.designsystem.component.TagChipLine
import com.rolla.musicplayer.core.designsystem.component.TagChipStyle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.SongCustomTags

/** Test tag on [SongCustomTagChips]' line. */
const val SONG_TAG_CHIPS_TAG = "song_tag_chips"

/**
 * A song's custom tags as one [TagChipLine] (custom tags phase 4, spec §6 calls 9–10): its tags as Tonal chips, then
 * its subtags as Outline chips, each list in the order given, on at most [maxLines] lines ending in "+N" when they do
 * not fit, start-aligned or [centred]. The line is one node for TalkBack, described "Tags: A, B. Subtags: C." (an empty
 * part is left out); the chips' own labels are cleared from semantics. Callers show it only when the song has tags.
 */
@Composable
fun SongCustomTagChips(
    tags: SongCustomTags,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    centred: Boolean = false,
) {
    val chips = remember(tags) {
        tags.tags.map { TagChipData(label = it.name, style = TagChipStyle.Tonal) } +
            tags.subTags.map { TagChipData(label = it.name, style = TagChipStyle.Outline) }
    }
    val description = remember(tags) { songCustomTagsDescription(tags) }
    TagChipLine(
        chips = chips,
        modifier = modifier
            .testTag(SONG_TAG_CHIPS_TAG)
            .clearAndSetSemantics { contentDescription = description },
        maxLines = maxLines,
        centred = centred,
    )
}

/** "Tags: A, B. Subtags: C.", leaving out a part whose list is empty. */
internal fun songCustomTagsDescription(tags: SongCustomTags): String = listOfNotNull(
    tags.tags.describedAs("Tags"),
    tags.subTags.describedAs("Subtags"),
).joinToString(separator = " ")

private fun List<CustomTag>.describedAs(heading: String): String? =
    if (isEmpty()) null else joinToString(prefix = "$heading: ", separator = ", ", postfix = ".") { it.name }

@Suppress("UnusedPrivateMember")
@Preview(name = "Song Custom Tag Chips - Light")
@Preview(name = "Song Custom Tag Chips - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSongCustomTagChips() {
    RollaMusicPlayerTheme {
        SongCustomTagChips(
            tags = SongCustomTags(
                tags = listOf(
                    CustomTag(kind = CustomTagKind.TAG, id = 1L, name = "Techno"),
                    CustomTag(kind = CustomTagKind.TAG, id = 2L, name = "Peak time"),
                ),
                subTags = listOf(CustomTag(kind = CustomTagKind.SUBTAG, id = 1L, name = "Hard")),
            ),
        )
    }
}
