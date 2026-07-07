package com.rolla.musicplayer.core.media

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackStateHolder: PlaybackStateHolder,
) {
    val audioSessionId: StateFlow<Int> = playbackStateHolder.audioSessionId
    val currentSong: StateFlow<Song?> = playbackStateHolder.currentSong
    val isPlaying: StateFlow<Boolean> = playbackStateHolder.isPlaying
    val positionMs: StateFlow<Long> = playbackStateHolder.positionMs
    val durationMs: StateFlow<Long> = playbackStateHolder.durationMs
    val shuffleMode: StateFlow<ShuffleMode> = playbackStateHolder.shuffleMode
    val repeatMode: StateFlow<RepeatMode> = playbackStateHolder.repeatMode

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val controller: MediaController?
        get() = controllerFuture?.let { if (it.isDone && !it.isCancelled) it.get() else null }

    fun connect() {
        val existing = controllerFuture
        // Return if there's an in-flight or successfully completed future.
        // Only reconnect if the previous future was cancelled (e.g. service crash).
        if (existing != null && !existing.isCancelled) return
        existing?.let { MediaController.releaseFuture(it) }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture ?: return)
        controllerFuture = null
    }

    fun play(song: Song) {
        val item = buildMediaItem(song)
        val c = controller
        if (c != null) {
            startPlayback(c, item)
        } else {
            // Future not yet resolved — enqueue command; last tap wins if multiple queued.
            controllerFuture?.addListener(
                { controller?.let { startPlayback(it, item) } },
                { command -> command.run() },
            )
        }
    }

    fun playAll(songs: List<Song>, startIndex: Int = 0) {
        val items = songs.map(::buildMediaItem)
        val c = controller
        if (c != null) {
            startQueuePlayback(c, items, startIndex)
        } else {
            // Future not yet resolved — enqueue command; last tap wins if multiple queued.
            controllerFuture?.addListener(
                { controller?.let { startQueuePlayback(it, items, startIndex) } },
                { command -> command.run() },
            )
        }
    }

    private fun buildMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri.ifEmpty { null }?.toUri())
                    .build(),
            )
            .build()

    private fun startPlayback(c: MediaController, item: MediaItem) {
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    private fun startQueuePlayback(c: MediaController, items: List<MediaItem>, startIndex: Int) {
        c.setMediaItems(items, startIndex, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    /**
     * Refreshes [song]'s metadata everywhere playback knows about it, after its tags were edited:
     * every queue entry whose media id matches is replaced via [MediaController.replaceMediaItem]
     * -- the rebuilt item keeps the same uri, so Media3 updates it in place without interrupting
     * playback -- and, if [song] is the current track, [PlaybackStateHolder.currentSong] is
     * refreshed directly so the mini-player/now-playing UI re-renders the new tags immediately
     * (the service's own transition callback echoes the same values shortly after). A no-op when
     * no controller is connected and the song isn't current.
     */
    fun updateSongMetadata(song: Song) {
        if (playbackStateHolder.currentSong.value?.id == song.id) {
            playbackStateHolder.setCurrentSong(song)
        }
        val c = controller ?: return
        val item = buildMediaItem(song)
        for (index in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(index).mediaId == song.id) {
                c.replaceMediaItem(index, item)
            }
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setShuffle(mode: ShuffleMode) {
        controller?.shuffleModeEnabled = (mode == ShuffleMode.ON)
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
