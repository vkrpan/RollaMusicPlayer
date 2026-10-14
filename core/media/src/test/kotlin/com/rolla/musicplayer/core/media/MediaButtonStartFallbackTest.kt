package com.rolla.musicplayer.core.media

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Ruling P6 and final-review I2: the foreground-start contract for a media-button start that nothing else puts in the
 * foreground. Ruling P8: a non-play key's first start stops the service once the restore settles idle.
 * ShadowService clears `lastForegroundNotification` on `stopForeground(REMOVE)` but keeps
 * `lastForegroundNotificationId`, so the id is the probe for "startForeground was called".
 */
@RunWith(AndroidJUnit4::class)
class MediaButtonStartFallbackTest {

    private val service = Robolectric.buildService(BareService::class.java).create().get()
    private var nothingToPlay = true
    private var idleWithoutResumption = true
    private val fallback = MediaButtonStartFallback(service, { nothingToPlay }, { idleWithoutResumption })
    private val shadow get() = shadowOf(service)

    private fun key(action: Int, keyCode: Int = KeyEvent.KEYCODE_HEADSETHOOK) = Intent(Intent.ACTION_MEDIA_BUTTON)
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode))

    // The media notification's deleteIntent: a STOP DOWN, sent through startService (ruling P8).
    private fun stopKey() = key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)

    // Mutations: drop the startForeground call → RED (id 0); drop stopForeground → RED; drop stopSelf → RED.
    @Test
    fun aMediaButtonStartWithNothingToResume_entersAndLeavesTheForegroundAndStops() {
        fallback.onStartCommand(Intent(Intent.ACTION_MEDIA_BUTTON), startId = 1, isPlaybackOngoing = false)

        fallback.onNothingToResume()

        assertEquals(MediaButtonStartFallback.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.notificationShouldRemoved)
        assertTrue(shadow.isStoppedBySelf)
    }

    // A service only bound by a MediaController (no start command) must never call startForeground.
    // Mutation: drop the flag check → RED.
    @Test
    fun aBoundServiceWithNothingToResume_neverEntersTheForeground() {
        fallback.onNothingToResume()

        assertEquals(0, shadow.lastForegroundNotificationId)
        assertFalse(shadow.isForegroundStopped)
        assertFalse(shadow.isStoppedBySelf)
    }

    // Each start command replaces the record. Mutation: only ever set the flag (`if (media button) flag = true`) → RED.
    @Test
    fun aLaterStartWithoutAMediaButton_replacesTheRecord() {
        fallback.onStartCommand(Intent(Intent.ACTION_MEDIA_BUTTON), startId = 1, isPlaybackOngoing = false)
        fallback.onStartCommand(Intent(), startId = 2, isPlaybackOngoing = false)

        fallback.onNothingToResume()

        assertEquals(0, shadow.lastForegroundNotificationId)
    }

    // The record is cleared once handled. After the first fallback the test marks the shadow with its own foreground
    // id; a second fallback would overwrite it. Mutation: drop `startedByMediaButton = false` → RED.
    @Test
    fun theRecordIsClearedOnceHandled() {
        fallback.onStartCommand(Intent(Intent.ACTION_MEDIA_BUTTON), startId = 1, isPlaybackOngoing = false)
        fallback.onNothingToResume()
        val marker = Notification.Builder(ApplicationProvider.getApplicationContext(), "marker").build()
        service.startForeground(MARKER_ID, marker)

        fallback.onNothingToResume()

        assertEquals(MARKER_ID, shadow.lastForegroundNotificationId)
    }

    // Final-review I2: Media3 ignores a key UP, so the UP's own start must satisfy the foreground contract at once.
    // Mutations: drop the UP branch (treat every key start as a DOWN) → id 0 → RED; drop stopForeground → RED.
    @Test
    fun aKeyUpWithNothingOngoing_entersAndLeavesTheForeground() {
        fallback.onStartCommand(key(KeyEvent.ACTION_UP), startId = 1, isPlaybackOngoing = false)

        assertEquals(MediaButtonStartFallback.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.notificationShouldRemoved)
    }

    // A DOWN made the first start and its resumption is still running (here a reload after the restore settled with
    // nothing, so the player is empty). The UP must not stop the service, and must leave the DOWN's record so the
    // DOWN's own fallback still runs if its resumption finds nothing. Mutations: an unconditional stop in the UP branch
    // → stopped → RED; drop the first-start check → the settled restore stops it with the UP's id → RED; the UP
    // clears the record (`startedByMediaButton = false`) → onNothingToResume does nothing → RED.
    @Test
    fun aKeyUpWhileADownsResumptionRuns_neitherStopsTheServiceNorDropsTheDownsRecord() {
        fallback.onStartCommand(key(KeyEvent.ACTION_DOWN), startId = 1, isPlaybackOngoing = false)
        fallback.onRestoreSettled()

        fallback.onStartCommand(key(KeyEvent.ACTION_UP), startId = 2, isPlaybackOngoing = false)

        assertEquals(MediaButtonStartFallback.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertFalse(shadow.isStoppedBySelf)
        fallback.onNothingToResume()
        assertTrue(shadow.isStoppedBySelf)
    }

    // A UP that made the service's first start: once the restore settles with nothing to play, the service stops with
    // the UP's start id (a later start keeps it alive). Mutations: drop the stop → RED; stop before the restore settles
    // (no `restoreSettled` check) → stopped early → RED; `stopSelf()` without the id → stopSelfId 0 → RED.
    @Test
    fun aKeyUpFirstStart_stopsOnceTheRestoreSettlesWithNothingToPlay() {
        fallback.onStartCommand(key(KeyEvent.ACTION_UP), startId = 5, isPlaybackOngoing = false)
        assertFalse(shadow.isStoppedBySelf)

        fallback.onRestoreSettled()

        assertTrue(shadow.isStoppedBySelf)
        assertEquals(5, shadow.stopSelfId)
    }

    // The restore can settle before the UP's start command runs. Mutation: stop only from onRestoreSettled (drop the
    // check in onStartCommand) → never stopped → RED.
    @Test
    fun aKeyUpFirstStartAfterTheRestoreSettled_stopsAtOnce() {
        fallback.onRestoreSettled()

        fallback.onStartCommand(key(KeyEvent.ACTION_UP), startId = 3, isPlaybackOngoing = false)

        assertTrue(shadow.isStoppedBySelf)
        assertEquals(3, shadow.stopSelfId)
    }

    // A restore that applied (or a resumption that took it) keeps the service alive. Mutation: ignore
    // hasNothingToPlay → stopped → RED.
    @Test
    fun aKeyUpFirstStartWithSomethingToPlay_staysStarted() {
        nothingToPlay = false
        fallback.onStartCommand(key(KeyEvent.ACTION_UP), startId = 1, isPlaybackOngoing = false)

        fallback.onRestoreSettled()

        assertFalse(shadow.isStoppedBySelf)
    }

    // Playback ongoing means Media3 already holds the foreground: the UP changes nothing. Mutation: drop the
    // isPlaybackOngoing check → the fallback notification replaces Media3's foreground → RED.
    @Test
    fun aKeyUpWhilePlaybackIsOngoing_doesNothing() {
        fallback.onStartCommand(key(KeyEvent.ACTION_UP), startId = 1, isPlaybackOngoing = true)
        fallback.onRestoreSettled()

        assertEquals(0, shadow.lastForegroundNotificationId)
        assertFalse(shadow.isStoppedBySelf)
    }

    // Ruling P8: the deleteIntent STOP restarted the service, the restore APPLIED (so hasNothingToPlay is false) and
    // nothing plays. The service stops with the STOP's start id once the restore settles, and never enters the
    // foreground: not at the start, and not later through a record the STOP would have set. Mutations: treat STOP as a
    // play key → never stopped → RED; require hasNothingToPlay instead of isIdleWithoutResumption → never stopped →
    // RED; enter and leave the foreground as for a key UP → id 1002 → RED; set the record as the old replace-on-DOWN
    // did (`startedByMediaButton = true`) → onNothingToResume enters the foreground → RED.
    @Test
    fun aStopFirstStartWithAnAppliedRestore_stopsOnceTheRestoreSettlesWithoutEnteringTheForeground() {
        nothingToPlay = false
        fallback.onStartCommand(stopKey(), startId = 4, isPlaybackOngoing = false)
        assertFalse(shadow.isStoppedBySelf)

        fallback.onRestoreSettled()

        assertTrue(shadow.isStoppedBySelf)
        assertEquals(4, shadow.stopSelfId)
        assertEquals(0, shadow.lastForegroundNotificationId)
        fallback.onNothingToResume()
        assertEquals(0, shadow.lastForegroundNotificationId)
    }

    // The restore can settle before the STOP's start command runs. Mutation: stop only from onRestoreSettled (drop the
    // check in stopOnceSettled) → never stopped → RED.
    @Test
    fun aStopFirstStartAfterTheRestoreSettled_stopsAtOnce() {
        nothingToPlay = false
        fallback.onRestoreSettled()

        fallback.onStartCommand(stopKey(), startId = 6, isPlaybackOngoing = false)

        assertTrue(shadow.isStoppedBySelf)
        assertEquals(6, shadow.stopSelfId)
        assertEquals(0, shadow.lastForegroundNotificationId)
    }

    // A resumption took the first load (HANDED_TO_RESUMPTION) or playback is ongoing: isIdleWithoutResumption is false
    // and the service stays started. Mutation: drop the guard (the STOP branch
    // passes `{ true }`) → stopped → RED.
    @Test
    fun aStopFirstStartDuringAResumptionOrPlayback_neverStops() {
        nothingToPlay = false
        idleWithoutResumption = false
        fallback.onStartCommand(stopKey(), startId = 1, isPlaybackOngoing = false)

        fallback.onRestoreSettled()

        assertFalse(shadow.isStoppedBySelf)
        assertEquals(0, shadow.lastForegroundNotificationId)
    }

    // A Play DOWN made the first start and its resumption may still run; a STOP start after it is not the first start.
    // It must not stop the service and must leave the DOWN's record, so the DOWN's fallback still runs if its
    // resumption finds nothing. Mutations: drop the first-start check → the settled restore stops it with the STOP's id
    // → RED; the STOP clears the record (`startedByMediaButton = false`) → onNothingToResume does nothing → RED.
    @Test
    fun aStopStartAfterAPlayDown_neitherStopsTheServiceNorDropsTheDownsRecord() {
        fallback.onStartCommand(key(KeyEvent.ACTION_DOWN), startId = 1, isPlaybackOngoing = false)
        fallback.onRestoreSettled()

        fallback.onStartCommand(stopKey(), startId = 2, isPlaybackOngoing = false)

        assertFalse(shadow.isStoppedBySelf)
        assertEquals(0, shadow.lastForegroundNotificationId)
        fallback.onNothingToResume()
        assertEquals(MediaButtonStartFallback.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isStoppedBySelf)
    }

    class BareService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }

    private companion object {
        const val MARKER_ID = 77
    }
}
