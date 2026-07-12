package com.rolla.musicplayer.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.rolla.musicplayer.core.media.PlaybackUpdateHook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The widget's implementation of `:core:media`'s [PlaybackUpdateHook] seam, contributed into the
 * service's hook set via `@Binds @IntoSet` (see [WidgetModule]). [PlaybackService] fires it -- off
 * the player callback thread, exception-contained on its side -- on track change, play/pause, and
 * the ~1s position tick while playing; each fire schedules a [MusicWidget] re-render.
 *
 * [WidgetStateProvider] needs no update call here: it is a live adapter over
 * `PlaybackStateHolder`, which the service updates BEFORE firing hooks (the seam's documented
 * ordering guarantee) -- so the render this hook schedules always snapshots post-change state.
 *
 * ### Coalescing
 * `updateAll` re-renders every widget instance through RemoteViews -- a burst of fires (track
 * change + isPlaying + a tick landing together) must not queue a render per fire. Fires go through
 * a [MutableSharedFlow] with a 1-slot buffer ([BufferOverflow.DROP_OLDEST]) consumed by a single
 * collector: at most ONE render is queued behind the in-flight one, and extra fires during a
 * render collapse into that single slot. Skipped intermediate fires lose nothing -- the render
 * always snapshots the CURRENT state, so latest-state-wins by construction.
 *
 * A failed render is dropped (house best-effort idiom; the next fire repaints);
 * [CancellationException] is rethrown so the collector's structured concurrency stays intact.
 */
@Singleton
class WidgetPlaybackUpdateHook @Inject constructor(
    @ApplicationContext private val context: Context,
    @WidgetIoDispatcher ioDispatcher: CoroutineDispatcher,
) : PlaybackUpdateHook {

    /**
     * Test seam (sdkIntProvider precedent): the real render is Glance's suspend
     * [updateAll], which needs an Android runtime; JVM tests swap this to count/gate renders.
     */
    internal var renderWidget: suspend () -> Unit = { MusicWidget().updateAll(context) }

    // replay = 1 (not extraBufferCapacity): a SharedFlow with replay 0 DROPS emissions that land
    // before the collector coroutine has started -- a fire racing construction would be lost. The
    // single replay slot retains the newest pending fire for the (one, long-lived) collector while
    // still collapsing bursts into at most one queued render.
    private val refreshRequests = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // Process-lifetime singleton: the collector idles suspended between fires; no unbind
        // needed. The scope survives service restarts deliberately -- hooks can fire from any
        // service incarnation.
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            refreshRequests.collect {
                try {
                    renderWidget()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                    // Dropped: a failed render self-corrects on the next fire.
                }
            }
        }
    }

    override fun onPlaybackStateChanged() {
        // tryEmit never suspends/blocks -- the service-side dispatch returns immediately.
        refreshRequests.tryEmit(Unit)
    }
}
