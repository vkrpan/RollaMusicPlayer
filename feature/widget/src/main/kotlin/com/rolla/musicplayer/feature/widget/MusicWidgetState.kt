package com.rolla.musicplayer.feature.widget

/**
 * Immutable snapshot of everything the home-screen widget renders: the current song's
 * title/artist, transport state, playback position, and a *local* artwork reference.
 *
 * [artworkPath] is a local `content://`/file path string only -- the widget is fully offline and
 * never resolves artwork from a URL. It is null when there is no current song or no local art.
 *
 * Design note: this is a plain Kotlin `data class`, deliberately **not** annotated with
 * `androidx.compose.runtime.Immutable`. Two reasons: (1) every stored property is a `String`,
 * `Boolean`, or `Long` (or a nullable of those) -- types the Compose compiler's stability
 * inference already treats as stable by default, so `@Immutable` would be redundant; (2) this
 * module does not declare `androidx.compose.runtime` as a direct dependency (Glance pulls it in
 * transitively via `glance-appwidget`), and reaching for an annotation from an undeclared
 * transitive dependency is fragile -- if that transitive edge ever changes, this file would break
 * for a reason unrelated to its own code. If a later prompt's Glance UI profiling shows a real
 * recomposition-skipping need, add `androidx.compose.runtime` as an explicit dependency first.
 */
data class MusicWidgetState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artworkPath: String? = null,
) {
    /** Playback progress in `0f..1f`. `0f` whenever [durationMs] is not a positive duration. */
    val progress: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(MIN_PROGRESS, MAX_PROGRESS)
        } else {
            MIN_PROGRESS
        }

    private companion object {
        const val MIN_PROGRESS = 0f
        const val MAX_PROGRESS = 1f
    }
}
