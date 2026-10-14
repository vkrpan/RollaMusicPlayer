package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.Player
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.SavedPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restores the saved queue once per service start (queue-restore spec §7.1). It reads the database once
 * ([firstLoad]); the in-app restore ([restoreInto]) and a resumption arriving while loading ([savedForResumption])
 * share that read. [outcome] is read and written only on [mainDispatcher]. [onSettled] runs once, when it leaves
 * [Outcome.LOADING].
 */
internal class RestoreCoordinator(
    private val repository: PlaybackStateRepository,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val onSettled: () -> Unit,
) {
    enum class Outcome { LOADING, APPLIED, DISCARDED, HANDED_TO_RESUMPTION }

    var outcome: Outcome = Outcome.LOADING
        private set

    private val firstLoad: Deferred<SavedPlayback?> = scope.async { loadOrNull() }

    /** The in-app restore (spec §7.2): applies paused unless a queue arrived first or a resumption took it. */
    fun restoreInto(player: Player, restoredOrder: RestoredShuffleOrder, holder: PlaybackStateHolder): Job =
        scope.launch {
            val saved = firstLoad.await()
            withContext(mainDispatcher) {
                if (outcome != Outcome.LOADING) return@withContext
                outcome = if (saved == null || player.mediaItemCount > 0) {
                    Outcome.DISCARDED
                } else {
                    // No suspension between the arm and setMediaItems: the armed order must meet these items.
                    player.prepareRestore(saved, restoredOrder)
                    player.setMediaItems(
                        saved.songs.map { it.toMediaItem() },
                        saved.state.currentIndex,
                        saved.state.positionMs,
                    )
                    player.prepare()
                    holder.setPositionMs(saved.state.positionMs)
                    Outcome.APPLIED
                }
                onSettled()
            }
        }

    /**
     * What [PlaybackSessionCallback.onPlaybackResumption] resumes. While loading it takes the first load and the
     * in-app restore stands down; once settled it reads the database again (ruling R6). Call it on [mainDispatcher].
     */
    suspend fun savedForResumption(): SavedPlayback? {
        if (outcome != Outcome.LOADING) return loadOrNull()
        outcome = Outcome.HANDED_TO_RESUMPTION
        onSettled()
        return firstLoad.await()
    }

    /** Ruling P1: a failed load is logged and treated as nothing saved; it does not clear a save that may be good. */
    private suspend fun loadOrNull(): SavedPlayback? = try {
        repository.load()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Any load or mapping failure must settle the restore as "nothing saved", never take the service down.
        Log.e(TAG, "Loading the saved playback queue failed", e)
        null
    }

    private companion object {
        const val TAG = "RestoreCoordinator"
    }
}

/** Ruling R3: arm the saved order and set the modes BEFORE the items, so the saved order lands after the enforcer's. */
internal fun Player.prepareRestore(saved: SavedPlayback, restoredOrder: RestoredShuffleOrder) {
    restoredOrder.arm(saved.shuffleOrder)
    repeatMode = saved.state.repeatMode.toPlayerRepeatMode()
    shuffleModeEnabled = saved.state.shuffleEnabled
}
