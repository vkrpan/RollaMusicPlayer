package com.rolla.musicplayer.baselineprofile

import android.os.Build
import androidx.test.uiautomator.UiDevice

private const val READ_MEDIA_AUDIO_PERMISSION = "android.permission.READ_MEDIA_AUDIO"
private const val READ_EXTERNAL_STORAGE_PERMISSION = "android.permission.READ_EXTERNAL_STORAGE"

/**
 * Grants the media-library runtime permission the app gates behind on first launch, then seeds
 * the on-device MediaStore if it's empty (managed devices boot with no music at all) so the
 * library-scroll / now-playing legs of the critical journey always have songs to work with.
 *
 * Runs ON the target device (this whole module is instrumentation code), so [Build.VERSION]
 * reflects the device under test, not the host running Gradle.
 */
internal fun UiDevice.grantMediaPermissionAndSeedLibrary(packageName: String) {
    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            READ_MEDIA_AUDIO_PERMISSION
        } else {
            READ_EXTERNAL_STORAGE_PERMISSION
        }
    executeShellCommand("pm grant $packageName $permission")
    MediaStoreSeeder.seedIfEmpty()
}
