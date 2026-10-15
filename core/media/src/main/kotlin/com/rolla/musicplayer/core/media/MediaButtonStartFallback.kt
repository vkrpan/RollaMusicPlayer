package com.rolla.musicplayer.core.media

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Keeps the foreground-start contract for a media-button start that nothing else will put in the foreground (rulings
 * P6 and final-review I2).
 *
 * Media3's MediaButtonReceiver starts [service] with `startForegroundService`. If playback resumption then has nothing
 * to resume, the player stays empty, Media3 posts no notification and never calls `startForeground`, and the system
 * kills the app (ForegroundServiceDidNotStartInTimeException on API 31+). In that case this enters the foreground with
 * a silent notification, leaves it at once and stops the service.
 *
 * Media3 acts only on ACTION_DOWN. In Media3 1.11.1 the MediaButtonReceiver itself also drops key UPs and repeats
 * (1.4.1 filtered on the key code only), so a non-DOWN key start now comes only from a direct start of the exported
 * service; this branch guards that. A non-DOWN key start with no playback ongoing enters and leaves the foreground at
 * once. It never stops a service that an earlier start (a DOWN whose resumption may be running) already owns. If the
 * key UP made the service's first start, the service stops once the restore settles with [hasNothingToPlay], through
 * `stopSelf` with that start's id, so any later start (a DOWN, Media3's own foreground start) keeps it alive.
 *
 * A DOWN of a key other than Play, Play/Pause or Headset hook (ruling P8: the media notification's deleteIntent STOP,
 * which the system fires when it cancels a dead process's notification) arrives through `startService`, so it owes no
 * foreground and never calls `startForeground`. It also leaves the record alone. If it made the service's first start,
 * the service stops through `stopSelf` with that start's id once the restore settles with [isIdleWithoutResumption]
 * (nothing playing, no resumption in flight; an applied restore still stops). Without that, the STOP leaves the
 * restarted service started in the background with an unprepared player.
 *
 * It acts only for a start command that carried a media button: a service started by binding (a MediaController) must
 * never call `startForeground` from the background (ForegroundServiceStartNotAllowedException on API 31+). Call every
 * function on the main thread.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class MediaButtonStartFallback(
    private val service: Service,
    private val hasNothingToPlay: () -> Boolean,
    private val isIdleWithoutResumption: () -> Boolean,
) {

    private var startedByMediaButton = false
    private var hadAStart = false
    private var restoreSettled = false

    // The service's first start when nothing will keep it alive (a key UP or a non-play key DOWN); null once handled.
    private var pendingStop: PendingStop? = null

    /**
     * Call from `onStartCommand`, before `super`. A start without a key event, or with a Play-key DOWN, replaces the
     * previous record; any other key start leaves it alone, so a DOWN's pending resumption keeps its fallback.
     */
    fun onStartCommand(intent: Intent?, startId: Int, isPlaybackOngoing: Boolean) {
        val isFirstStart = !hadAStart
        hadAStart = true
        val keyEvent = intent?.mediaButtonKeyEvent()
        when {
            keyEvent == null -> startedByMediaButton = intent?.action == Intent.ACTION_MEDIA_BUTTON
            keyEvent.action != KeyEvent.ACTION_DOWN -> onKeyUpStart(startId, isFirstStart, isPlaybackOngoing)
            keyEvent.keyCode in PLAY_KEY_CODES -> startedByMediaButton = true
            // Ruling P8: a startService start (no foreground owed) that Media3 will not keep alive.
            isFirstStart -> stopOnceSettled(startId, isIdleWithoutResumption)
        }
    }

    /** The restore left LOADING. Call it once, from the restore coordinator's settle callback. */
    fun onRestoreSettled() {
        restoreSettled = true
        stopIfIdleOnceSettled()
    }

    /** Playback resumption found nothing to resume. Does nothing unless a media button started the service. */
    fun onNothingToResume() {
        if (!startedByMediaButton) return
        startedByMediaButton = false
        enterAndLeaveForeground()
        service.stopSelf()
    }

    // Media3 drops every key action but DOWN, so nothing else will call startForeground for this start.
    private fun onKeyUpStart(startId: Int, isFirstStart: Boolean, isPlaybackOngoing: Boolean) {
        if (isPlaybackOngoing) return
        enterAndLeaveForeground()
        if (isFirstStart) stopOnceSettled(startId, hasNothingToPlay)
    }

    private fun stopOnceSettled(startId: Int, shouldStop: () -> Boolean) {
        pendingStop = PendingStop(startId, shouldStop)
        stopIfIdleOnceSettled()
    }

    // Waits for both the first start and the settled restore, in either order. stopSelf(startId) leaves the service
    // running if a later start reached it.
    private fun stopIfIdleOnceSettled() {
        if (!restoreSettled) return
        val stop = pendingStop ?: return
        pendingStop = null
        if (stop.shouldStop()) service.stopSelf(stop.startId)
    }

    // :core:media compiles against androidx.core 1.9, which has no ServiceCompat.startForeground, hence the check.
    private fun enterAndLeaveForeground() {
        val notification = silentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                service.startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) for a stale record: log it, never crash playback.
            Log.w(TAG, "Media-button fallback could not enter the foreground", e)
        }
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    // Media3's own low-importance playback channel, so the fallback adds no channel and no copy of its own.
    private fun silentNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val manager = NotificationManagerCompat.from(service)
        if (manager.getNotificationChannelCompat(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(service.getString(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID))
                .setShowBadge(false)
                .build(),
        )
    }

    // The key event of a media-button intent, or null for any other intent or one without a key event.
    private fun Intent.mediaButtonKeyEvent(): KeyEvent? {
        if (action != Intent.ACTION_MEDIA_BUTTON) return null
        @Suppress("DEPRECATION") // The typed getParcelableExtra overload is API 33+; minSdk is 24.
        return getParcelableExtra(Intent.EXTRA_KEY_EVENT)
    }

    private class PendingStop(val startId: Int, val shouldStop: () -> Boolean)

    companion object {
        private const val TAG = "MediaButtonStartFallback"
        private const val CHANNEL_ID = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID

        // The keys Media3's MediaButtonReceiver starts the service for in the foreground (API 26+).
        private val PLAY_KEY_CODES = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )

        // Distinct from Media3's DEFAULT_NOTIFICATION_ID (1001); the notification is removed at once anyway.
        const val NOTIFICATION_ID = 1002
    }
}
