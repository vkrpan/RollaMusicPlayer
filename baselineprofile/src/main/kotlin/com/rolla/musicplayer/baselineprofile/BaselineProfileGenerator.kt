package com.rolla.musicplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.rolla.musicplayer"
private const val OPEN_PLAYER_TIMEOUT_MS = 5_000L

/**
 * Generates the Baseline Profile for RollaMusicPlayer by driving its hottest journey:
 * cold start -> library "Songs" list settling + scroll -> open a song -> Now Playing.
 *
 * Run later (no device attached in this environment -- see baselineprofile/build.gradle.kts for
 * the managed-device vs connected-device wiring):
 *   ./gradlew :app:generateBaselineProfile                                 # managed device
 *   ./gradlew :app:generateBaselineProfile -Pbaselineprofile.useConnected  # physical/attached device
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            device.grantMediaPermissionAndSeedLibrary(PACKAGE_NAME)

            pressHome()
            startActivityAndWait()

            // Journey step 1: the library "Songs" list settles and is scrolled.
            val list = waitForSongList()
            flingSongList(list)

            // Journey step 2: open a song, expand the mini-player into Now Playing.
            list?.children?.firstOrNull()?.click()
            device.wait(Until.hasObject(By.descContains("Open player")), OPEN_PLAYER_TIMEOUT_MS)
            device.findObject(By.descContains("Open player"))?.click()
            device.wait(Until.hasObject(By.desc("Playback position")), OPEN_PLAYER_TIMEOUT_MS)
            device.waitForIdle()
            device.pressBack()
        }
}
