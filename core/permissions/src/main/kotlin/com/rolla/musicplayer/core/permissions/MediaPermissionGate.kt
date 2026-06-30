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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val MESSAGE_NEEDS_REQUEST =
    "RollaMusicPlayer needs access to the audio on your device to build " +
        "your library. Your music never leaves your phone."

private const val MESSAGE_SHOW_RATIONALE =
    "Without audio access there's nothing to play. Grant access to scan " +
        "and play your local music. No data is collected or uploaded."

private const val MESSAGE_PERMANENTLY_DENIED =
    "Audio access is turned off. Open Settings to enable it so " +
        "RollaMusicPlayer can load your music."

@Composable
fun MediaPermissionGate(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var status by rememberPermissionStatus(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = resolvePermissionStatus(granted, context)
    }
    ObserveResumePermission(context = context, onGranted = { status = MediaPermissionStatus.GRANTED })
    LaunchedEffect(status) {
        if (status == MediaPermissionStatus.GRANTED) onGranted()
    }
    PermissionContent(
        status = status,
        onRequest = { launcher.launch(MediaPermission.name) },
        onOpenSettings = { context.openAppSettings() },
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun rememberPermissionStatus(context: Context): MutableState<MediaPermissionStatus> =
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

@Composable
private fun PermissionContent(
    status: MediaPermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    when (status) {
        MediaPermissionStatus.GRANTED -> content()
        MediaPermissionStatus.NEEDS_REQUEST -> PermissionPrompt(
            message = MESSAGE_NEEDS_REQUEST,
            buttonText = "Allow access",
            onClick = onRequest,
            modifier = modifier,
        )
        MediaPermissionStatus.SHOW_RATIONALE -> PermissionPrompt(
            message = MESSAGE_SHOW_RATIONALE,
            buttonText = "Grant access",
            onClick = onRequest,
            modifier = modifier,
        )
        MediaPermissionStatus.PERMANENTLY_DENIED -> PermissionPrompt(
            message = MESSAGE_PERMANENTLY_DENIED,
            buttonText = "Open Settings",
            onClick = onOpenSettings,
            modifier = modifier,
        )
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
