package com.rolla.musicplayer.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.rolla.musicplayer.core.media.PlaybackController

/**
 * The five widget control taps, each its own [ActionCallback] (Glance instantiates these itself
 * via reflection -- there is no constructor Hilt can inject into, hence [WidgetEntryPoint] rather
 * than `@Inject`; see that file's KDoc). Each callback resolves [PlaybackController] and
 * dispatches to the SAME service the notification/in-app controls use -- there is no second
 * player here, only calls onto the existing controller (`ui-style-guide.md` / widget-agent
 * charter: "thin mirror, not a second player").
 *
 * ### Post-action refresh -- read before touching cadence/semantics
 * This lands ahead of the service's own push-update hook (a later prompt makes
 * `PlaybackService` call `MusicWidget().updateAll(context)` on every track/play-pause/throttled
 * position change). Until then, every callback below calls [MusicWidget.update] on its own
 * [GlanceId] after acting, so THIS widget instance re-renders immediately and a tap is never
 * silently invisible -- but this is a best-effort fast path, not a source of truth:
 * [PlaybackController.togglePlayPause]/[PlaybackController.next]/etc. dispatch to the
 * [androidx.media3.session.MediaController] asynchronously (an IPC round trip to the session), so
 * the [WidgetStateProvider] snapshot that `update` renders immediately after can still reflect the
 * PRE-action state for one frame (most visible on play/pause, where the icon can briefly show the
 * old state) -- it self-corrects on the very next state-driven update once the service's listener
 * callback lands and pushes the real state. A synchronous delay/poll here to "wait" for the
 * controller to settle was deliberately rejected (ugly, and still racy against the IPC); the
 * chosen tradeoff is: render optimistically now, let the service's push-update hook (landing in
 * the next prompt) be the actual correction. This mirrors the same one-tick staleness every
 * `MediaController` command already has; it is not unique to the widget.
 *
 * ### Cold-start caveat
 * [PlaybackController.connect] resolves a [androidx.media3.session.MediaController] via an async
 * `buildAsync()` future. If the app process was fully dead (not just backgrounded) before this
 * tap -- e.g. after a reboot or force-stop -- the very first tap calls `connect()` and then
 * immediately calls e.g. `togglePlayPause()` in the same [onAction], before that future can
 * possibly have resolved; [PlaybackController.togglePlayPause] (and `next`/`previous`/`seekTo`)
 * silently no-op when the underlying controller is `null` (see their `controller?.let { }`
 * bodies in `:core:media`), so that specific first tap is a best-effort no-op and a second tap
 * (by which point `connect()`'s future has resolved) works normally. [PlaybackController.play]
 * avoids this for the "start a song" path by queuing onto `controllerFuture.addListener`, but
 * `togglePlayPause`/`next`/`previous`/`seekTo` do not expose that queuing today -- widening that
 * is a `:core:media` change (audio-engineer's module), out of `:feature:widget`'s ownership, and
 * is called out as a coordination item rather than patched here.
 */
private suspend fun withController(context: Context, glanceId: GlanceId, block: (PlaybackController) -> Unit) {
    val controller = WidgetEntryPoint.get(context).playbackController()
    controller.connect()
    block(controller)
    MusicWidget().update(context, glanceId)
}

/** [Previous][PlaybackController.previous] -- mirrors the notification/in-app "previous" action. */
class PreviousActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context, glanceId) { it.previous() }
    }
}

/** [Next][PlaybackController.next] -- mirrors the notification/in-app "next" action. */
class NextActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context, glanceId) { it.next() }
    }
}

/** [Toggle play/pause][PlaybackController.togglePlayPause]. */
class PlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withController(context, glanceId) { it.togglePlayPause() }
    }
}

/** Seeks 15s earlier, clamped to `0`. Reads the pre-tap position snapshot -- see the file KDoc. */
class SkipBack15ActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val state = WidgetEntryPoint.get(context).widgetStateProvider().current()
        val target = clampSeekPosition(state.positionMs, -SKIP_DELTA_MS, state.durationMs)
        withController(context, glanceId) { it.seekTo(target) }
    }
}

/** Seeks 15s later, clamped to the track's duration. Reads the pre-tap position snapshot. */
class SkipForward15ActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val state = WidgetEntryPoint.get(context).widgetStateProvider().current()
        val target = clampSeekPosition(state.positionMs, SKIP_DELTA_MS, state.durationMs)
        withController(context, glanceId) { it.seekTo(target) }
    }
}

private const val SKIP_DELTA_MS = 15_000L

/**
 * Applies a `±15s` (or any) [deltaMs] to [currentPositionMs] and clamps the result to a valid
 * seek target within `[0, durationMs]`. Pulled out as a pure function (no [PlaybackController]/
 * [Context] involved) specifically so the boundary math is unit-testable without an Android
 * runtime -- see `ClampSeekPositionTest`.
 *
 * A non-positive [durationMs] (no track loaded, or a track with an unknown/zero duration) has no
 * valid seek range, so it defensively returns `0L` rather than a target that could exceed an
 * unknown or zero-length track.
 */
internal fun clampSeekPosition(currentPositionMs: Long, deltaMs: Long, durationMs: Long): Long {
    if (durationMs <= 0) return 0L
    return (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
}
