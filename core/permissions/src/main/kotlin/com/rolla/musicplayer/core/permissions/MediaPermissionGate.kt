package com.rolla.musicplayer.core.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

private const val MESSAGE_NEEDS_REQUEST =
    "Rolla Music needs access to the audio on your device to build " +
        "your library. Your music never leaves your phone."

private const val MESSAGE_SHOW_RATIONALE =
    "Without audio access there's nothing to play. Grant access to scan " +
        "and play your local music. No data is collected or uploaded."

private const val MESSAGE_PERMANENTLY_DENIED =
    "Audio access is turned off. Open Settings to enable it so " +
        "Rolla Music can load your music."

/**
 * Shows [content] once [state] is granted, and the explain-first prompt otherwise: the rationale before the system
 * dialog, a re-request after one denial, and the app's Settings after a permanent one. [onGranted] runs from a
 * LaunchedEffect keyed on the status, never from composition, so the library sync it triggers runs once per grant and
 * never again on recomposition.
 */
@Composable
fun MediaPermissionGate(
    state: MediaPermissionState,
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val status = state.status
    LaunchedEffect(status) {
        if (status == MediaPermissionStatus.GRANTED) onGranted()
    }
    PermissionContent(
        status = status,
        onRequest = state.request,
        onOpenSettings = state.openSettings,
        modifier = modifier,
        content = content,
    )
}

/** The gate's stateless body: [content] when granted, else the prompt for [status] (internal for its tests). */
@Composable
internal fun PermissionContent(
    status: MediaPermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
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
