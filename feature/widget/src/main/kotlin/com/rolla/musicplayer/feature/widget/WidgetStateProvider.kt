package com.rolla.musicplayer.feature.widget

import com.rolla.musicplayer.core.media.PlaybackStateHolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget-side read access to the current playback state.
 *
 * The **playback service** (owned by `:core:media`, audio-engineer) is the single writer of
 * playback state: it pushes every update -- track change, play/pause, throttled position ticks --
 * into [PlaybackStateHolder]. `:core:media` must never depend on `:feature:widget`, so this is not
 * a second holder the service writes into; it is a read-only **adapter** on the widget side that
 * snapshots [PlaybackStateHolder]'s `StateFlow`s into a [MusicWidgetState] the Glance UI can render.
 *
 * Calling `updateAll`/`update` on the Glance widget whenever the underlying state changes (so the
 * rendered widget actually refreshes) is push-update wiring that lands in a later prompt -- this
 * interface only defines the read side.
 */
interface WidgetStateProvider {
    /** A snapshot of the current playback state, suitable for a single Glance render pass. */
    fun current(): MusicWidgetState
}

@Singleton
class WidgetStateProviderImpl @Inject constructor(
    private val playbackStateHolder: PlaybackStateHolder,
) : WidgetStateProvider {

    override fun current(): MusicWidgetState {
        val song = playbackStateHolder.currentSong.value ?: return MusicWidgetState()
        return MusicWidgetState(
            title = song.title,
            artist = song.artist,
            isPlaying = playbackStateHolder.isPlaying.value,
            positionMs = playbackStateHolder.positionMs.value,
            durationMs = playbackStateHolder.durationMs.value,
            artworkPath = song.artworkUri.ifBlank { null },
        )
    }
}
