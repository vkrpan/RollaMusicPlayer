package com.rolla.musicplayer.navigation

import androidx.navigation.NavController
import com.rolla.musicplayer.core.model.Song

/**
 * Opens the single-track tag editor for [songId] (Phase 6b ruling 10): the one TagEditor push every entry point uses
 * (Home, Now Playing's ⋮, FolderDetail, AlbumDetail, ArtistDetail, PlaylistDetail/SmartPlaylist and Search).
 *
 * A plain push with no popUpTo, so back always returns to whichever screen opened the editor. launchSingleTop guards a
 * double tap of "Edit tags": a second call while the editor is on top keeps one TagEditor entry.
 */
internal fun NavController.openTagEditor(songId: Long) {
    navigate(TagEditor(songId)) { launchSingleTop = true }
}

/**
 * Opens the tag editor for [song]. Song.id is the MediaStore row id, a numeric String by construction, so
 * toLongOrNull() is defensive per navigation-conventions.md (never crash on a malformed id): a non-numeric id is a
 * no-op.
 */
internal fun NavController.openTagEditor(song: Song) {
    song.id.toLongOrNull()?.let { songId -> openTagEditor(songId) }
}
