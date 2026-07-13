package com.rolla.musicplayer.navigation

import kotlinx.serialization.Serializable

interface Route

@Serializable
data object Library : Route

@Serializable
data object NowPlaying : Route

@Serializable
data object Equalizer : Route

@Serializable
data object Playlists : Route

@Serializable
data class PlaylistDetail(val playlistId: Long) : Route

@Serializable
data class SmartPlaylist(val kind: String) : Route

// Real album_id from the songs schema (unlike ArtistDetail's synthetic FNV-1a hash id --
// see SearchDao -- albums have a genuine MediaStore-backed numeric id).
@Serializable
data class AlbumDetail(val albumId: Long) : Route

@Serializable
data class TagEditor(val songId: Long) : Route

@Serializable
data class BatchTagEditor(val songIds: List<Long>) : Route

// Nullable-with-default per navigation-conventions.md: `query` becomes an optional query param
// in both the type-safe route itself and the `navDeepLink<Search>` derived from it. A missing or
// blank query never crashes -- SearchViewModel treats a null/blank query as its Idle state.
@Serializable
data class Search(val query: String? = null) : Route

// Reachable from both Library and Playlists top-bar overflow menus.
@Serializable
data object Settings : Route

// Settings sub-screens. Single entry point: Settings only.
@Serializable
data object About : Route

@Serializable
data object Licenses : Route

@Serializable
data object Privacy : Route
