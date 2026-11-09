package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.rolla.musicplayer.core.model.SavedPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Serves Media3's playback resumption (queue-restore spec §7.6, ruling R1). Media3 calls it for any Play on an empty
 * player: from our own MediaController (the app, the widget), a media button, or the System UI. It then sets the
 * returned items and plays. It is the service's `MediaLibrarySession.Callback`, and it overrides no library method:
 * Media3's defaults refuse every browse, and Media3 itself answers the System UI's recent root (panel-resume spec §3).
 *
 * Media3 1.11 adds `isForPlayback`. `true` is a Play (our controllers, a media button, the panel card's Play) and
 * resumes as above. `false` is a metadata request: System UI's card, or any browser reading the recent root's
 * children (panel-resume spec §5.3). Media3 serves the recent ROOT only to System UI, but answers its CHILDREN (the
 * saved current track's title, artist, album and artwork URI and, for the start item, the placeholder's picture bytes)
 * for any connected browser (spec §10). It reads the saved queue through [RestoreCoordinator.peekSaved] and changes
 * nothing, so it never takes the in-app restore, arms the order, touches the player or calls [onNothingToResume]. Its
 * items are [asPlayableLeaf]s, because Media3 wraps the current one in a library result, which rejects an item without
 * the browsable and playable flags.
 *
 * The metadata request's START item (the one Media3 describes) also carries `artworkData` from [startItemArtwork]
 * (ruling P12): Media3's legacy browse path, which serves the stock-Android resume card, decodes only those bytes,
 * never the artwork uri. By user decision (ruling P14) the service's provider always answers the white-note
 * placeholder, never the track's own picture, so a user's picture never leaves the app through the recent root. A
 * provider failure leaves the item without bytes. The playback branch's items keep only the uri; the player never
 * holds picture bytes.
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
    /** The resume card's bytes for a song's artwork value, or null to attach none (P14: always the placeholder). */
    private val startItemArtwork: suspend (artworkUri: String) -> ByteArray?,
    private val onNothingToResume: () -> Unit,
) : MediaLibrarySession.Callback {

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaItemsWithStartPosition> = if (isForPlayback) {
        savedQueueFuture(
            onNothing = onNothingToResume,
            load = coordinator::savedForResumption,
            // The player's items keep their metadata: Media3 sets these on the player as they are.
            toResultItem = { it },
            startArtwork = null,
        ) { saved ->
            // Accepted residual window: the order is armed here, before Media3 sets the returned items.
            // Also accepted (inherent to Media3 resumption): an app queue that arrives while
            // savedForResumption is suspended is replaced anyway when Media3's onSuccess sets these items.
            mediaSession.player.prepareRestore(saved, restoredOrder)
        }
    } else {
        // A metadata request, read only (panel-resume spec §5.3): System UI's card, or any connected browser reading
        // the recent root's children, which Media3 answers for every browser (spec §10).
        savedQueueFuture(
            onNothing = {},
            load = coordinator::peekSaved,
            toResultItem = MediaItem::asPlayableLeaf,
            startArtwork = startItemArtwork,
        ) {}
    }

    /**
     * Resolves the saved queue as Media3's resumption result, each item mapped by [toResultItem], the start item with
     * [startArtwork]'s bytes when given. [load] and [beforeReturn] run on [mainDispatcher] before the future resolves;
     * the true path's `mediaSession.player` read relies on that under Media3 1.11's application-thread check.
     */
    private fun savedQueueFuture(
        onNothing: () -> Unit,
        load: suspend () -> SavedPlayback?,
        toResultItem: (MediaItem) -> MediaItem,
        startArtwork: (suspend (String) -> ByteArray?)?,
        beforeReturn: (SavedPlayback) -> Unit,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaItemsWithStartPosition>()
        scope.launch(mainDispatcher) {
            try {
                val saved = load()
                if (saved == null) {
                    onNothing()
                    future.setException(IllegalStateException("No saved queue to resume"))
                } else {
                    val items = resultItems(saved, toResultItem, startArtwork)
                    beforeReturn(saved)
                    future.set(MediaItemsWithStartPosition(items, saved.state.currentIndex, saved.state.positionMs))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Any load or mapping failure must fail the future, never leave Media3 waiting.
                onNothing()
                future.setException(e)
            }
        }.invokeOnCompletion { cause ->
            // Also covers a scope cancelled before the body ever ran: the future never stays pending.
            if (cause != null) future.cancel(false)
        }
        return future
    }
}

/**
 * [saved]'s songs as result items, each mapped by [toResultItem]; the start item (`currentIndex`) also carries
 * [startArtwork]'s bytes when [startArtwork] is given and answers.
 */
private suspend fun resultItems(
    saved: SavedPlayback,
    toResultItem: (MediaItem) -> MediaItem,
    startArtwork: (suspend (String) -> ByteArray?)?,
): List<MediaItem> {
    val items = saved.songs.map { toResultItem(it.toMediaItem()) }.toMutableList()
    val start = saved.state.currentIndex
    if (startArtwork != null && start in items.indices) {
        artworkOrNull(startArtwork, saved.songs[start].artworkUri)?.let { items[start] = items[start].withArtwork(it) }
    }
    return items
}

/**
 * [provider]'s bytes for [artworkUri], or null when it throws anything but cancellation: a picture failure must not
 * fail the whole metadata request, so the start item still resolves with its title and artist. Errors propagate.
 */
private suspend fun artworkOrNull(provider: suspend (String) -> ByteArray?, artworkUri: String): ByteArray? =
    try {
        provider(artworkUri)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "Resume-card artwork failed; describing the track without it", e)
        null
    }

private const val TAG = "PlaybackSessionCallback"

/** This item with [bytes] as its front-cover `artworkData`; the artwork uri stays. */
private fun MediaItem.withArtwork(bytes: ByteArray): MediaItem =
    buildUpon()
        .setMediaMetadata(
            mediaMetadata.buildUpon().setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build(),
        )
        .build()
