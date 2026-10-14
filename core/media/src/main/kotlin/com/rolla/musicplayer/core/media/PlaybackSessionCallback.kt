package com.rolla.musicplayer.core.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Serves Media3's playback resumption (queue-restore spec §7.6, ruling R1). Media3 1.4.1 calls it for any Play on an
 * empty player: from our own MediaController (the app, the widget), a media button, or the System UI. It then sets
 * the returned items and plays.
 *
 * [onNothingToResume] runs on [mainDispatcher] just before the future fails because nothing could be resumed (ruling
 * P6: a media-button start must still keep the foreground-start contract).
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackSessionCallback(
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val coordinator: RestoreCoordinator,
    private val restoredOrder: RestoredShuffleOrder,
    private val onNothingToResume: () -> Unit,
) : MediaSession.Callback {

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaItemsWithStartPosition>()
        scope.launch(mainDispatcher) {
            try {
                val saved = coordinator.savedForResumption()
                if (saved == null) {
                    onNothingToResume()
                    future.setException(IllegalStateException("No saved queue to resume"))
                } else {
                    // Accepted residual window: the order is armed here, before Media3 sets the returned items.
                    // Also accepted (inherent to Media3 resumption): an app queue that arrives while
                    // savedForResumption is suspended is replaced anyway when Media3's onSuccess sets these items.
                    mediaSession.player.prepareRestore(saved, restoredOrder)
                    future.set(
                        MediaItemsWithStartPosition(
                            saved.songs.map { it.toMediaItem() },
                            saved.state.currentIndex,
                            saved.state.positionMs,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Any load or mapping failure must fail the future, never leave Media3 waiting.
                onNothingToResume()
                future.setException(e)
            }
        }.invokeOnCompletion { cause ->
            // Also covers a scope cancelled before the body ever ran: the future never stays pending.
            if (cause != null) future.cancel(false)
        }
        return future
    }
}
