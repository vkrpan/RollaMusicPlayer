package com.rolla.musicplayer.core.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The media read permission's status, hoisted out of [MediaPermissionGate] (Phase 6b ruling 8) so the screen around
 * the gate can react to it: Home hides its + and its selection bar while access is denied. Create it with
 * [rememberMediaPermissionState]. The request and open-Settings actions stay internal: only the gate's prompt calls
 * them.
 */
@Stable
class MediaPermissionState internal constructor(
    private val statusState: State<MediaPermissionStatus>,
    internal val request: () -> Unit,
    internal val openSettings: () -> Unit,
) {
    /** Where the user stands on the one audio-read permission (READ_MEDIA_AUDIO on 33+, else READ_EXTERNAL_STORAGE). */
    val status: MediaPermissionStatus get() = statusState.value

    /** True once the audio-read permission is granted. */
    val isGranted: Boolean get() = status == MediaPermissionStatus.GRANTED
}

/**
 * Owns the permission request launcher, the ON_RESUME re-check (returning from Settings with access granted lifts the
 * gate without a restart) and the status, which starts from the current grant.
 */
@Composable
fun rememberMediaPermissionState(): MediaPermissionState {
    val context = LocalContext.current
    val status = rememberInitialStatus(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status.value = resolvePermissionStatus(granted, context)
    }
    ObserveResumePermission(context = context, onGranted = { status.value = MediaPermissionStatus.GRANTED })
    return remember(status, launcher, context) {
        MediaPermissionState(
            statusState = status,
            request = { launcher.launch(MediaPermission.name) },
            openSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun rememberInitialStatus(context: Context): MutableState<MediaPermissionStatus> =
    remember {
        mutableStateOf(
            if (MediaPermission.isGranted(context)) {
                MediaPermissionStatus.GRANTED
            } else {
                MediaPermissionStatus.NEEDS_REQUEST
            },
        )
    }

private fun resolvePermissionStatus(granted: Boolean, context: Context): MediaPermissionStatus =
    when {
        granted -> MediaPermissionStatus.GRANTED
        shouldShowRationale(context, MediaPermission.name) -> MediaPermissionStatus.SHOW_RATIONALE
        else -> MediaPermissionStatus.PERMANENTLY_DENIED
    }

@Composable
private fun ObserveResumePermission(context: Context, onGranted: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && MediaPermission.isGranted(context)) {
                onGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun shouldShowRationale(context: Context, permission: String): Boolean =
    (context as? ComponentActivity)
        ?.shouldShowRequestPermissionRationale(permission) == true

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
