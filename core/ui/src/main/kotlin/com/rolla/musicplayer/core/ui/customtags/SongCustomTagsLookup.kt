package com.rolla.musicplayer.core.ui.customtags

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.rolla.musicplayer.core.model.SongCustomTags

/**
 * Looks up a song's known custom tags by its ID (custom tags phase 4): null when the song carries none. The app
 * provides one for the whole tree through [LocalSongCustomTags]; track rows and Now Playing read it.
 */
@Stable
fun interface SongCustomTagsLookup {
    fun tagsFor(songId: String): SongCustomTags?
}

/**
 * The [SongCustomTagsLookup] every track list and Now Playing read their chips from. `:app` provides it once at the
 * content root, reading the tags map's State inside [SongCustomTagsLookup.tagsFor], so a change of the map recomposes
 * only the composables that asked. The default knows no tags, so a screen outside the app (a test, a preview) shows
 * no chips.
 */
val LocalSongCustomTags = staticCompositionLocalOf { SongCustomTagsLookup { null } }
