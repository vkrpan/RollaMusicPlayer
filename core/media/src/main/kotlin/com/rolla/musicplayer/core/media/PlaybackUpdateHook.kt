package com.rolla.musicplayer.core.media

/**
 * The service -> feature push-update seam for playback state changes.
 *
 * `:core:media` owns [PlaybackService] and must never depend on any `:feature:*` module (see the
 * module dependency rules in the project's CLAUDE.md), so it cannot call into `:feature:widget`
 * directly to tell it to refresh when playback state changes. Instead, `:feature:widget` implements
 * this interface with its widget updater and contributes that implementation into the
 * [Set]-of-[PlaybackUpdateHook] Hilt multibinding declared in [PlaybackUpdateModule] -- the
 * dependency arrow stays feature -> core (`:feature:widget` depends on `:core:media`), never the
 * other way around.
 *
 * [PlaybackService] injects the full `Set<PlaybackUpdateHook>` (via [PlaybackUpdateDispatcher]) and
 * invokes every entry after each observable playback state change -- see [PlaybackUpdateDispatcher]
 * for exactly when and how. The set is legally empty when no feature contributes an implementation,
 * which is why the multibinding is declared with `@Multibinds` in [PlaybackUpdateModule] rather than
 * assuming at least one `@IntoSet` contribution exists.
 *
 * Invoked after observable playback state changes; implementations must be fast and
 * non-throwing-critical: [PlaybackUpdateDispatcher] contains any exception a hook throws (other than
 * [kotlinx.coroutines.CancellationException]) so a broken hook can never crash [PlaybackService], but
 * a slow or blocking implementation still delays whatever else that dispatch's coroutine does.
 */
fun interface PlaybackUpdateHook {
    fun onPlaybackStateChanged()
}
