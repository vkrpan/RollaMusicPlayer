package com.rolla.musicplayer.baselineprofile

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

// Matches Modifier.testTag("song_list") on Home's Tracks LazyColumn, resolved via
// testTagsAsResourceId = true on the app's root Box (MainActivity's MiniPlayerHost modifier).
private const val SONG_LIST_RESOURCE_ID = "song_list"
private const val LIST_WAIT_TIMEOUT_MS = 10_000L

// Home's pager order is Favourites · Playlists · Tracks · Albums · Artists · Folders: three swipes reach Folders.
private const val TABS_RIGHT_OF_TRACKS = 3
private const val SWIPE_STEPS = 20

// FastScrollRail's single TalkBack node and one of its per-section actions (Phase 5 semantics).
private const val RAIL_DESCRIPTION = "Fast scroll, letter index"
private const val RAIL_JUMP_ACTION = "Jump to M"
private const val TAP_ATTEMPTS = 3

/**
 * Waits for Home's "Tracks" list to appear and returns a handle to it, falling back to any
 * scrollable node if the resource id isn't resolvable so the journey degrades to a startup-only
 * profile instead of crashing (e.g. an empty/still-loading library).
 */
internal fun MacrobenchmarkScope.waitForSongList(): UiObject2? {
    device.wait(Until.hasObject(By.res(SONG_LIST_RESOURCE_ID)), LIST_WAIT_TIMEOUT_MS)
    return device.findObject(By.res(SONG_LIST_RESOURCE_ID))
        ?: device.findObject(By.scrollable(true))
}

/**
 * Swipes Home's pager from Tracks to Folders and back (Albums, Artists and Folders compose their first page), at the
 * vertical middle of the screen, inside the content panel.
 */
internal fun MacrobenchmarkScope.swipeHomeTabs() {
    val y = device.displayHeight / 2
    val left = device.displayWidth / 5
    val right = device.displayWidth - left
    repeat(TABS_RIGHT_OF_TRACKS) {
        device.swipe(right, y, left, y, SWIPE_STEPS)
        device.waitForIdle()
    }
    repeat(TABS_RIGHT_OF_TRACKS) {
        device.swipe(left, y, right, y, SWIPE_STEPS)
        device.waitForIdle()
    }
}

/**
 * Jumps the Tracks list with the A–Z rail through its "Jump to M" accessibility action, the rail's first-jump path.
 * Falls back to tapping the rail's centre when the action is missing. It works on the raw accessibility node, read
 * fresh from the window: Compose replaces the rail's UiObject2 as the list moves, so a held handle goes stale.
 */
internal fun MacrobenchmarkScope.jumpWithRail() {
    device.wait(Until.hasObject(By.desc(RAIL_DESCRIPTION)), LIST_WAIT_TIMEOUT_MS)
    val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return
    val rail = root.findByDescription(RAIL_DESCRIPTION) ?: return
    val jump = rail.actionList.firstOrNull { it.label?.toString() == RAIL_JUMP_ACTION }
    if (jump != null) {
        rail.performAction(jump.id)
    } else {
        val bounds = Rect().also(rail::getBoundsInScreen)
        device.click(bounds.centerX(), bounds.centerY())
    }
    device.waitForIdle()
}

/** Depth-first search for the node whose content description is exactly [description]. */
private fun AccessibilityNodeInfo.findByDescription(description: String): AccessibilityNodeInfo? {
    if (contentDescription?.toString() == description) return this
    for (index in 0 until childCount) {
        getChild(index)?.findByDescription(description)?.let { return it }
    }
    return null
}

/**
 * Taps the first visible track. The tab swipes and the rail jump recompose the Tracks page, so a row handle can go
 * stale between finding it and tapping it: each attempt finds the list again and taps the row's centre by coordinates.
 */
internal fun MacrobenchmarkScope.tapFirstTrack() {
    repeat(TAP_ATTEMPTS) {
        try {
            val row = waitForSongList()?.children?.firstOrNull() ?: return
            val bounds = row.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            return
        } catch (stale: StaleObjectException) {
            device.waitForIdle()
        }
    }
}

/** Flings the library list down three times, then back up once, mirroring a real first scroll. */
internal fun MacrobenchmarkScope.flingSongList(list: UiObject2?) {
    // The Tracks list runs to the bottom of the screen, under the floating mini-player pill (visible from the second
    // iteration on, because playback state persists). The bottom margin keeps the fling's start point above the
    // navigation bar, the pill and touch slop; the other three margins keep their original width / 5.
    val sideMargin = device.displayWidth / 5
    list?.setGestureMargins(sideMargin, sideMargin, sideMargin, device.displayHeight / 4)
    repeat(3) {
        list?.fling(Direction.DOWN)
        device.waitForIdle()
    }
    list?.fling(Direction.UP)
    device.waitForIdle()
}
