package com.rolla.musicplayer.navigation

import kotlinx.serialization.Serializable

interface Route

// The start destination: the One UI Home pager (the six library tabs).
@Serializable
data object Home : Route

@Serializable
data object NowPlaying : Route

@Serializable
data object Equalizer : Route

@Serializable
data class PlaylistDetail(val playlistId: Long) : Route

@Serializable
data class SmartPlaylist(val kind: String) : Route

// Real album_id from the songs schema (unlike ArtistDetail's synthetic FNV-1a hash id --
// see SearchDao -- albums have a genuine MediaStore-backed numeric id).
@Serializable
data class AlbumDetail(val albumId: Long) : Route

// Routed by NAME, not id: the songs schema has no artist_id column (artists exist only as a GROUP BY artist
// grouping). Artist.id is a synthetic FNV-1a hash that is display-only; lists key artists by name (redesign Phase 4a
// ruling 12). The name is the real identity and ArtistRepository is keyed by it. Contrast AlbumDetail above (real
// album_id).
@Serializable
data class ArtistDetail(val artistName: String) : Route

// Routed by the folder's path relative to its storage volume ("Music/Chill"); "" is the root ("Internal storage"),
// which Navigation 2.8 accepts as an empty path argument (FolderDetailRouteTest). The path is the folder's identity
// and FolderRepository is keyed by it. FolderDetailViewModel reads it by this property name.
@Serializable
data class FolderDetail(val folderPath: String) : Route

@Serializable
data class TagEditor(val songId: Long) : Route

@Serializable
data class BatchTagEditor(val songIds: List<Long>) : Route

// Nullable-with-default per navigation-conventions.md: `query` becomes an optional query param
// in both the type-safe route itself and the `navDeepLink<Search>` derived from it. A missing or
// blank query never crashes -- SearchViewModel treats a null/blank query as its Idle state.
@Serializable
data class Search(val query: String? = null) : Route

// Reachable from the Home header overflow menu.
@Serializable
data object Settings : Route

// Settings sub-screens. Single entry point: Settings only.
@Serializable
data object About : Route

@Serializable
data object Licenses : Route

@Serializable
data object Privacy : Route
