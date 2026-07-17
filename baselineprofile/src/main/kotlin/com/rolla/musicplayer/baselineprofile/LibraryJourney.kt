package com.rolla.musicplayer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

// Matches Modifier.testTag("song_list") on the library "Songs" LazyColumn, resolved via
// testTagsAsResourceId = true on the app's root Scaffold (MainActivity).
private const val SONG_LIST_RESOURCE_ID = "song_list"
private const val LIST_WAIT_TIMEOUT_MS = 10_000L

/**
 * Waits for the library "Songs" list to appear and returns a handle to it, falling back to any
 * scrollable node if the resource id isn't resolvable so the journey degrades to a startup-only
 * profile instead of crashing (e.g. an empty/still-loading library).
 */
internal fun MacrobenchmarkScope.waitForSongList(): UiObject2? {
    device.wait(Until.hasObject(By.res(SONG_LIST_RESOURCE_ID)), LIST_WAIT_TIMEOUT_MS)
    return device.findObject(By.res(SONG_LIST_RESOURCE_ID))
        ?: device.findObject(By.scrollable(true))
}

/** Flings the library list down three times, then back up once, mirroring a real first scroll. */
internal fun MacrobenchmarkScope.flingSongList(list: UiObject2?) {
    list?.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        list?.fling(Direction.DOWN)
        device.waitForIdle()
    }
    list?.fling(Direction.UP)
    device.waitForIdle()
}
