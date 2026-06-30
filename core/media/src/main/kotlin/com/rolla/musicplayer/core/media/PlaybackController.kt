package com.rolla.musicplayer.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val controller: MediaController?
        get() = controllerFuture?.let { if (it.isDone && !it.isCancelled) it.get() else null }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture ?: return)
        controllerFuture = null
    }

    fun play(song: Song) {
        val item = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build(),
            )
            .build()
        controller?.run {
            setMediaItem(item)
            prepare()
            play()
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
}
