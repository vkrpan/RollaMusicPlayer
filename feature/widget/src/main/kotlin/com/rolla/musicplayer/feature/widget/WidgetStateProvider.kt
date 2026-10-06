package com.rolla.musicplayer.feature.widget

import com.rolla.musicplayer.core.media.PlaybackStateHolder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget-side read access to the current playback state.
 *
 * The **playback service** (owned by `:core:media`, audio-engineer) is the single writer of
 * playback state: it pushes every update -- track change, play/pause, throttled position ticks --
 * into [PlaybackStateHolder]. `:core:media` must never depend on `:feature:widget`, so this is not
 * a second holder the service writes into; it is a read-only **adapter** on the widget side that
 * maps [PlaybackStateHolder]'s `StateFlow`s into a [MusicWidgetState] the Glance UI can render.
 */
interface WidgetStateProvider {
    /** A snapshot of the current playback state -- the initial value for a Glance session. */
    fun current(): MusicWidgetState

    /**
     * The same mapping as [current], re-emitted whenever any underlying playback state changes.
     *
     * The Glance composition MUST observe this rather than render a one-off [current] snapshot:
     * `GlanceAppWidget.update`/`updateAll` do not re-run `provideGlance` while a session is alive,
     * and the service's 1s position tick keeps the session alive for as long as music plays -- a
     * snapshot taken before `provideContent` is therefore frozen for the whole playback session.
     */
    val states: Flow<MusicWidgetState>
}

@Singleton
class WidgetStateProviderImpl @Inject constructor(
    private val playbackStateHolder: PlaybackStateHolder,
) : WidgetStateProvider {

    override fun current(): MusicWidgetState = toWidgetState(
        song = playbackStateHolder.currentSong.value,
        isPlaying = playbackStateHolder.isPlaying.value,
        positionMs = playbackStateHolder.positionMs.value,
        durationMs = playbackStateHolder.durationMs.value,
    )

    override val states: Flow<MusicWidgetState> = combine(
        playbackStateHolder.currentSong,
        playbackStateHolder.isPlaying,
        playbackStateHolder.positionMs,
        playbackStateHolder.durationMs,
        ::toWidgetState,
    ).distinctUntilChanged()

    private fun toWidgetState(song: Song?, isPlaying: Boolean, positionMs: Long, durationMs: Long): MusicWidgetState {
        song ?: return MusicWidgetState()
        return MusicWidgetState(
            title = song.title,
            artist = song.artist,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            artworkPath = song.artworkUri.ifBlank { null },
        )
    }
}
