package com.rolla.musicplayer.core.permissions

internal enum class MediaPermissionStatus {
    GRANTED,
    NEEDS_REQUEST,
    SHOW_RATIONALE,
    PERMANENTLY_DENIED,
}
